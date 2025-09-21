#!/usr/bin/env python3
"""
Performance Comparison Script for Pose Coach Android CI/CD Pipeline
Compares current build performance against baseline metrics
"""

import argparse
import json
import sys
import logging
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from google.cloud import storage
import requests
import statistics

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class PerformanceMetric:
    """Performance metric data structure"""
    name: str
    value: float
    unit: str
    threshold: float
    category: str

@dataclass
class PerformanceResult:
    """Performance comparison result"""
    metric: PerformanceMetric
    baseline_value: float
    current_value: float
    change_percent: float
    is_regression: bool
    severity: str

class PerformanceComparator:
    """Compares performance metrics between current and baseline builds"""

    def __init__(self, threshold_percent: float = 10.0):
        self.threshold_percent = threshold_percent
        self.storage_client = storage.Client()

    def download_results(self, gcs_path: str) -> Dict:
        """Download performance results from Google Cloud Storage"""
        try:
            bucket_name = gcs_path.replace('gs://', '').split('/')[0]
            blob_path = '/'.join(gcs_path.replace('gs://', '').split('/')[1:])

            bucket = self.storage_client.bucket(bucket_name)
            blob = bucket.blob(f"{blob_path}/performance-metrics.json")

            if not blob.exists():
                logger.warning(f"Performance metrics not found at {gcs_path}")
                return {}

            content = blob.download_as_text()
            return json.loads(content)

        except Exception as e:
            logger.error(f"Failed to download results from {gcs_path}: {e}")
            return {}

    def parse_metrics(self, results: Dict) -> List[PerformanceMetric]:
        """Parse performance metrics from Firebase Test Lab results"""
        metrics = []

        # App startup time
        if 'app_startup' in results:
            startup_data = results['app_startup']
            metrics.append(PerformanceMetric(
                name="app_startup_time",
                value=startup_data.get('cold_start_ms', 0),
                unit="ms",
                threshold=3000,  # 3 seconds max
                category="startup"
            ))

        # Memory usage
        if 'memory' in results:
            memory_data = results['memory']
            metrics.append(PerformanceMetric(
                name="peak_memory_usage",
                value=memory_data.get('peak_mb', 0),
                unit="MB",
                threshold=500,  # 500MB max
                category="memory"
            ))

        # Frame rate and rendering
        if 'graphics' in results:
            graphics_data = results['graphics']
            metrics.append(PerformanceMetric(
                name="average_fps",
                value=graphics_data.get('avg_fps', 0),
                unit="fps",
                threshold=30,  # Minimum 30 FPS
                category="rendering"
            ))

        # AI model inference time
        if 'ai_inference' in results:
            ai_data = results['ai_inference']
            metrics.append(PerformanceMetric(
                name="pose_detection_latency",
                value=ai_data.get('avg_latency_ms', 0),
                unit="ms",
                threshold=200,  # 200ms max for real-time performance
                category="ai"
            ))

        # Battery usage
        if 'battery' in results:
            battery_data = results['battery']
            metrics.append(PerformanceMetric(
                name="battery_drain_rate",
                value=battery_data.get('drain_percent_per_hour', 0),
                unit="%/hour",
                threshold=15,  # 15% per hour max
                category="battery"
            ))

        # Network performance
        if 'network' in results:
            network_data = results['network']
            metrics.append(PerformanceMetric(
                name="api_response_time",
                value=network_data.get('avg_response_ms', 0),
                unit="ms",
                threshold=1000,  # 1 second max
                category="network"
            ))

        return metrics

    def compare_metrics(self, current_metrics: List[PerformanceMetric],
                       baseline_metrics: List[PerformanceMetric]) -> List[PerformanceResult]:
        """Compare current metrics with baseline"""
        results = []
        baseline_dict = {m.name: m for m in baseline_metrics}

        for current_metric in current_metrics:
            baseline_metric = baseline_dict.get(current_metric.name)
            if not baseline_metric:
                logger.warning(f"No baseline found for metric: {current_metric.name}")
                continue

            change_percent = ((current_metric.value - baseline_metric.value) /
                            baseline_metric.value * 100)

            # Determine if this is a regression
            is_regression = self._is_regression(current_metric, baseline_metric, change_percent)

            # Determine severity
            severity = self._get_severity(current_metric, change_percent)

            results.append(PerformanceResult(
                metric=current_metric,
                baseline_value=baseline_metric.value,
                current_value=current_metric.value,
                change_percent=change_percent,
                is_regression=is_regression,
                severity=severity
            ))

        return results

    def _is_regression(self, current: PerformanceMetric, baseline: PerformanceMetric,
                      change_percent: float) -> bool:
        """Determine if the change represents a performance regression"""
        # For metrics where lower is better (latency, memory, battery)
        lower_is_better = current.category in ['startup', 'memory', 'ai', 'battery', 'network']

        if lower_is_better:
            return change_percent > self.threshold_percent
        else:
            # For metrics where higher is better (FPS)
            return change_percent < -self.threshold_percent

    def _get_severity(self, metric: PerformanceMetric, change_percent: float) -> str:
        """Determine the severity of performance change"""
        abs_change = abs(change_percent)

        if abs_change > 50:
            return "critical"
        elif abs_change > 25:
            return "major"
        elif abs_change > 10:
            return "minor"
        else:
            return "negligible"

    def generate_report(self, results: List[PerformanceResult], output_path: str):
        """Generate comprehensive performance comparison report"""
        report = {
            "summary": {
                "total_metrics": len(results),
                "regressions": len([r for r in results if r.is_regression]),
                "improvements": len([r for r in results if not r.is_regression and r.change_percent < 0]),
                "stable": len([r for r in results if abs(r.change_percent) <= self.threshold_percent])
            },
            "metrics": []
        }

        for result in results:
            report["metrics"].append({
                "name": result.metric.name,
                "category": result.metric.category,
                "current_value": result.current_value,
                "baseline_value": result.baseline_value,
                "change_percent": round(result.change_percent, 2),
                "is_regression": result.is_regression,
                "severity": result.severity,
                "unit": result.metric.unit,
                "threshold": result.metric.threshold
            })

        # Sort by severity and regression status
        report["metrics"].sort(key=lambda x: (
            not x["is_regression"],  # Regressions first
            {"critical": 0, "major": 1, "minor": 2, "negligible": 3}[x["severity"]]
        ))

        # Write report
        with open(output_path, 'w') as f:
            json.dump(report, f, indent=2)

        # Print summary
        self._print_summary(report)

        return report

    def _print_summary(self, report: Dict):
        """Print performance comparison summary"""
        summary = report["summary"]

        print("\n🔍 Performance Comparison Summary")
        print("=" * 50)
        print(f"📊 Total Metrics Analyzed: {summary['total_metrics']}")
        print(f"❌ Performance Regressions: {summary['regressions']}")
        print(f"✅ Performance Improvements: {summary['improvements']}")
        print(f"🔄 Stable Metrics: {summary['stable']}")

        # Show critical regressions
        critical_regressions = [
            m for m in report["metrics"]
            if m["is_regression"] and m["severity"] == "critical"
        ]

        if critical_regressions:
            print(f"\n🚨 Critical Performance Regressions:")
            for metric in critical_regressions:
                print(f"  • {metric['name']}: {metric['change_percent']:+.1f}% "
                      f"({metric['baseline_value']} → {metric['current_value']} {metric['unit']})")

        # Show significant improvements
        improvements = [
            m for m in report["metrics"]
            if not m["is_regression"] and abs(m["change_percent"]) > 10
        ]

        if improvements:
            print(f"\n🎉 Significant Performance Improvements:")
            for metric in improvements[:5]:  # Show top 5
                print(f"  • {metric['name']}: {metric['change_percent']:+.1f}% "
                      f"({metric['baseline_value']} → {metric['current_value']} {metric['unit']})")

def main():
    parser = argparse.ArgumentParser(description='Compare performance metrics against baseline')
    parser.add_argument('--current-results', required=True,
                       help='GCS path to current performance results')
    parser.add_argument('--baseline-results', required=True,
                       help='GCS path to baseline performance results')
    parser.add_argument('--threshold', type=float, default=10.0,
                       help='Performance regression threshold percentage (default: 10%)')
    parser.add_argument('--output', default='performance-comparison.json',
                       help='Output report file path')
    parser.add_argument('--fail-on-regression', action='store_true',
                       help='Exit with non-zero code if regressions are found')

    args = parser.parse_args()

    comparator = PerformanceComparator(threshold_percent=args.threshold)

    # Download and parse results
    logger.info("Downloading current performance results...")
    current_data = comparator.download_results(args.current_results)

    logger.info("Downloading baseline performance results...")
    baseline_data = comparator.download_results(args.baseline_results)

    if not current_data or not baseline_data:
        logger.error("Failed to download performance data")
        sys.exit(1)

    # Parse metrics
    current_metrics = comparator.parse_metrics(current_data)
    baseline_metrics = comparator.parse_metrics(baseline_data)

    if not current_metrics or not baseline_metrics:
        logger.error("No valid performance metrics found")
        sys.exit(1)

    # Compare metrics
    logger.info("Comparing performance metrics...")
    results = comparator.compare_metrics(current_metrics, baseline_metrics)

    # Generate report
    report = comparator.generate_report(results, args.output)

    # Check for regressions
    if args.fail_on_regression and report["summary"]["regressions"] > 0:
        logger.error(f"Performance regressions detected: {report['summary']['regressions']}")
        sys.exit(1)

    logger.info(f"Performance comparison completed. Report saved to {args.output}")

if __name__ == "__main__":
    main()