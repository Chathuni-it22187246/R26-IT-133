"""Tests for structured climate / heater action log helpers."""

from __future__ import annotations

import tempfile
from pathlib import Path

from climate_log import (
    TARGET_TEMPERATURE_C,
    append_fan_action,
    append_heater_action,
    apply_single_active_climate_rule,
    ensure_sample_log,
    evaluate_climate,
    evaluate_greenhouse_health,
    format_structured_line,
    heater_immediate_action,
    infection_line_count,
    is_structured_log_line,
    needed_climate_actuator,
    parse_structured_log_line,
    sample_seed_lines,
    synthesize_structured_decision,
)
from ml_pipeline import load_or_train_heater_model, predict_heater_speed

BASE = Path(__file__).resolve().parent


def test_cold_climate_is_non_optimal() -> None:
    evaluation = evaluate_climate(18.4, TARGET_TEMPERATURE_C, 64.0)
    assert evaluation.non_optimal
    assert evaluation.status == "Low Temperature"
    assert evaluation.urgency in {"High", "Critical"}


def test_optimal_climate() -> None:
    evaluation = evaluate_climate(24.0, TARGET_TEMPERATURE_C, 60.0)
    assert not evaluation.non_optimal
    assert evaluation.status == "Optimal"


def test_structured_line_roundtrip() -> None:
    line = format_structured_line(
        prefix="HEATER",
        kind="heater",
        speed=62.5,
        status="Low Temperature",
        urgency="High",
        current_temperature=18.4,
        target_temperature=26.0,
        humidity=64.0,
        timestamp="2026-08-30T09:48:00+00:00",
        action="Set heater output to 63%.",
    )
    parsed = parse_structured_log_line(line)
    assert parsed is not None
    assert parsed["_prefix"] == "HEATER"
    assert parsed["speed"] == "62.50"
    assert parsed["status"] == "Low Temperature"
    card = synthesize_structured_decision(
        parsed, log_line=line, line_index=1, log_path="infection_log.txt"
    )
    assert card["kind"] == "heater"
    assert card["category"] == "heater_fans"
    assert card["heater_speed"] == 62.5
    assert "63%" in card["title"] or "62%" in card["title"]
    assert card["immediate_action"]
    assert card.get("lifecycle") == "Active"


def test_sample_seed_has_infections_and_climate() -> None:
    lines = sample_seed_lines(now="2026-08-30T09:48:00+00:00")
    assert any(ln == "Powdery Mildew" for ln in lines)
    climate = [ln for ln in lines if is_structured_log_line(ln)]
    assert len(climate) >= 3
    assert any(ln.startswith("HEATER|") for ln in climate)
    assert any("kind=fan" in ln for ln in climate)


def test_ensure_sample_log_empty_and_append(tmp_path: Path | None = None) -> None:
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "infection_log.txt"
        assert ensure_sample_log(path)
        lines = path.read_text(encoding="utf-8").splitlines()
        assert any(is_structured_log_line(ln) for ln in lines)
        assert infection_line_count(path) >= 1
        assert ensure_sample_log(path) is False

        infection_only = Path(folder) / "infections_only.txt"
        infection_only.write_text("Powdery Mildew\nLate Blight\n", encoding="utf-8")
        assert ensure_sample_log(infection_only)
        text = infection_only.read_text(encoding="utf-8")
        assert "Powdery Mildew" in text
        assert "HEATER|" in text
        assert infection_line_count(infection_only) == 2


def test_append_heater_skips_optimal_climate() -> None:
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "infection_log.txt"
        evaluation = evaluate_climate(24.0, 26.0, 60.0)
        assert append_heater_action(path, speed=12.0, evaluation=evaluation) is None
        evaluation_cold = evaluate_climate(18.0, 26.0, 60.0)
        line = append_heater_action(path, speed=61.0, evaluation=evaluation_cold)
        assert line is not None
        assert line.startswith("HEATER|")
        # Dedupe: same status and similar speed should not write again immediately.
        assert append_heater_action(path, speed=62.0, evaluation=evaluation_cold) is None


def test_heater_model_speed_when_cold() -> None:
    model = load_or_train_heater_model(BASE)
    cold = predict_heater_speed(model, 18.0, 26.0, 65.0)
    warm = predict_heater_speed(model, 27.0, 26.0, 60.0)
    assert 0.0 <= cold <= 100.0
    assert 0.0 <= warm <= 100.0
    assert cold > warm
    action = heater_immediate_action(cold, 18.0, 26.0, 65.0, "Low Temperature")
    assert f"{cold:.0f}%" in action


def test_greenhouse_health_optimal() -> None:
    from climate_log import evaluate_greenhouse_health

    health = evaluate_greenhouse_health(24.0, 26.0, 62.0, 0)
    assert health.health == "Optimal"
    assert health.color == "green"
    assert health.climate_level == "Optimal"
    assert health.infection_level == "Optimal"


def test_greenhouse_health_warning_climate_or_one_infection() -> None:
    from climate_log import evaluate_greenhouse_health

    climate_only = evaluate_greenhouse_health(21.0, 26.0, 62.0, 0)
    assert climate_only.health == "Warning"
    assert climate_only.color == "yellow"
    one_infection = evaluate_greenhouse_health(24.0, 26.0, 62.0, 1)
    assert one_infection.health == "Warning"
    assert one_infection.infection_level == "Warning"


def test_greenhouse_health_critical_from_infections_or_severe_climate() -> None:
    from climate_log import evaluate_greenhouse_health

    many = evaluate_greenhouse_health(24.0, 26.0, 62.0, 3)
    assert many.health == "Critical"
    assert many.color == "red"
    assert "unresolved" in many.summary.lower()
    cold = evaluate_greenhouse_health(18.0, 26.0, 64.0, 0)
    assert cold.health == "Critical"
    assert cold.climate_level == "Critical"


def test_single_active_heater_when_cold() -> None:
    evaluation = evaluate_climate(18.0, 26.0, 64.0)
    assert needed_climate_actuator(evaluation) == "heater"
    decisions = [
        {"kind": "heater", "title": "old heater"},
        {"kind": "fan", "title": "old fan"},
        {"kind": "heater", "title": "current heater"},
        {"kind": "infection", "title": "blight"},
    ]
    resolved = apply_single_active_climate_rule(decisions, evaluation)
    assert resolved[0]["lifecycle"] == "Completed"
    assert resolved[1]["lifecycle"] == "Completed"
    assert resolved[2]["lifecycle"] == "Active"
    assert "lifecycle" not in resolved[3]


def test_all_climate_actions_completed_when_optimal() -> None:
    evaluation = evaluate_climate(24.0, 26.0, 62.0)
    assert needed_climate_actuator(evaluation) is None
    resolved = apply_single_active_climate_rule(
        [{"kind": "heater"}, {"kind": "fan"}],
        evaluation,
    )
    assert resolved[0]["lifecycle"] == "Completed"
    assert resolved[1]["lifecycle"] == "Completed"


def test_append_fan_when_hot() -> None:
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "infection_log.txt"
        hot = evaluate_climate(30.0, 26.0, 70.0)
        assert needed_climate_actuator(hot) == "fan"
        assert append_heater_action(path, speed=5.0, evaluation=hot) is None
        line = append_fan_action(path, evaluation=hot)
        assert line is not None
        assert "kind=fan" in line
        assert "lifecycle=Active" in line


if __name__ == "__main__":
    test_cold_climate_is_non_optimal()
    test_optimal_climate()
    test_structured_line_roundtrip()
    test_sample_seed_has_infections_and_climate()
    test_ensure_sample_log_empty_and_append()
    test_append_heater_skips_optimal_climate()
    test_heater_model_speed_when_cold()
    test_greenhouse_health_optimal()
    test_greenhouse_health_warning_climate_or_one_infection()
    test_greenhouse_health_critical_from_infections_or_severe_climate()
    test_single_active_heater_when_cold()
    test_all_climate_actions_completed_when_optimal()
    test_append_fan_when_hot()
    print("climate_log tests passed")
