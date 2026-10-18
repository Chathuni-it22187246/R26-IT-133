"""
GreenHands AI Decision Service
------------------------------
FastAPI backend that monitors infection_log.txt, runs a RAG / extension-retrieval
pipeline, and exposes AI decision APIs for the Android app.

Realtime behaviour:
  - Each non-empty line in infection_log.txt is one Active AI Decision.
  - Plain lines are infections; HEATER / CLIMATE / ACTION lines are climate cards.
  - POST /api/v1/predict-heater uses heater_model.pkl (Random Forest) for 0–100% speed.
  - Non-optimal temperature or humidity appends a structured heater action log entry.
  - A background watcher rebuilds the full active list whenever the file changes.
  - Android polls GET /api/v1/ai-decision/active to render infection + heater cards.
"""

from __future__ import annotations

import asyncio
import logging
import time
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from climate_log import (
    TARGET_TEMPERATURE_C,
    append_fan_action,
    append_heater_action,
    apply_single_active_climate_rule,
    ensure_sample_log,
    evaluate_climate,
    evaluate_greenhouse_health,
    heater_immediate_action,
    infection_line_count,
    is_structured_log_line,
    needed_climate_actuator,
    parse_structured_log_line,
    synthesize_structured_decision,
)
from rag_pipeline import synthesize_treatment_guide
from ml_pipeline import (
    load_infection_dataset,
    load_or_train_heater_model,
    match_infection_row,
    predict_heater_speed,
)

load_dotenv()

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("greenhands.ai")

BASE_DIR = Path(__file__).resolve().parent
INFECTION_LOG = BASE_DIR / "infection_log.txt"

# Cache: one decision per log line (index-aligned with infection_log.txt)
_active_decisions: list[dict[str, Any]] = []
_latest_decision: dict[str, Any] | None = None
_latest_infection: str = ""
_latest_updated_at: str = ""
_last_fingerprint: tuple[float, int, tuple[str, ...]] | None = None
_monitor_task: asyncio.Task | None = None
_heater_model: Any | None = None
_infection_dataset: Any | None = None
_last_climate_eval: Any | None = None


class AiDecisionRequest(BaseModel):
    infection_text: str | None = Field(
        default=None, description="Detected infection / disease name"
    )
    disease: str | None = Field(default=None, description="Alias for infection_text")
    crop: str | None = None
    stage: str | None = None


class AiDecisionResponse(BaseModel):
    title: str
    description: str
    urgency: str
    infection_name: str
    severity_level: str
    immediate_action: str
    biological_treatment: list[str]
    chemical_control: list[str]
    prevention: list[str]
    environmental_adjustments: list[str]
    sources: list[str] = []
    crop: str = ""
    stage: str = ""
    updated_at: str = ""
    log_line: str = ""
    log_path: str = ""
    decision_id: str = ""
    line_index: int = 0
    kind: str = "infection"
    category: str = "infections"
    heater_speed: float | None = None
    current_temperature: float | None = None
    target_temperature: float | None = None
    humidity: float | None = None
    climate_status: str = ""
    lifecycle: str = ""


class ActiveDecisionsResponse(BaseModel):
    count: int
    updated_at: str
    log_path: str
    decisions: list[AiDecisionResponse]


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_infection_line(raw: str) -> str:
    line = (raw or "").strip()
    if not line:
        return ""
    if "|" in line:
        line = line.split("|")[-1].strip()
    if ";" in line:
        line = line.split(";")[-1].strip()
    if "=" in line:
        maybe = line.split("=")[-1].strip()
        if maybe:
            line = maybe
    return line


def read_infection_log_lines() -> list[str]:
    ensure_sample_log(INFECTION_LOG)
    text = INFECTION_LOG.read_text(encoding="utf-8")
    return [ln.strip() for ln in text.splitlines() if ln.strip()]


def read_latest_infection() -> str:
    lines = read_infection_log_lines()
    for raw in reversed(lines):
        if is_structured_log_line(raw):
            continue
        parsed = parse_infection_line(raw)
        if parsed:
            return parsed
    return "Unknown Infection"


def file_fingerprint() -> tuple[float, int, tuple[str, ...]]:
    if not INFECTION_LOG.exists():
        return (0.0, 0, tuple())
    stat = INFECTION_LOG.stat()
    lines = tuple(read_infection_log_lines())
    return (stat.st_mtime, stat.st_size, lines)


def _enrich_guide(
    guide: dict[str, Any],
    *,
    log_line: str,
    line_index: int,
    crop: str | None = None,
    stage: str | None = None,
) -> dict[str, Any]:
    guide = dict(guide)
    if crop:
        guide["crop"] = crop
    if stage:
        guide["stage"] = stage
    guide["updated_at"] = _utc_now()
    guide["log_line"] = log_line
    guide["log_path"] = str(INFECTION_LOG)
    guide["line_index"] = line_index
    guide["decision_id"] = f"log-{line_index}-{parse_infection_line(log_line).lower().replace(' ', '-')}"
    guide.setdefault("kind", "infection")
    guide.setdefault("category", "infections")
    guide.setdefault("heater_speed", None)
    guide.setdefault("current_temperature", None)
    guide.setdefault("target_temperature", None)
    guide.setdefault("humidity", None)
    guide.setdefault("climate_status", "")
    guide.setdefault("lifecycle", "")
    return guide


def synthesize_one(
    infection: str,
    *,
    log_line: str,
    line_index: int,
    crop: str | None = None,
    stage: str | None = None,
) -> dict[str, Any]:
    guide = synthesize_treatment_guide(infection, crop=crop, stage=stage)
    return _enrich_guide(
        guide,
        log_line=log_line,
        line_index=line_index,
        crop=crop,
        stage=stage,
    )


def rebuild_active_decisions(
    crop: str | None = None,
    stage: str | None = None,
) -> list[dict[str, Any]]:
    """Build one AI decision card payload per infection_log.txt line."""
    global _active_decisions, _latest_decision, _latest_infection, _latest_updated_at

    raw_lines = read_infection_log_lines()
    decisions: list[dict[str, Any]] = []

    # Reuse previous decisions for unchanged log lines to keep polling snappy.
    prev_by_key = {
        (d.get("line_index"), d.get("log_line", "")): d
        for d in _active_decisions
    }

    for idx, raw in enumerate(raw_lines):
        key = (idx, raw)
        cached = prev_by_key.get(key)
        if cached is not None:
            decisions.append(cached)
            continue
        structured = parse_structured_log_line(raw)
        if structured is not None:
            logger.info("Climate action card [%s] %s", idx, structured.get("_prefix"))
            decisions.append(
                synthesize_structured_decision(
                    structured,
                    log_line=raw,
                    line_index=idx,
                    log_path=str(INFECTION_LOG),
                )
            )
            continue
        infection = parse_infection_line(raw) or "Unknown Infection"
        logger.info("Synthesizing decision [%s] %s", idx, infection)
        decisions.append(
            synthesize_one(
                infection,
                log_line=raw,
                line_index=idx,
                crop=crop,
                stage=stage,
            )
        )

    _active_decisions = apply_single_active_climate_rule(decisions, _last_climate_eval)
    if _active_decisions:
        _latest_decision = _active_decisions[-1]
        _latest_infection = _active_decisions[-1].get("infection_name", "")
        _latest_updated_at = _active_decisions[-1].get("updated_at", _utc_now())
    else:
        _latest_decision = None
        _latest_infection = ""
        _latest_updated_at = _utc_now()

    logger.info("Active AI decisions ready: %s card(s)", len(_active_decisions))
    return _active_decisions


def build_decision(
    infection: str,
    crop: str | None = None,
    stage: str | None = None,
    log_line: str | None = None,
) -> dict[str, Any]:
    """Single-decision helper (also refreshes the full active list from the log)."""
    rebuild_active_decisions(crop=crop, stage=stage)
    if _latest_decision and _latest_infection.lower() == infection.lower():
        return _latest_decision
    # Explicit infection not yet the last log line — synthesize standalone then refresh list.
    guide = synthesize_one(
        infection,
        log_line=log_line or infection,
        line_index=max(len(_active_decisions) - 1, 0),
        crop=crop,
        stage=stage,
    )
    return guide


async def infection_log_monitor(poll_seconds: float = 1.0) -> None:
    global _last_fingerprint
    logger.info("Infection log monitor started → %s (poll=%.1fs)", INFECTION_LOG, poll_seconds)
    while True:
        try:
            fp = await asyncio.to_thread(file_fingerprint)
            if _last_fingerprint is None or fp != _last_fingerprint:
                lines = fp[2]
                logger.info(
                    "greenhouse log update detected (%s line(s)) — refreshing active decisions",
                    len(lines),
                )
                await asyncio.to_thread(rebuild_active_decisions)
                _last_fingerprint = fp
        except Exception as exc:  # noqa: BLE001
            logger.exception("Monitor error: %s", exc)
        await asyncio.sleep(poll_seconds)


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _monitor_task, _last_fingerprint, _heater_model, _infection_dataset, _last_climate_eval
    seeded = await asyncio.to_thread(ensure_sample_log, INFECTION_LOG)
    if seeded:
        logger.info("Seeded sample climate / infection action cards → %s", INFECTION_LOG)
    _heater_model = await asyncio.to_thread(load_or_train_heater_model, BASE_DIR)
    _infection_dataset = await asyncio.to_thread(load_infection_dataset, BASE_DIR)
    temperature, humidity, _light = _simulate_sensors()
    _last_climate_eval = evaluate_climate(temperature, TARGET_TEMPERATURE_C, humidity)
    await asyncio.to_thread(rebuild_active_decisions, "Tomato", "Fruiting")
    _last_fingerprint = file_fingerprint()
    _monitor_task = asyncio.create_task(infection_log_monitor(poll_seconds=1.0))
    yield
    if _monitor_task:
        _monitor_task.cancel()
        try:
            await _monitor_task
        except asyncio.CancelledError:
            pass


app = FastAPI(
    title="GreenHands AI Decision API",
    version="1.5.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "service": "greenhands-ai-decision",
        "latest_infection": _latest_infection,
        "updated_at": _latest_updated_at,
        "active_count": len(_active_decisions),
        "log_path": str(INFECTION_LOG),
        "log_lines": len(read_infection_log_lines()),
        "infection_log_lines": infection_line_count(INFECTION_LOG),
        "heater_model_ready": _heater_model is not None,
        "infection_dataset_rows": 0 if _infection_dataset is None else int(len(_infection_dataset)),
        "unresolved_infections": infection_line_count(INFECTION_LOG),
    }


class GreenhouseTelemetryResponse(BaseModel):
    temperature_c: float
    humidity_percent: float
    light_lux: float
    infection_count: int
    connection_state: str = "LIVE"
    updated_at: str = ""
    sensor_id: str = "gh-bay-01"
    heater_speed: float = 0.0
    heater_status: str = "Standby"
    heater_urgency: str = "Info"
    climate_optimal: bool = True
    target_temperature: float = TARGET_TEMPERATURE_C
    heater_logged: bool = False
    health: str = "Optimal"
    health_color: str = "green"
    health_summary: str = ""
    climate_level: str = "Optimal"
    infection_level: str = "Optimal"
    active_actuator: str = ""


class GreenhouseHealthResponse(BaseModel):
    health: str
    health_color: str
    health_summary: str
    climate_status: str
    climate_level: str
    climate_optimal: bool
    infection_count: int
    infection_level: str
    temperature_c: float
    humidity_percent: float
    target_temperature: float = TARGET_TEMPERATURE_C
    updated_at: str = ""


# Soft live sensor simulation. Amplitude is wide enough to leave the optimal
# band so the heater model and Automated Mode cards can be exercised live.
_telemetry_base = {
    "temperature_c": 23.4,
    "humidity_percent": 62.0,
    "light_lux": 7800.0,
}


def apply_heater_prediction(
    current_temperature: float,
    target_temperature: float,
    humidity: float,
    *,
    log_if_non_optimal: bool = True,
    require_model: bool = True,
) -> dict[str, Any]:
    """Run the Random Forest heater model and log the single live heater or fan action."""
    global _last_fingerprint, _last_climate_eval, _active_decisions
    evaluation = evaluate_climate(current_temperature, target_temperature, humidity)
    _last_climate_eval = evaluation
    if _heater_model is None:
        if require_model:
            raise HTTPException(status_code=503, detail="Heater model is not loaded.")
        speed = 0.0
    else:
        speed = predict_heater_speed(
            _heater_model,
            current_temperature,
            target_temperature,
            humidity,
        )
    action = heater_immediate_action(
        speed,
        current_temperature,
        target_temperature,
        humidity,
        evaluation.status,
    )
    logged_line: str | None = None
    needed = needed_climate_actuator(evaluation)
    if log_if_non_optimal and evaluation.non_optimal and _heater_model is not None:
        if needed == "heater":
            logged_line = append_heater_action(
                INFECTION_LOG,
                speed=speed,
                evaluation=evaluation,
                action=action,
            )
        elif needed == "fan":
            logged_line = append_fan_action(
                INFECTION_LOG,
                evaluation=evaluation,
            )
        if logged_line:
            logger.info(
                "Logged %s action (%s) → %s",
                needed,
                evaluation.status,
                INFECTION_LOG.name,
            )
            rebuild_active_decisions()
            _last_fingerprint = file_fingerprint()
    if _active_decisions:
        _active_decisions = apply_single_active_climate_rule(_active_decisions, evaluation)
    return {
        "heater_speed": speed,
        "unit": "percent",
        "status": evaluation.status,
        "urgency": evaluation.urgency,
        "climate_optimal": not evaluation.non_optimal,
        "logged": logged_line is not None,
        "timestamp": _utc_now(),
        "immediate_action": action,
        "current_temperature": round(current_temperature, 2),
        "target_temperature": round(target_temperature, 2),
        "humidity": round(humidity, 2),
        "log_line": logged_line or "",
        "active_actuator": needed or "",
    }


def _simulate_sensors() -> tuple[float, float, float]:
    import math
    import random

    t = time.time()
    wave = math.sin(t / 42.0)
    temperature = _telemetry_base["temperature_c"] + wave * 5.2 + random.uniform(-0.2, 0.15)
    humidity = _telemetry_base["humidity_percent"] + math.cos(t / 33.0) * 16.0 + random.uniform(-0.5, 0.5)
    light = _telemetry_base["light_lux"] + math.sin(t / 19.0) * 350 + random.uniform(-40, 40)
    return (
        round(temperature, 1),
        round(max(30.0, min(95.0, humidity)), 0),
        round(max(500.0, light), 0),
    )


def _greenhouse_health_fields(
    temperature: float,
    humidity: float,
    infection_count: int,
    *,
    target_temperature: float = TARGET_TEMPERATURE_C,
) -> dict[str, Any]:
    health = evaluate_greenhouse_health(
        temperature,
        target_temperature,
        humidity,
        infection_count,
    )
    payload = health.as_dict()
    payload["climate_status"] = health.climate_status
    payload["climate_optimal"] = health.climate_level == "Optimal"
    payload["infection_count"] = health.infection_count
    payload["temperature_c"] = round(temperature, 1)
    payload["humidity_percent"] = round(humidity, 0)
    payload["target_temperature"] = target_temperature
    payload["updated_at"] = _utc_now()
    return payload


def _next_telemetry_reading() -> dict[str, Any]:
    temperature, humidity, light = _simulate_sensors()
    heater = apply_heater_prediction(
        temperature,
        TARGET_TEMPERATURE_C,
        humidity,
        log_if_non_optimal=True,
        require_model=False,
    )
    infections = infection_line_count(INFECTION_LOG)
    health = _greenhouse_health_fields(temperature, humidity, infections)
    return {
        "temperature_c": temperature,
        "humidity_percent": humidity,
        "light_lux": light,
        "infection_count": infections,
        "connection_state": "LIVE",
        "updated_at": health["updated_at"],
        "sensor_id": "gh-bay-01",
        "heater_speed": heater["heater_speed"],
        "heater_status": heater["status"],
        "heater_urgency": heater["urgency"],
        "climate_optimal": health["climate_optimal"],
        "target_temperature": TARGET_TEMPERATURE_C,
        "heater_logged": heater["logged"],
        "health": health["health"],
        "health_color": health["health_color"],
        "health_summary": health["health_summary"],
        "climate_level": health["climate_level"],
        "infection_level": health["infection_level"],
        "active_actuator": heater.get("active_actuator") or "",
    }


@app.get("/api/v1/greenhouse/telemetry", response_model=GreenhouseTelemetryResponse)
def get_greenhouse_telemetry() -> dict[str, Any]:
    """Realtime greenhouse metrics for the Decision Making status card."""
    return _next_telemetry_reading()


@app.get("/api/v1/greenhouse/health", response_model=GreenhouseHealthResponse)
def get_greenhouse_health() -> dict[str, Any]:
    """Combined Optimal / Warning / Critical health from climate + unresolved infections."""
    temperature, humidity, _light = _simulate_sensors()
    infections = infection_line_count(INFECTION_LOG)
    return _greenhouse_health_fields(temperature, humidity, infections)


@app.get("/api/v1/ai-decision/active", response_model=ActiveDecisionsResponse)
def get_active_decisions() -> dict[str, Any]:
    """One decision per log line (infections + heater/climate actions)."""
    fp = file_fingerprint()
    global _last_fingerprint, _active_decisions
    if not _active_decisions or _last_fingerprint != fp:
        rebuild_active_decisions()
        _last_fingerprint = fp
    else:
        _active_decisions = apply_single_active_climate_rule(_active_decisions, _last_climate_eval)
    return {
        "count": len(_active_decisions),
        "updated_at": _latest_updated_at or _utc_now(),
        "log_path": str(INFECTION_LOG),
        "decisions": _active_decisions,
    }


@app.get("/api/v1/ai-decision/latest", response_model=AiDecisionResponse)
def get_latest_decision() -> dict[str, Any]:
    active = get_active_decisions()
    decisions = active["decisions"]
    if not decisions:
        raise HTTPException(status_code=404, detail="No infections in log.")
    return decisions[-1]


@app.post("/api/v1/ai-decision", response_model=AiDecisionResponse)
def create_ai_decision(body: AiDecisionRequest) -> dict[str, Any]:
    infection = (body.infection_text or body.disease or "").strip()
    if not infection:
        infection = read_latest_infection()
    if not infection:
        raise HTTPException(status_code=400, detail="No infection text provided or found in log.")
    try:
        with INFECTION_LOG.open("a", encoding="utf-8") as f:
            f.write(f"{infection}\n")
    except OSError as exc:
        logger.warning("Could not append infection_log.txt: %s", exc)
    decisions = rebuild_active_decisions(crop=body.crop, stage=body.stage)
    global _last_fingerprint
    _last_fingerprint = file_fingerprint()
    return decisions[-1] if decisions else build_decision(infection, body.crop, body.stage)


class HeaterPredictRequest(BaseModel):
    current_temperature: float
    target_temperature: float
    humidity: float


class HeaterPredictResponse(BaseModel):
    heater_speed: float
    unit: str = "percent"
    status: str = "Optimal"
    urgency: str = "Info"
    climate_optimal: bool = True
    logged: bool = False
    timestamp: str = ""
    immediate_action: str = ""
    current_temperature: float = 0.0
    target_temperature: float = TARGET_TEMPERATURE_C
    humidity: float = 0.0
    log_line: str = ""


class InfectionDecisionRequest(BaseModel):
    query: str | None = None
    symptoms: str | None = None
    plant_type: str | None = None
    infection_name: str | None = None


class InfectionDecisionResponse(BaseModel):
    plant_type: str
    infection_short_name: str
    infection_full_name: str
    severity_level: str
    visible_symptoms: str
    treatment_description: str
    biological_control: str
    chemical_control: str
    prevention_steps: str


@app.post("/api/v1/predict-heater", response_model=HeaterPredictResponse)
def predict_heater(body: HeaterPredictRequest) -> dict[str, Any]:
    """Predict heater speed 0–100% from heater_model.pkl; log when climate is non-optimal."""
    return apply_heater_prediction(
        body.current_temperature,
        body.target_temperature,
        body.humidity,
        log_if_non_optimal=True,
        require_model=True,
    )


@app.post("/api/v1/infection-decision", response_model=InfectionDecisionResponse)
def infection_decision(body: InfectionDecisionRequest) -> dict[str, str]:
    if _infection_dataset is None:
        raise HTTPException(status_code=503, detail="Infection dataset is not loaded.")
    if not any([body.query, body.symptoms, body.plant_type, body.infection_name]):
        raise HTTPException(status_code=400, detail="Provide query, symptoms, plant_type, or infection_name.")
    return match_infection_row(
        _infection_dataset,
        query=body.query,
        symptoms=body.symptoms,
        plant_type=body.plant_type,
        infection_name=body.infection_name,
    )


class PlantRequest(BaseModel):
    crop: str
    stage: str
    disease: str


@app.post("/predict-decision", response_model=AiDecisionResponse)
def predict_decision_legacy(data: PlantRequest) -> dict[str, Any]:
    try:
        with INFECTION_LOG.open("a", encoding="utf-8") as f:
            f.write(f"{data.disease}\n")
    except OSError:
        pass
    decisions = rebuild_active_decisions(crop=data.crop, stage=data.stage)
    global _last_fingerprint
    _last_fingerprint = file_fingerprint()
    return decisions[-1] if decisions else build_decision(data.disease, data.crop, data.stage)


if __name__ == "__main__":
    import socket
    import uvicorn

    def _lan_ips() -> list[str]:
        hosts: list[str] = []
        try:
            for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
                ip = info[4][0]
                if ip and not ip.startswith("127.") and ip not in hosts:
                    hosts.append(ip)
        except OSError:
            pass
        return hosts

    print("GreenHands AI listening on http://0.0.0.0:8002")
    for ip in _lan_ips():
        print(f"  Physical device URL: http://{ip}:8002")

    uvicorn.run("ai_server:app", host="0.0.0.0", port=8002, reload=True)
