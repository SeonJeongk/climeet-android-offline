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


COLUMNS = (
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
NUMERIC_COLUMNS = ("durationMs", "requestCount", "requestBodyBytes", "responseBodyBytes")


def nearest_rank(values: list[int], percentile: int) -> int:
    if not values:
        raise ValueError("percentile requires at least one valid sample")
    ordered = sorted(values)
    rank = max(1, math.ceil(percentile / 100 * len(ordered)))
    return ordered[rank - 1]


def read_samples(paths: Iterable[Path]) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for path in sorted(paths):
        with path.open(newline="", encoding="utf-8") as stream:
            reader = csv.DictReader(stream)
            if tuple(reader.fieldnames or ()) != COLUMNS:
                raise ValueError(f"{path}: columns must exactly match the public schema")
            for line_number, raw in enumerate(reader, start=2):
                try:
                    valid_text = raw["valid"].strip().lower()
                    if valid_text not in {"true", "false"}:
                        raise ValueError("valid must be true or false")
                    row: dict[str, object] = dict(raw)
                    row["valid"] = valid_text == "true"
                    row["runIndex"] = int(raw["runIndex"])
                    for column in NUMERIC_COLUMNS:
                        value = int(raw[column])
                        if value < 0:
                            raise ValueError(f"{column} must not be negative")
                        row[column] = value
                    if row["valid"] and raw["invalidReason"]:
                        raise ValueError("a valid sample cannot have invalidReason")
                    if not row["valid"] and not raw["invalidReason"]:
                        raise ValueError("an invalid sample requires invalidReason")
                except ValueError as error:
                    raise ValueError(f"{path}:{line_number}: {error}") from error
                rows.append(row)
    return rows


def aggregate(rows: list[dict[str, object]], source_names: list[str]) -> dict[str, object]:
    groups: dict[tuple[str, str], list[dict[str, object]]] = defaultdict(list)
    for row in rows:
        groups[(str(row["scenarioId"]), str(row["variant"]))].append(row)

    summaries = []
    for (scenario_id, variant), group_rows in sorted(groups.items()):
        valid_rows = [row for row in group_rows if bool(row["valid"])]
        invalid_rows = [row for row in group_rows if not bool(row["valid"])]
        metrics = {}
        for column in NUMERIC_COLUMNS:
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


def build_result(sample_paths: list[Path]) -> dict[str, object]:
    return aggregate(read_samples(sample_paths), [path.name for path in sample_paths])


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
