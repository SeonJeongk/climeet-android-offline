import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from aggregate_evidence import COLUMNS, build_result, nearest_rank


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


if __name__ == "__main__":
    unittest.main()
