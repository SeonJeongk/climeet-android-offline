#!/usr/bin/env python3
"""Publish sanitized baseline/after route-cache re-entry measurements as public schema v3."""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

from aggregate_evidence import V3_COLUMNS, build_result, parse_boolean


RAW_REQUIRED = {
    "iteration",
    "is_warmup",
    "infrastructure_valid",
    "scenario",
    "condition",
    "phase",
    "outcome",
    "flow_success",
    "correctness_pass",
    "false_success",
    "state_mismatch_count",
    "catalog_version_match",
    "filter_result_match",
    "full_catalog_match",
    "stale_age_ms",
    "time_to_first_usable_ms",
    "time_to_expected_state_ms",
    "request_count",
    "duplicate_request_count",
    "response_body_bytes",
    "blocking_error_count",
}
# Only the re-entry conditions behind the published claims are public.
EXPECTED_GROUPS = {
    ("ROUTE_REENTRY", "clean", "single"),
    ("ROUTE_REENTRY", "latency_3000", "single"),
}
STALE_GROUPS = {
    ("ROUTE_REENTRY", "latency_3000", "single"),
}
FAILURE_CATEGORIES = {
    "catalog_version_mismatch",
    "filter_result_mismatch",
    "full_catalog_mismatch",
    "multiple_state_mismatches",
    "false_success",
    "app_flow_failure",
    "correctness_failure",
}
PRIVATE_MARKERS = ("serial", "fingerprint", "/Users/", "Perfetto", "digest", "trace", "run_id")


def normalized_boolean(value: str) -> str:
    if not value.strip():
        return ""
    return str(parse_boolean(value, "measurement boolean")).lower()


def failure_category(raw: dict[str, str]) -> str:
    correctness = parse_boolean(raw["correctness_pass"], "correctness_pass")
    flow = parse_boolean(raw["flow_success"], "flow_success")
    false_success = parse_boolean(raw["false_success"], "false_success")
    if correctness and flow:
        return ""
    mismatches = []
    if normalized_boolean(raw["catalog_version_match"]) == "false":
        mismatches.append("catalog_version_mismatch")
    if normalized_boolean(raw["filter_result_match"]) == "false":
        mismatches.append("filter_result_mismatch")
    if raw["full_catalog_match"].strip() and normalized_boolean(raw["full_catalog_match"]) == "false":
        mismatches.append("full_catalog_mismatch")
    if len(mismatches) > 1:
        return "multiple_state_mismatches"
    if mismatches:
        return mismatches[0]
    if false_success:
        return "false_success"
    if not flow:
        return "app_flow_failure"
    return "correctness_failure"


def sanitize(path: Path, variant: str) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        missing = RAW_REQUIRED - set(reader.fieldnames or ())
        if missing:
            raise ValueError(f"{path}: missing internal columns: {', '.join(sorted(missing))}")
        rows = []
        for line_number, raw in enumerate(reader, start=2):
            try:
                if parse_boolean(raw["is_warmup"], "is_warmup"):
                    continue
                if not parse_boolean(raw["infrastructure_valid"], "infrastructure_valid"):
                    continue
                rows.append(
                    {
                        "run_index": raw["iteration"],
                        "variant": variant,
                        "scenario": raw["scenario"],
                        "condition": raw["condition"],
                        "phase": raw["phase"],
                        "outcome": raw["outcome"],
                        "flow_success": normalized_boolean(raw["flow_success"]),
                        "correctness_pass": normalized_boolean(raw["correctness_pass"]),
                        "false_success": normalized_boolean(raw["false_success"]),
                        "catalog_version_match": normalized_boolean(
                            raw["catalog_version_match"],
                        ),
                        "filter_result_match": normalized_boolean(
                            raw["filter_result_match"],
                        ),
                        "full_catalog_match": normalized_boolean(raw["full_catalog_match"]),
                        "stale_age_ms": raw["stale_age_ms"],
                        "failure_category": failure_category(raw),
                        "state_mismatch_count": raw["state_mismatch_count"],
                        "time_to_first_usable_ms": raw["time_to_first_usable_ms"],
                        "time_to_expected_state_ms": raw["time_to_expected_state_ms"],
                        "request_count": raw["request_count"],
                        "duplicate_request_count": raw["duplicate_request_count"],
                        "response_body_bytes": raw["response_body_bytes"],
                        "blocking_error_count": raw["blocking_error_count"],
                    },
                )
            except ValueError as error:
                raise ValueError(f"{path}:{line_number}: {error}") from error
    return rows


def validate_matrix(rows: list[dict[str, str]], expected_attempts: int) -> None:
    groups: dict[tuple[str, str, str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        groups[(row["variant"], row["scenario"], row["condition"], row["phase"])].append(row)

    expected = {
        (variant, scenario, condition, phase)
        for variant in ("baseline", "after")
        for scenario, condition, phase in EXPECTED_GROUPS
    }
    if set(groups) != expected:
        missing = sorted(expected - set(groups))
        unexpected = sorted(set(groups) - expected)
        raise ValueError(f"route measurement groups differ: missing={missing}, unexpected={unexpected}")

    for key, group_rows in sorted(groups.items()):
        if len(group_rows) != expected_attempts:
            raise ValueError(f"{key} has {len(group_rows)} valid attempts; expected {expected_attempts}")
        indices = [row["run_index"] for row in group_rows]
        if len(indices) != len(set(indices)):
            raise ValueError(f"{key} contains duplicate run_index values")
        for row in group_rows:
            if row["failure_category"] and row["failure_category"] not in FAILURE_CATEGORIES:
                raise ValueError(f"unsupported public failure category: {row['failure_category']}")
            route_group = (row["scenario"], row["condition"], row["phase"])
            if (
                row["variant"] == "after"
                and route_group in STALE_GROUPS
                and row["correctness_pass"] == "true"
                and not row["stale_age_ms"]
            ):
                raise ValueError(f"{key} successful attempts require stale_age_ms")


def assert_public(rows: list[dict[str, str]]) -> None:
    serialized = "\n".join(",".join(row[column] for column in V3_COLUMNS) for row in rows)
    for marker in PRIVATE_MARKERS:
        if marker.lower() in serialized.lower():
            raise ValueError(f"public result contains private marker: {marker}")


def publish(
    baseline_paths: list[Path],
    after_paths: list[Path],
    output_dir: Path,
    expected_attempts: int,
) -> tuple[Path, Path]:
    rows = []
    for path in baseline_paths:
        rows.extend(sanitize(path, "baseline"))
    for path in after_paths:
        rows.extend(sanitize(path, "after"))
    validate_matrix(rows, expected_attempts)
    assert_public(rows)

    output_dir.mkdir(parents=True, exist_ok=True)
    output_csv = output_dir / "route-cache-runs.csv"
    with output_csv.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=V3_COLUMNS)
        writer.writeheader()
        writer.writerows(sorted(
            rows,
            key=lambda row: (
                row["variant"], row["scenario"], row["condition"], row["phase"], int(row["run_index"]),
            ),
        ))
    summary = build_result([output_csv])
    summary["measurementMatrix"] = {
        "attemptsPerVariantCondition": expected_attempts,
        "groupCount": len(EXPECTED_GROUPS),
        "rowCount": len(rows),
        "failureCategoryCounts": dict(sorted(Counter(
            row["failure_category"] for row in rows if row["failure_category"]
        ).items())),
    }
    output_json = output_dir / "route-cache-summary.json"
    output_json.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return output_csv, output_json


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, nargs="+", required=True, help="baseline private run.csv files")
    parser.add_argument("--after", type=Path, nargs="+", required=True, help="after private run.csv files")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--expected-attempts", type=int, default=50)
    args = parser.parse_args()
    if args.expected_attempts < 1:
        parser.error("--expected-attempts must be positive")
    publish(args.baseline, args.after, args.output_dir, args.expected_attempts)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print(f"publish failed: {error}", file=sys.stderr)
        raise SystemExit(1)
