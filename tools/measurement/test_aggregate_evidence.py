import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from aggregate_evidence import COLUMNS, V2_COLUMNS, V3_COLUMNS, build_result, nearest_rank


class AggregateEvidenceTest(unittest.TestCase):
    def test_nearest_rank_percentiles_are_deterministic(self):
        values = list(range(1, 101))
        self.assertEqual(50, nearest_rank(values, 50))
        self.assertEqual(90, nearest_rank(values, 90))
        self.assertEqual(95, nearest_rank(values, 95))

    def test_invalid_samples_are_counted_but_excluded_from_percentiles(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "samples.csv"
            with path.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=COLUMNS)
                writer.writeheader()
                writer.writerow(self.row(1, "true", "", 100, "success"))
                writer.writerow(self.row(2, "true", "", 300, "failure"))
                writer.writerow(self.row(3, "false", "harness-timeout", 999, "unknown"))

            group = build_result([path])["groups"][0]

            self.assertEqual(2, group["validSampleCount"])
            self.assertEqual(1, group["excludedSampleCount"])
            self.assertEqual(300, group["metrics"]["durationMs"]["p90"])
            self.assertEqual(0.5, group["successRate"])

    def test_v2_keeps_failures_in_denominator_and_partitions_timing_by_outcome(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "samples.csv"
            with path.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=V2_COLUMNS)
                writer.writeheader()
                writer.writerow(self.v2_row(1, "success", "true", "true", "", 100))
                writer.writerow(self.v2_row(2, "blocking_error", "false", "false", "offline", ""))
                writer.writerow(self.v2_row(3, "false_content", "true", "false", "state_mismatch", 1))

            result = build_result([path])
            group = result["groups"][0]

            self.assertEqual(2, result["schemaVersion"])
            self.assertEqual(3, group["validAppAttemptCount"])
            self.assertEqual(0.666667, group["successRate"])
            self.assertEqual(1, group["correctnessPassCount"])
            self.assertEqual(100, group["metricsByOutcome"]["success"]["time_to_expected_state_ms"]["p95"])
            self.assertIsNone(group["metricsByOutcome"]["blocking_error"]["time_to_expected_state_ms"]["p95"])
            self.assertIsNone(group["metricsByOutcome"]["false_content"]["time_to_expected_state_ms"]["p95"])

    def test_v2_requires_both_s3_phases_for_each_iteration(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "samples.csv"
            with path.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=V2_COLUMNS)
                writer.writeheader()
                row = self.v2_row(1, "preserved_draft", "false", "true", "offline", 200)
                row["scenario"] = "S3"
                row["phase"] = "offline"
                writer.writerow(row)

            with self.assertRaisesRegex(ValueError, "one offline and one reconnect"):
                build_result([path])

    def test_v3_preserves_route_accuracy_and_stale_age_by_variant(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "route-cache.csv"
            with path.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=V3_COLUMNS)
                writer.writeheader()
                writer.writerow(self.v3_row("baseline", full_catalog="", stale_age=""))
                writer.writerow(self.v3_row("after", full_catalog="true", stale_age="901000"))

            result = build_result([path])
            groups = {group["variant"]: group for group in result["groups"]}

            self.assertEqual(3, result["schemaVersion"])
            self.assertIsNone(groups["baseline"]["accuracy"]["fullCatalogMatchRate"])
            self.assertEqual(1.0, groups["after"]["accuracy"]["fullCatalogMatchRate"])
            self.assertEqual(901000, groups["after"]["metrics"]["stale_age_ms"]["p95"])

    @staticmethod
    def row(index, valid, reason, duration, outcome):
        return {
            "runIndex": index,
            "scenarioId": "S1",
            "variant": "cache-enabled",
            "valid": valid,
            "invalidReason": reason,
            "durationMs": duration,
            "requestCount": 2,
            "requestBodyBytes": 10,
            "responseBodyBytes": 20,
            "outcome": outcome,
        }

    @staticmethod
    def v2_row(index, outcome, flow_success, correctness_pass, failure_category, duration):
        return {
            "run_index": index,
            "scenario": "S1",
            "condition": "clean",
            "phase": "single",
            "outcome": outcome,
            "flow_success": flow_success,
            "correctness_pass": correctness_pass,
            "false_success": "false",
            "failure_category": failure_category,
            "state_mismatch_count": 0 if correctness_pass == "true" else 1,
            "time_to_first_usable_ms": duration,
            "time_to_expected_state_ms": duration,
            "blocking_loading_ms": "",
            "finish_to_actionable_ms": "",
            "reconnect_to_server_record_ms": "",
            "request_count": 2,
            "duplicate_request_count": 0,
            "response_body_bytes": 20,
            "automatic_retry_count": 0,
            "manual_retry_count": 0,
            "blocking_error_count": 1 if outcome == "blocking_error" else 0,
            "local_record_loss_count": 0,
            "server_duplicate_count": 0,
        }

    @staticmethod
    def v3_row(variant, full_catalog, stale_age):
        return {
            "run_index": 0,
            "variant": variant,
            "scenario": "ROUTE_REENTRY",
            "condition": "latency_3000",
            "phase": "single",
            "outcome": "success",
            "flow_success": "true",
            "correctness_pass": "true",
            "false_success": "false",
            "catalog_version_match": "true",
            "filter_result_match": "true",
            "full_catalog_match": full_catalog,
            "stale_age_ms": stale_age,
            "failure_category": "",
            "state_mismatch_count": 0,
            "time_to_first_usable_ms": 50,
            "time_to_expected_state_ms": 900,
            "request_count": 1,
            "duplicate_request_count": 0,
            "response_body_bytes": 200,
            "blocking_error_count": 0,
        }


if __name__ == "__main__":
    unittest.main()
