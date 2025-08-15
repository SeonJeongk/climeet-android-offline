#!/usr/bin/env python3
"""Aggregate sanitized Climeet measurement samples with no external dependency."""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import Counter, defaultdict
from pathlib import Path
from typing import Iterable


V1_COLUMNS = (
    "runIndex",
    "scenarioId",
    "variant",
    "valid",
    "invalidReason",
    "durationMs",
    "requestCount",
    "requestBodyBytes",
    "responseBodyBytes",
    "outcome",
)
V1_NUMERIC_COLUMNS = ("durationMs", "requestCount", "requestBodyBytes", "responseBodyBytes")
COLUMNS = V1_COLUMNS

V2_COLUMNS = (
    "run_index",
    "scenario",
    "condition",
    "phase",
    "outcome",
    "flow_success",
    "correctness_pass",
    "false_success",
    "failure_category",
    "state_mismatch_count",
    "time_to_first_usable_ms",
    "time_to_expected_state_ms",
    "blocking_loading_ms",
    "finish_to_actionable_ms",
    "reconnect_to_server_record_ms",
    "request_count",
    "duplicate_request_count",
    "response_body_bytes",
    "automatic_retry_count",
    "manual_retry_count",
    "blocking_error_count",
    "local_record_loss_count",
    "server_duplicate_count",
)
V2_OPTIONAL_METRICS = (
    "time_to_first_usable_ms",
    "time_to_expected_state_ms",
    "blocking_loading_ms",
    "finish_to_actionable_ms",
    "reconnect_to_server_record_ms",
)
V2_COUNT_COLUMNS = (
    "state_mismatch_count",
    "request_count",
    "duplicate_request_count",
    "response_body_bytes",
    "automatic_retry_count",
    "manual_retry_count",
    "blocking_error_count",
    "local_record_loss_count",
    "server_duplicate_count",
)


def nearest_rank(values: list[int], percentile: int) -> int:
    if not values:
        raise ValueError("percentile requires at least one valid sample")
    ordered = sorted(values)
    rank = max(1, math.ceil(percentile / 100 * len(ordered)))
    return ordered[rank - 1]


def parse_boolean(value: str, name: str) -> bool:
    normalized = value.strip().lower()
    if normalized not in {"true", "false"}:
        raise ValueError(f"{name} must be true or false")
    return normalized == "true"


def parse_non_negative(value: str, name: str, *, optional: bool = False) -> int | None:
    if optional and not value.strip():
        return None
    parsed = int(value)
    if parsed < 0:
        raise ValueError(f"{name} must not be negative")
    return parsed


def detect_schema(fieldnames: tuple[str, ...], path: Path) -> int:
    if fieldnames == V1_COLUMNS:
        return 1
    if fieldnames == V2_COLUMNS:
        return 2
    raise ValueError(f"{path}: columns must exactly match public schema v1 or v2")


def read_samples(paths: Iterable[Path]) -> tuple[int, list[dict[str, object]]]:
    rows: list[dict[str, object]] = []
    schema_version: int | None = None
    for path in sorted(paths):
        with path.open(newline="", encoding="utf-8") as stream:
            reader = csv.DictReader(stream)
            current_schema = detect_schema(tuple(reader.fieldnames or ()), path)
            if schema_version is None:
                schema_version = current_schema
            elif schema_version != current_schema:
                raise ValueError("all sample files must use the same public schema version")
            for line_number, raw in enumerate(reader, start=2):
                try:
                    row: dict[str, object] = dict(raw)
                    if current_schema == 1:
                        row["valid"] = parse_boolean(raw["valid"], "valid")
                        row["runIndex"] = parse_non_negative(raw["runIndex"], "runIndex")
                        for column in V1_NUMERIC_COLUMNS:
                            row[column] = parse_non_negative(raw[column], column)
                        if row["valid"] and raw["invalidReason"]:
                            raise ValueError("a valid sample cannot have invalidReason")
                        if not row["valid"] and not raw["invalidReason"]:
                            raise ValueError("an invalid sample requires invalidReason")
                    else:
                        row["run_index"] = parse_non_negative(raw["run_index"], "run_index")
                        for column in ("flow_success", "correctness_pass", "false_success"):
                            row[column] = parse_boolean(raw[column], column)
                        for column in V2_COUNT_COLUMNS:
                            row[column] = parse_non_negative(raw[column], column)
                        for column in V2_OPTIONAL_METRICS:
                            row[column] = parse_non_negative(raw[column], column, optional=True)
                        if not raw["scenario"] or not raw["condition"] or not raw["phase"] or not raw["outcome"]:
                            raise ValueError("scenario, condition, phase, and outcome are required")
                        failed = not bool(row["flow_success"]) or not bool(row["correctness_pass"])
                        if failed and not raw["failure_category"]:
                            raise ValueError("failed app attempts require failure_category")
                        if not failed and raw["failure_category"]:
                            raise ValueError("successful correct attempts cannot have failure_category")
                        if bool(row["false_success"]) and bool(row["correctness_pass"]):
                            raise ValueError("false_success requires correctness_pass=false")
                except ValueError as error:
                    raise ValueError(f"{path}:{line_number}: {error}") from error
                rows.append(row)
    if schema_version is None:
        raise ValueError("at least one sample file is required")
    return schema_version, rows


def aggregate_v1(rows: list[dict[str, object]], source_names: list[str]) -> dict[str, object]:
    groups: dict[tuple[str, str], list[dict[str, object]]] = defaultdict(list)
    for row in rows:
        groups[(str(row["scenarioId"]), str(row["variant"]))].append(row)

    summaries = []
    for (scenario_id, variant), group_rows in sorted(groups.items()):
        valid_rows = [row for row in group_rows if bool(row["valid"])]
        invalid_rows = [row for row in group_rows if not bool(row["valid"])]
        metrics = {}
        for column in V1_NUMERIC_COLUMNS:
            values = [int(row[column]) for row in valid_rows]
            metrics[column] = {
                "p50": nearest_rank(values, 50) if values else None,
                "p90": nearest_rank(values, 90) if values else None,
                "p95": nearest_rank(values, 95) if values else None,
            }
        outcomes = Counter(str(row["outcome"]) for row in valid_rows)
        summaries.append(
            {
                "scenarioId": scenario_id,
                "variant": variant,
                "validSampleCount": len(valid_rows),
                "excludedSampleCount": len(invalid_rows),
                "invalidReasons": dict(sorted(Counter(str(row["invalidReason"]) for row in invalid_rows).items())),
                "successRate": round(outcomes.get("success", 0) / len(valid_rows), 6) if valid_rows else None,
                "outcomes": dict(sorted(outcomes.items())),
                "metrics": metrics,
            }
        )

    return {
        "schemaVersion": 1,
        "sampleFiles": sorted(source_names),
        "groups": summaries,
    }


def validate_v2_phase_links(rows: list[dict[str, object]]) -> None:
    s3_rows: dict[tuple[str, int], list[str]] = defaultdict(list)
    for row in rows:
        if str(row["scenario"]) == "S3":
            key = (str(row["condition"]), int(row["run_index"]))
            s3_rows[key].append(str(row["phase"]))
    for key, phases in sorted(s3_rows.items()):
        if sorted(phases) != ["offline", "reconnect"]:
            raise ValueError(f"S3 {key} must contain one offline and one reconnect row")


def percentiles(values: list[int]) -> dict[str, int | None]:
    return {
        "p50": nearest_rank(values, 50) if values else None,
        "p90": nearest_rank(values, 90) if values else None,
        "p95": nearest_rank(values, 95) if values else None,
    }


def aggregate_v2(rows: list[dict[str, object]], source_names: list[str]) -> dict[str, object]:
    validate_v2_phase_links(rows)
    groups: dict[tuple[str, str, str], list[dict[str, object]]] = defaultdict(list)
    for row in rows:
        groups[(str(row["scenario"]), str(row["condition"]), str(row["phase"]))].append(row)

    summaries = []
    for (scenario, condition, phase), group_rows in sorted(groups.items()):
        outcomes = Counter(str(row["outcome"]) for row in group_rows)
        metrics_by_outcome = {}
        for outcome in sorted(outcomes):
            metric_rows = [
                row for row in group_rows
                if str(row["outcome"]) == outcome and bool(row["correctness_pass"])
            ]
            metrics_by_outcome[outcome] = {
                column: percentiles([int(row[column]) for row in metric_rows if row[column] is not None])
                for column in V2_OPTIONAL_METRICS
            }
        summaries.append(
            {
                "scenario": scenario,
                "condition": condition,
                "phase": phase,
                "validAppAttemptCount": len(group_rows),
                "flowSuccessCount": sum(bool(row["flow_success"]) for row in group_rows),
                "successRate": round(
                    sum(bool(row["flow_success"]) for row in group_rows) / len(group_rows),
                    6,
                ) if group_rows else None,
                "correctnessPassCount": sum(bool(row["correctness_pass"]) for row in group_rows),
                "falseSuccessCount": sum(bool(row["false_success"]) for row in group_rows),
                "failureCategories": dict(sorted(Counter(
                    str(row["failure_category"]) for row in group_rows if row["failure_category"]
                ).items())),
                "outcomes": dict(sorted(outcomes.items())),
                "countTotals": {
                    column: sum(int(row[column]) for row in group_rows)
                    for column in V2_COUNT_COLUMNS
                },
                "metricsByOutcome": metrics_by_outcome,
            }
        )

    return {
        "schemaVersion": 2,
        "sampleFiles": sorted(source_names),
        "groups": summaries,
    }


def build_result(sample_paths: list[Path]) -> dict[str, object]:
    schema_version, rows = read_samples(sample_paths)
    source_names = [path.name for path in sample_paths]
    if schema_version == 1:
        return aggregate_v1(rows, source_names)
    return aggregate_v2(rows, source_names)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("samples", type=Path, nargs="+", help="sanitized sample CSV files")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    result = build_result(args.samples)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
