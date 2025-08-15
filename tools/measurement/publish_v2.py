#!/usr/bin/env python3
"""Publish sanitized v2 CSV/JSON from a private internal run CSV."""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

from aggregate_evidence import V2_COLUMNS, build_result


RAW_REQUIRED = {
    "iteration", "is_warmup", "infrastructure_valid", "scenario", "condition", "phase",
    "outcome", "flow_success", "correctness_pass", "false_success", "failure_reason",
    "state_mismatch_count", "time_to_first_usable_ms", "time_to_expected_state_ms",
    "blocking_loading_ms", "finish_to_actionable_ms", "reconnect_to_sync_ms", "request_count",
    "duplicate_request_count", "response_body_bytes", "automatic_retry_count", "manual_retry_count",
    "blocking_error_count", "local_record_count", "server_record_count", "duplicate_record_count",
}
PRIVATE_MARKERS = ("serial", "fingerprint", "/Users/", "Perfetto", "digest", "trace")


def optional(row: dict[str, str], name: str) -> str:
    return row.get(name, "")


def sanitize(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        missing = RAW_REQUIRED - set(reader.fieldnames or ())
        if missing:
            raise ValueError(f"{path}: missing internal columns: {', '.join(sorted(missing))}")
        result: list[dict[str, str]] = []
        for raw in reader:
            if raw["is_warmup"].strip().lower() == "true":
                continue
            if raw["infrastructure_valid"].strip().lower() != "true":
                continue
            local_count = int(raw["local_record_count"] or 0)
            server_count = int(raw["server_record_count"] or 0)
            phase = raw["phase"]
            result.append({
                "run_index": raw["iteration"],
                "scenario": raw["scenario"],
                "condition": raw["condition"],
                "phase": phase,
                "outcome": raw["outcome"],
                "flow_success": raw["flow_success"],
                "correctness_pass": raw["correctness_pass"],
                "false_success": raw["false_success"],
                "failure_category": raw["failure_reason"],
                "state_mismatch_count": raw["state_mismatch_count"],
                "time_to_first_usable_ms": raw["time_to_first_usable_ms"],
                "time_to_expected_state_ms": raw["time_to_expected_state_ms"],
                "blocking_loading_ms": raw["blocking_loading_ms"],
                "finish_to_actionable_ms": raw["finish_to_actionable_ms"],
                "reconnect_to_server_record_ms": raw["reconnect_to_sync_ms"] if phase == "reconnect" else "",
                "request_count": raw["request_count"],
                "duplicate_request_count": raw["duplicate_request_count"],
                "response_body_bytes": raw["response_body_bytes"],
                "automatic_retry_count": raw["automatic_retry_count"],
                "manual_retry_count": raw["manual_retry_count"],
                "blocking_error_count": raw["blocking_error_count"],
                "local_record_loss_count": str(max(0, 1 - max(local_count, server_count))) if raw["scenario"] == "S3" else "0",
                "server_duplicate_count": raw["duplicate_record_count"],
            })
    return result


def assert_public(rows: list[dict[str, str]]) -> None:
    serialized = "\n".join(",".join(row[column] for column in V2_COLUMNS) for row in rows)
    for marker in PRIVATE_MARKERS:
        if marker.lower() in serialized.lower():
            raise ValueError(f"public result contains private marker: {marker}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("raw_run_csv", type=Path, nargs="+")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    rows = []
    for raw_run_csv in args.raw_run_csv:
        rows.extend(sanitize(raw_run_csv))
    assert_public(rows)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output_csv = args.output_dir / "baseline-runs.csv"
    with output_csv.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=V2_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    result = build_result([output_csv])
    (args.output_dir / "baseline-summary.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print(f"publish failed: {error}", file=sys.stderr)
        raise SystemExit(1)
