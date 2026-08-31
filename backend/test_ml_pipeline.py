"""Sanity checks for the heater regressor and 500-row infection matcher."""

from pathlib import Path

from ml_pipeline import (
    INFECTION_FIELDS,
    load_infection_dataset,
    load_or_train_heater_model,
    match_infection_row,
    predict_heater_speed,
)

BASE = Path(__file__).resolve().parent


def test_heater_prediction_range() -> None:
    model = load_or_train_heater_model(BASE)
    speed = predict_heater_speed(model, 18.0, 26.0, 65.0)
    assert 0.0 <= speed <= 100.0
    warm = predict_heater_speed(model, 27.0, 26.0, 60.0)
    assert warm < speed


def test_infection_dataset_has_500_rows() -> None:
    df = load_infection_dataset(BASE)
    assert len(df) == 500
    assert list(df.columns)[:9] == list(INFECTION_FIELDS)


def test_powdery_mildew_tomato_match() -> None:
    df = load_infection_dataset(BASE)
    row = match_infection_row(df, infection_name="Powdery Mildew", plant_type="Tomato")
    assert row["infection_short_name"] == "Powdery Mildew"
    assert row["plant_type"] == "Tomato"
    assert set(row) == set(INFECTION_FIELDS)


if __name__ == "__main__":
    test_heater_prediction_range()
    test_infection_dataset_has_500_rows()
    test_powdery_mildew_tomato_match()
    print("ml_pipeline tests passed")
