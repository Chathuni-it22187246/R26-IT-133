"""Heater regression + infection-dataset matching used by ai_server.py."""

from __future__ import annotations

import logging
import re
from pathlib import Path
from typing import Any

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestRegressor

logger = logging.getLogger("greenhands.ai")

FEATURE_COLUMNS = ["current_temperature", "target_temperature", "humidity"]
TARGET_COLUMN = "heater_speed"
INFECTION_FIELDS = (
    "plant_type",
    "infection_short_name",
    "infection_full_name",
    "severity_level",
    "visible_symptoms",
    "treatment_description",
    "biological_control",
    "chemical_control",
    "prevention_steps",
)

_TOKEN_RE = re.compile(r"[a-z0-9]+")


def resolve_data_file(base_dir: Path, filename: str) -> Path:
    candidates = [
        base_dir / filename,
        base_dir / "data" / filename,
        base_dir.parent / filename,
        Path.home() / "Desktop" / filename,
    ]
    for path in candidates:
        if path.exists():
            return path
    raise FileNotFoundError(f"Could not find {filename}. Looked in: {candidates}")


def train_heater_model(csv_path: Path, model_path: Path) -> Any:
    df = pd.read_csv(csv_path)
    missing = [col for col in FEATURE_COLUMNS + [TARGET_COLUMN] if col not in df.columns]
    if missing:
        raise ValueError(f"heater dataset missing columns: {missing}")
    model = RandomForestRegressor(
        n_estimators=80,
        max_depth=12,
        min_samples_leaf=2,
        random_state=42,
        n_jobs=-1,
    )
    model.fit(df[FEATURE_COLUMNS], df[TARGET_COLUMN])
    model_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, model_path)
    logger.info("Saved heater RandomForest model → %s (%s rows)", model_path, len(df))
    return model


def load_or_train_heater_model(base_dir: Path) -> Any:
    model_path = base_dir / "heater_model.pkl"
    csv_path = resolve_data_file(base_dir, "heater_training_data.csv")
    if model_path.exists():
        logger.info("Loading heater model from %s", model_path)
        return joblib.load(model_path)
    return train_heater_model(csv_path, model_path)


def predict_heater_speed(
    model: Any,
    current_temperature: float,
    target_temperature: float,
    humidity: float,
) -> float:
    sample = pd.DataFrame(
        [[current_temperature, target_temperature, humidity]],
        columns=FEATURE_COLUMNS,
    )
    raw = float(model.predict(sample)[0])
    return round(max(0.0, min(100.0, raw)), 2)


def load_infection_dataset(base_dir: Path) -> pd.DataFrame:
    csv_path = resolve_data_file(base_dir, "plant_infections_dataset.csv")
    df = pd.read_csv(csv_path)
    missing = [col for col in INFECTION_FIELDS if col not in df.columns]
    if missing:
        raise ValueError(f"infection dataset missing columns: {missing}")
    logger.info("Loaded infection dataset %s (%s rows)", csv_path, len(df))
    return df.fillna("")


def _tokens(*parts: str | None) -> list[str]:
    blob = " ".join(p for p in parts if p).lower()
    return [tok for tok in _TOKEN_RE.findall(blob) if len(tok) > 2]


def match_infection_row(
    dataset: pd.DataFrame,
    *,
    query: str | None = None,
    symptoms: str | None = None,
    plant_type: str | None = None,
    infection_name: str | None = None,
) -> dict[str, str]:
    if dataset.empty:
        raise ValueError("Infection dataset is empty.")

    tokens = _tokens(query, symptoms, plant_type, infection_name)
    plant = (plant_type or "").strip().lower()
    name = (infection_name or query or "").strip().lower()

    best_idx = 0
    best_score = -1.0
    for idx, row in dataset.iterrows():
        short = str(row["infection_short_name"])
        full = str(row["infection_full_name"])
        row_plant = str(row["plant_type"])
        symptoms_text = str(row["visible_symptoms"])
        haystack = f"{row_plant} {short} {full} {symptoms_text}".lower()
        score = 0.0
        if name and name == short.lower():
            score += 20.0
        elif name and name in short.lower():
            score += 12.0
        elif name and name in full.lower():
            score += 8.0
        if plant and plant == row_plant.lower():
            score += 6.0
        for token in tokens:
            if token in short.lower():
                score += 3.0
            elif token in full.lower():
                score += 2.0
            elif token in haystack:
                score += 1.0
        if score > best_score:
            best_score = score
            best_idx = idx

    if best_score <= 0 and tokens:
        # Fall back to first row of the closest plant type, else first dataset row.
        if plant:
            plant_hits = dataset[dataset["plant_type"].str.lower() == plant]
            if not plant_hits.empty:
                best_idx = plant_hits.index[0]
        else:
            best_idx = dataset.index[0]

    row = dataset.loc[best_idx]
    return {field: str(row[field]) for field in INFECTION_FIELDS}
