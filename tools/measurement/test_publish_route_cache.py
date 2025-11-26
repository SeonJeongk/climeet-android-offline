import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from publish_route_cache import EXPECTED_GROUPS, RAW_REQUIRED, STALE_GROUPS, failure_category, publish, sanitize


class PublishRouteCacheTest(unittest.TestCase):
    def test_unobserved_app_failure_does_not_become_catalog_mismatch(self):
        raw = {
            "correctness_pass": "false", "flow_success": "false", "false_success": "false",
            "catalog_version_match": "", "filter_result_match": "", "full_catalog_match": "",
        }
        self.assertEqual("app_flow_failure", failure_category(raw))

    def test_sanitization_preserves_unobserved_accuracy_and_removes_private_failure_text(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "failure.csv"
            raw = dict.fromkeys(RAW_REQUIRED, "0")
            raw.update({
                "iteration": "1", "is_warmup": "false", "infrastructure_valid": "true",
                "scenario": "ROUTE_OFFLINE", "condition": "offline", "phase": "single",
                "outcome": "blocking_error", "flow_success": "false", "correctness_pass": "false",
                "false_success": "false", "catalog_version_match": "", "filter_result_match": "",
                "full_catalog_match": "", "failure_reason": "/Users/private/serial-private-trace.log",
            })
            with path.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=tuple(raw))
                writer.writeheader()
                writer.writerow(raw)
            row = sanitize(path, "baseline")[0]
            self.assertEqual("", row["catalog_version_match"])
            self.assertEqual("", row["filter_result_match"])
            self.assertEqual("app_flow_failure", row["failure_category"])
            self.assertNotIn("/Users/", json.dumps(row))

    def test_publish_requires_complete_matrix_and_keeps_accuracy_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = root / "baseline.csv"
            after = root / "after.csv"
            self.write_raw(baseline, "baseline")
            self.write_raw(after, "after")

            output_csv, output_json = publish([baseline], [after], root / "public", 1)

            with output_csv.open(newline="", encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream))
            summary = json.loads(output_json.read_text(encoding="utf-8"))
            self.assertEqual(4, len(rows))
            self.assertEqual("", next(row for row in rows if row["variant"] == "baseline")["full_catalog_match"])
            self.assertTrue(all(row["full_catalog_match"] == "true" for row in rows if row["variant"] == "after"))
            self.assertEqual(3, summary["schemaVersion"])
            self.assertEqual(4, summary["measurementMatrix"]["rowCount"])

    def test_unobserved_duplicate_requests_stay_empty_instead_of_zero(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = root / "baseline.csv"
            after = root / "after.csv"
            self.write_raw(baseline, "baseline")
            self.write_raw(after, "after")

            output_csv, output_json = publish([baseline], [after], root / "public", 1)

            with output_csv.open(newline="", encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream))
            summary = json.loads(output_json.read_text(encoding="utf-8"))
            self.assertTrue(all(row["duplicate_request_count"] == "" for row in rows))
            self.assertTrue(all(
                group["countTotals"]["duplicate_request_count"] is None for group in summary["groups"]
            ))
            self.assertTrue(all(group["countTotals"]["request_count"] == 1 for group in summary["groups"]))

    def test_successful_after_stale_group_requires_stale_age(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = root / "baseline.csv"
            after = root / "after.csv"
            self.write_raw(baseline, "baseline")
            self.write_raw(after, "after", omit_stale_age=True)

            with self.assertRaisesRegex(ValueError, "require stale_age_ms"):
                publish([baseline], [after], root / "public", 1)

    @staticmethod
    def write_raw(path: Path, variant: str, omit_stale_age: bool = False) -> None:
        fieldnames = sorted(RAW_REQUIRED)
        with path.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=fieldnames)
            writer.writeheader()
            for scenario, condition, phase in sorted(EXPECTED_GROUPS):
                stale = (scenario, condition, phase) in STALE_GROUPS
                writer.writerow({
                    "iteration": "0",
                    "is_warmup": "false",
                    "infrastructure_valid": "true",
                    "scenario": scenario,
                    "condition": condition,
                    "phase": phase,
                    "outcome": "success",
                    "flow_success": "true",
                    "correctness_pass": "true",
                    "false_success": "false",
                    "state_mismatch_count": "0",
                    "catalog_version_match": "true",
                    "filter_result_match": "true",
                    "full_catalog_match": "" if variant == "baseline" else "true",
                    "stale_age_ms": "" if omit_stale_age or not stale or variant == "baseline" else "901000",
                    "time_to_first_usable_ms": "50",
                    "time_to_expected_state_ms": "900",
                    "request_count": "1",
                    "duplicate_request_count": "",
                    "response_body_bytes": "200",
                    "blocking_error_count": "0",
                })


if __name__ == "__main__":
    unittest.main()
