"""Structured greenhouse action-log entries (heater / climate + mock actuators).

infection_log.txt remains the single active log. Plain lines are infections;
prefixed HEATER / CLIMATE / ACTION lines are climate-control decisions that the
Android Automated Mode renders as action cards.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

STRUCTURED_PREFIXES = {"HEATER", "CLIMATE", "ACTION"}

TARGET_TEMPERATURE_C = 26.0
OPTIMAL_TEMP_C = (22.0, 28.0)
OPTIMAL_HUMIDITY = (55.0, 75.0)

LOG_DEDUPE_SECONDS = 45.0
SPEED_DEDUPE_DELTA = 8.0

LIFECYCLE_ACTIVE = "Active"
LIFECYCLE_COMPLETED = "Completed"
HEATER_KINDS = {"heater", "climate", "humidity"}
FAN_KINDS = {"fan"}
CLIMATE_ACTUATOR_KINDS = HEATER_KINDS | FAN_KINDS


HEALTH_OPTIMAL = "Optimal"
HEALTH_WARNING = "Warning"
HEALTH_CRITICAL = "Critical"
HEALTH_COLORS = {
    HEALTH_OPTIMAL: "green",
    HEALTH_WARNING: "yellow",
    HEALTH_CRITICAL: "red",
}
_HEALTH_RANK = {HEALTH_OPTIMAL: 0, HEALTH_WARNING: 1, HEALTH_CRITICAL: 2}


@dataclass(frozen=True)
class ClimateEvaluation:
    current_temperature: float
    target_temperature: float
    humidity: float
    temp_optimal: bool
    humidity_optimal: bool
    non_optimal: bool
    status: str
    urgency: str
    reasons: tuple[str, ...]


@dataclass(frozen=True)
class GreenhouseHealth:
    health: str
    color: str
    summary: str
    climate_status: str
    climate_level: str
    infection_level: str
    infection_count: int

    def as_dict(self) -> dict[str, Any]:
        return {
            "health": self.health,
            "health_color": self.color,
            "health_summary": self.summary,
            "climate_level": self.climate_level,
            "infection_level": self.infection_level,
        }


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_iso(ts: str) -> datetime | None:
    raw = (ts or "").strip()
    if not raw:
        return None
    try:
        if raw.endswith("Z"):
            raw = raw[:-1] + "+00:00"
        return datetime.fromisoformat(raw)
    except ValueError:
        return None


def evaluate_climate(
    current_temperature: float,
    target_temperature: float,
    humidity: float,
) -> ClimateEvaluation:
    reasons: list[str] = []
    if current_temperature < OPTIMAL_TEMP_C[0]:
        reasons.append("Low Temperature")
    elif current_temperature > OPTIMAL_TEMP_C[1]:
        reasons.append("High Temperature")
    if humidity < OPTIMAL_HUMIDITY[0]:
        reasons.append("Low Humidity")
    elif humidity > OPTIMAL_HUMIDITY[1]:
        reasons.append("High Humidity")

    temp_optimal = OPTIMAL_TEMP_C[0] <= current_temperature <= OPTIMAL_TEMP_C[1]
    humidity_optimal = OPTIMAL_HUMIDITY[0] <= humidity <= OPTIMAL_HUMIDITY[1]
    if not reasons:
        status = "Optimal"
        urgency = "Info"
    else:
        status = " + ".join(reasons)
        severe_temp = current_temperature < 20.0 or current_temperature > 32.0
        severe_humidity = humidity < 40.0 or humidity > 90.0
        if severe_temp or severe_humidity:
            urgency = "Critical"
        elif not temp_optimal:
            urgency = "High"
        else:
            urgency = "Moderate"

    return ClimateEvaluation(
        current_temperature=current_temperature,
        target_temperature=target_temperature,
        humidity=humidity,
        temp_optimal=temp_optimal,
        humidity_optimal=humidity_optimal,
        non_optimal=bool(reasons),
        status=status,
        urgency=urgency,
        reasons=tuple(reasons),
    )


def _worse_health(*levels: str) -> str:
    worst = HEALTH_OPTIMAL
    for level in levels:
        if _HEALTH_RANK.get(level, 0) > _HEALTH_RANK[worst]:
            worst = level
    return worst


def climate_health_level(evaluation: ClimateEvaluation) -> str:
    if evaluation.urgency == "Critical":
        return HEALTH_CRITICAL
    if evaluation.non_optimal:
        return HEALTH_WARNING
    return HEALTH_OPTIMAL


def infection_health_level(infection_count: int) -> str:
    count = max(0, int(infection_count))
    if count >= 2:
        return HEALTH_CRITICAL
    if count == 1:
        return HEALTH_WARNING
    return HEALTH_OPTIMAL


def _health_summary(
    *,
    health: str,
    climate: ClimateEvaluation,
    infection_count: int,
) -> str:
    if health == HEALTH_OPTIMAL:
        return "Climate on target and no unresolved infections."
    climate_bit = (
        f"{climate.status} ({climate.current_temperature:.1f}°C vs "
        f"{climate.target_temperature:.1f}°C target, {climate.humidity:.0f}% RH)"
        if climate.non_optimal
        else "climate on target"
    )
    if infection_count <= 0:
        infection_bit = "no unresolved infections"
    elif infection_count == 1:
        infection_bit = "1 unresolved infection"
    else:
        infection_bit = f"{infection_count} unresolved infections"
    return f"{health}: {climate_bit} · {infection_bit}."


def evaluate_greenhouse_health(
    current_temperature: float,
    target_temperature: float,
    humidity: float,
    infection_count: int,
) -> GreenhouseHealth:
    """Combine climate deviation and unresolved infection count into one health state."""
    climate = evaluate_climate(current_temperature, target_temperature, humidity)
    infections = max(0, int(infection_count))
    climate_level = climate_health_level(climate)
    infection_level = infection_health_level(infections)
    overall = _worse_health(climate_level, infection_level)
    return GreenhouseHealth(
        health=overall,
        color=HEALTH_COLORS[overall],
        summary=_health_summary(health=overall, climate=climate, infection_count=infections),
        climate_status=climate.status,
        climate_level=climate_level,
        infection_level=infection_level,
        infection_count=infections,
    )


def needed_climate_actuator(evaluation: ClimateEvaluation) -> str | None:
    """Which heater/fan action should be Active right now, or None if climate is on target."""
    if not evaluation.non_optimal:
        return None
    if "Low Temperature" in evaluation.status or "Low Humidity" in evaluation.status:
        return "heater"
    if "High Temperature" in evaluation.status or "High Humidity" in evaluation.status:
        return "fan"
    return "heater"


def is_climate_actuator_decision(decision: dict[str, Any]) -> bool:
    kind = str(decision.get("kind") or "").lower()
    if kind in CLIMATE_ACTUATOR_KINDS:
        return True
    return str(decision.get("category") or "").lower() == "heater_fans" and kind not in {
        "water",
        "pump",
        "irrigation",
        "other",
        "infection",
    }


def _matches_needed_actuator(kind: str, needed: str) -> bool:
    kind = (kind or "").lower()
    if needed == "heater":
        return kind in HEATER_KINDS
    if needed == "fan":
        return kind in FAN_KINDS
    return False


def apply_single_active_climate_rule(
    decisions: list[dict[str, Any]],
    evaluation: ClimateEvaluation | None,
) -> list[dict[str, Any]]:
    """Only the current heater or fan action is Active; all other climate cards are Completed."""
    needed = needed_climate_actuator(evaluation) if evaluation is not None else None
    active_index: int | None = None
    if needed:
        for idx in range(len(decisions) - 1, -1, -1):
            kind = str(decisions[idx].get("kind") or "").lower()
            if _matches_needed_actuator(kind, needed):
                active_index = idx
                break

    resolved: list[dict[str, Any]] = []
    for idx, decision in enumerate(decisions):
        if not is_climate_actuator_decision(decision):
            resolved.append(decision)
            continue
        item = dict(decision)
        if idx == active_index:
            item["lifecycle"] = LIFECYCLE_ACTIVE
        else:
            item["lifecycle"] = LIFECYCLE_COMPLETED
        resolved.append(item)
    return resolved


def heater_immediate_action(
    speed: float,
    current_temperature: float,
    target_temperature: float,
    humidity: float,
    status: str,
) -> str:
    deficit = target_temperature - current_temperature
    speed_txt = f"{speed:.0f}%"
    if "Low Temperature" in status:
        return (
            f"Set heater output to {speed_txt} immediately to close a {abs(deficit):.1f}°C deficit "
            f"({current_temperature:.1f}°C now vs {target_temperature:.1f}°C target)."
        )
    if "High Temperature" in status:
        return (
            f"Reduce heater to {speed_txt} and increase airflow — greenhouse is "
            f"{abs(deficit):.1f}°C above the {target_temperature:.1f}°C target."
        )
    if "Low Humidity" in status:
        return (
            f"Hold heater at {speed_txt} while recovering humidity from {humidity:.0f}% "
            f"toward {OPTIMAL_HUMIDITY[0]:.0f}–{OPTIMAL_HUMIDITY[1]:.0f}%."
        )
    if "High Humidity" in status:
        return (
            f"Hold heater at {speed_txt} and vent moisture — humidity is {humidity:.0f}% "
            f"(target {OPTIMAL_HUMIDITY[0]:.0f}–{OPTIMAL_HUMIDITY[1]:.0f}%)."
        )
    if speed <= 1.0:
        return (
            f"Heater standby at {speed_txt}. Climate is on target "
            f"({current_temperature:.1f}°C / {humidity:.0f}% RH)."
        )
    return (
        f"Set heater output to {speed_txt} to track the {target_temperature:.1f}°C target "
        f"(currently {current_temperature:.1f}°C, {humidity:.0f}% RH)."
    )


def fan_immediate_action(
    current_temperature: float,
    target_temperature: float,
    humidity: float,
    status: str,
) -> str:
    if "High Temperature" in status:
        overshoot = current_temperature - target_temperature
        return (
            f"Increase cooling fans immediately to dump {abs(overshoot):.1f}°C of excess heat "
            f"({current_temperature:.1f}°C now vs {target_temperature:.1f}°C target)."
        )
    if "High Humidity" in status:
        return (
            f"Run circulation fans and vent moisture — humidity is {humidity:.0f}% "
            f"(target {OPTIMAL_HUMIDITY[0]:.0f}–{OPTIMAL_HUMIDITY[1]:.0f}%)."
        )
    return (
        f"Run circulation fans to mix air "
        f"({current_temperature:.1f}°C, {humidity:.0f}% RH)."
    )


def _sanitize_action(text: str) -> str:
    return " ".join((text or "").replace("|", "/").split())


def format_structured_line(
    *,
    prefix: str,
    speed: float | None = None,
    status: str,
    urgency: str,
    current_temperature: float | None = None,
    target_temperature: float | None = None,
    humidity: float | None = None,
    timestamp: str | None = None,
    action: str,
    kind: str | None = None,
    lifecycle: str | None = None,
) -> str:
    parts = [prefix.upper()]
    if kind:
        parts.append(f"kind={kind}")
    if speed is not None:
        parts.append(f"speed={speed:.2f}")
    parts.append(f"status={_sanitize_action(status)}")
    parts.append(f"urgency={urgency}")
    if current_temperature is not None:
        parts.append(f"temp={current_temperature:.2f}")
    if target_temperature is not None:
        parts.append(f"target={target_temperature:.2f}")
    if humidity is not None:
        parts.append(f"humidity={humidity:.2f}")
    parts.append(f"lifecycle={lifecycle or LIFECYCLE_ACTIVE}")
    parts.append(f"ts={timestamp or utc_now()}")
    parts.append(f"action={_sanitize_action(action)}")
    return "|".join(parts)


def parse_structured_log_line(raw: str) -> dict[str, str] | None:
    line = (raw or "").strip()
    if not line or "|" not in line:
        return None
    prefix, rest = line.split("|", 1)
    prefix = prefix.strip().upper()
    if prefix not in STRUCTURED_PREFIXES:
        return None
    fields: dict[str, str] = {"_prefix": prefix}
    for part in rest.split("|"):
        if "=" not in part:
            continue
        key, value = part.split("=", 1)
        fields[key.strip().lower()] = value.strip()
    return fields


def is_structured_log_line(raw: str) -> bool:
    return parse_structured_log_line(raw) is not None


def _float_field(fields: dict[str, str], *keys: str) -> float | None:
    for key in keys:
        raw = fields.get(key)
        if raw is None or raw == "":
            continue
        try:
            return float(raw)
        except ValueError:
            continue
    return None


def category_for_kind(kind: str, prefix: str) -> str:
    kind = (kind or "").lower()
    if kind in {"water", "pump", "irrigation"}:
        return "water_pump"
    if kind in {"other", "nutrient", "fertigation"}:
        return "other"
    if prefix in {"HEATER", "CLIMATE"} or kind in {"heater", "climate", "fan", "humidity"}:
        return "heater_fans"
    return "infections"


def synthesize_structured_decision(
    fields: dict[str, str],
    *,
    log_line: str,
    line_index: int,
    log_path: str,
) -> dict[str, Any]:
    prefix = fields.get("_prefix", "HEATER")
    kind = (fields.get("kind") or ("heater" if prefix == "HEATER" else prefix.lower())).lower()
    speed = _float_field(fields, "speed", "heater_speed")
    temp = _float_field(fields, "temp", "current_temp", "current_temperature")
    target = _float_field(fields, "target", "target_temp", "target_temperature")
    humidity = _float_field(fields, "humidity")
    status = fields.get("status") or "Active"
    urgency = fields.get("urgency") or "Moderate"
    action = fields.get("action") or "Review greenhouse climate controls."
    timestamp = fields.get("ts") or utc_now()
    category = category_for_kind(kind, prefix)

    if kind in {"heater", "climate", "humidity"} or prefix == "HEATER":
        speed_label = f"{speed:.0f}%" if speed is not None else "standby"
        if kind == "humidity":
            title = f"Climate Control: {speed_label} AI Output"
        else:
            title = f"Heating Unit: {speed_label} AI Output"
        description = (
            f"{status} — AI heater commanded to {speed_label}"
            + (f" ({temp:.1f}°C vs {target:.1f}°C target)." if temp is not None and target is not None else ".")
        )
        infection_name = "Heating Unit"
        kind = "heater" if prefix == "HEATER" or kind == "heater" else kind
        category = "heater_fans"
    elif kind == "fan":
        title = "Cooling Fans: Automated Circulation"
        description = action
        infection_name = "Cooling Fans"
    elif kind in {"water", "pump", "irrigation"}:
        title = "Water Pump: Automated Irrigation"
        description = action
        infection_name = "Water Pump"
        kind = "water"
        category = "water_pump"
    else:
        title = "Greenhouse Action: Automated Control"
        description = action
        infection_name = status

    slug = f"{kind}-{line_index}"
    environmental = []
    if temp is not None:
        environmental.append(f"Current temperature {temp:.1f}°C")
    if target is not None:
        environmental.append(f"Target temperature {target:.1f}°C")
    if humidity is not None:
        environmental.append(f"Humidity {humidity:.0f}%")
    if speed is not None:
        environmental.append(f"Predicted heater speed {speed:.0f}%")

    return {
        "title": title,
        "description": description,
        "urgency": urgency,
        "infection_name": infection_name,
        "severity_level": urgency,
        "immediate_action": action,
        "biological_treatment": [
            "AI Random Forest heater model (heater_model.pkl)" if category == "heater_fans" and kind != "fan" else "Automated greenhouse actuator"
        ],
        "chemical_control": [],
        "prevention": [
            f"Keep greenhouse between {OPTIMAL_TEMP_C[0]:.0f}–{OPTIMAL_TEMP_C[1]:.0f}°C "
            f"and {OPTIMAL_HUMIDITY[0]:.0f}–{OPTIMAL_HUMIDITY[1]:.0f}% RH."
        ],
        "environmental_adjustments": environmental,
        "sources": ["heater_model.pkl"] if kind in {"heater", "climate", "humidity"} else [],
        "crop": "",
        "stage": "",
        "updated_at": timestamp,
        "log_line": log_line,
        "log_path": log_path,
        "line_index": line_index,
        "decision_id": f"log-{slug}-{status.lower().replace(' ', '-')}",
        "kind": kind,
        "category": category,
        "heater_speed": speed,
        "current_temperature": temp,
        "target_temperature": target,
        "humidity": humidity,
        "climate_status": status,
        "lifecycle": fields.get("lifecycle") or LIFECYCLE_ACTIVE,
    }


def read_log_lines(path: Path) -> list[str]:
    if not path.exists():
        return []
    return [ln.strip() for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]


def infection_line_count(path: Path) -> int:
    return sum(1 for ln in read_log_lines(path) if not is_structured_log_line(ln))


def has_climate_entries(path: Path) -> bool:
    return any(is_structured_log_line(ln) for ln in read_log_lines(path))


def sample_seed_lines(*, now: str | None = None) -> list[str]:
    ts = now or utc_now()
    heater_speed = 62.50
    humidity_speed = 18.20
    return [
        "Powdery Mildew",
        format_structured_line(
            prefix="HEATER",
            kind="heater",
            speed=heater_speed,
            status="Low Temperature",
            urgency="High",
            current_temperature=18.40,
            target_temperature=TARGET_TEMPERATURE_C,
            humidity=64.00,
            timestamp=ts,
            action=heater_immediate_action(heater_speed, 18.40, TARGET_TEMPERATURE_C, 64.00, "Low Temperature"),
        ),
        format_structured_line(
            prefix="CLIMATE",
            kind="humidity",
            speed=humidity_speed,
            status="Low Humidity",
            urgency="Moderate",
            current_temperature=24.80,
            target_temperature=TARGET_TEMPERATURE_C,
            humidity=42.00,
            timestamp=ts,
            action=heater_immediate_action(humidity_speed, 24.80, TARGET_TEMPERATURE_C, 42.00, "Low Humidity"),
        ),
        format_structured_line(
            prefix="ACTION",
            kind="fan",
            status="High Temperature",
            urgency="Moderate",
            current_temperature=29.20,
            target_temperature=TARGET_TEMPERATURE_C,
            humidity=70.00,
            timestamp=ts,
            lifecycle=LIFECYCLE_COMPLETED,
            action=fan_immediate_action(29.20, TARGET_TEMPERATURE_C, 70.00, "High Temperature"),
        ),
        format_structured_line(
            prefix="ACTION",
            kind="water",
            status="Monitoring",
            urgency="Info",
            timestamp=ts,
            action="Hold irrigation; soil moisture is within band while climate recovers.",
        ),
        "Late Blight",
    ]


def climate_sample_lines(*, now: str | None = None) -> list[str]:
    """Climate / actuator samples only (appended next to existing infection lines)."""
    return [ln for ln in sample_seed_lines(now=now) if is_structured_log_line(ln)]


def ensure_sample_log(path: Path) -> bool:
    """If the log is empty, write sample infections + climate actions.

    If the log already has infections but no climate/heater rows, append mock
    climate actions so Automated Mode can show operational cards during testing.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    existing = read_log_lines(path)
    if not existing:
        path.write_text("\n".join(sample_seed_lines()) + "\n", encoding="utf-8")
        return True
    if not has_climate_entries(path):
        with path.open("a", encoding="utf-8") as handle:
            handle.write("\n".join(climate_sample_lines()) + "\n")
        return True
    return False


def _last_actuator_entry(path: Path, *, kinds: set[str], prefixes: set[str]) -> dict[str, str] | None:
    for line in reversed(read_log_lines(path)):
        parsed = parse_structured_log_line(line)
        if not parsed:
            continue
        kind = (parsed.get("kind") or "").lower()
        prefix = parsed.get("_prefix") or ""
        if kind in kinds or prefix in prefixes:
            return parsed
    return None


def should_append_actuator_entry(
    path: Path,
    *,
    status: str,
    speed: float | None,
    kinds: set[str],
    prefixes: set[str],
) -> bool:
    last = _last_actuator_entry(path, kinds=kinds, prefixes=prefixes)
    if last is None:
        return True
    last_status = last.get("status") or ""
    if last_status != status:
        return True
    if speed is not None:
        last_speed = _float_field(last, "speed", "heater_speed")
        if last_speed is None or abs(last_speed - speed) >= SPEED_DEDUPE_DELTA:
            return True
    last_ts = parse_iso(last.get("ts") or "")
    if last_ts is None:
        return True
    now = datetime.now(timezone.utc)
    if last_ts.tzinfo is None:
        last_ts = last_ts.replace(tzinfo=timezone.utc)
    elapsed = (now - last_ts).total_seconds()
    return elapsed >= LOG_DEDUPE_SECONDS


def append_heater_action(
    path: Path,
    *,
    speed: float,
    evaluation: ClimateEvaluation,
    action: str | None = None,
) -> str | None:
    """Append a HEATER log line when a heater action is the live need."""
    if needed_climate_actuator(evaluation) != "heater":
        return None
    if not should_append_actuator_entry(
        path,
        status=evaluation.status,
        speed=speed,
        kinds=HEATER_KINDS,
        prefixes={"HEATER", "CLIMATE"},
    ):
        return None
    action_text = action or heater_immediate_action(
        speed,
        evaluation.current_temperature,
        evaluation.target_temperature,
        evaluation.humidity,
        evaluation.status,
    )
    line = format_structured_line(
        prefix="HEATER",
        kind="heater",
        speed=speed,
        status=evaluation.status,
        urgency=evaluation.urgency,
        current_temperature=evaluation.current_temperature,
        target_temperature=evaluation.target_temperature,
        humidity=evaluation.humidity,
        lifecycle=LIFECYCLE_ACTIVE,
        action=action_text,
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(line + "\n")
    return line


def append_fan_action(
    path: Path,
    *,
    evaluation: ClimateEvaluation,
    action: str | None = None,
) -> str | None:
    """Append a fan ACTION line when cooling/venting is the live need."""
    if needed_climate_actuator(evaluation) != "fan":
        return None
    if not should_append_actuator_entry(
        path,
        status=evaluation.status,
        speed=None,
        kinds=FAN_KINDS,
        prefixes=set(),
    ):
        return None
    action_text = action or fan_immediate_action(
        evaluation.current_temperature,
        evaluation.target_temperature,
        evaluation.humidity,
        evaluation.status,
    )
    line = format_structured_line(
        prefix="ACTION",
        kind="fan",
        status=evaluation.status,
        urgency=evaluation.urgency,
        current_temperature=evaluation.current_temperature,
        target_temperature=evaluation.target_temperature,
        humidity=evaluation.humidity,
        lifecycle=LIFECYCLE_ACTIVE,
        action=action_text,
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(line + "\n")
    return line
