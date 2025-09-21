#!/usr/bin/env python3
"""
Performance Benchmark Processing Script

This script processes benchmark results from Android instrumentation tests
and generates performance reports with regression detection.
"""

import json
import os
import sys
import glob
import statistics
from datetime import datetime
from typing import Dict, List, Tuple, Optional


class BenchmarkProcessor:
    """Processes and analyzes benchmark results"""

    def __init__(self):
        self.baseline_file = "performance_baselines.json"
        self.results = {}
        self.regressions = []
        self.improvements = []

    def load_baseline(self) -> Dict:
        """Load baseline performance metrics"""
        if os.path.exists(self.baseline_file):
            with open(self.baseline_file, 'r') as f:
                return json.load(f)
        return {
            "pose_detection_median_ns": 25000000,  # 25ms
            "ui_frame_time_median_ns": 12000000,   # 12ms
            "memory_peak_mb": 180,
            "apk_size_mb": 50,
            "api_response_time_ms": 800,
            "websocket_latency_ms": 45
        }

    def find_benchmark_files(self) -> List[str]:
        """Find all benchmark result files"""
        patterns = [
            "**/build/outputs/connected_android_test_additional_output/**/benchmarks.json",
            "**/benchmark-results/*.json",
            "benchmark-*.json"
        ]

        files = []
        for pattern in patterns:
            files.extend(glob.glob(pattern, recursive=True))

        return files

    def parse_benchmark_file(self, file_path: str) -> Dict:
        """Parse a single benchmark file"""
        try:
            with open(file_path, 'r') as f:
                data = json.load(f)

            metrics = {}

            # Process Android Benchmark Library format
            if 'benchmarks' in data:
                for benchmark in data['benchmarks']:
                    name = benchmark.get('name', '')
                    if 'metrics' in benchmark:
                        for metric_name, metric_data in benchmark['metrics'].items():
                            key = f"{name}_{metric_name}"
                            if isinstance(metric_data, dict) and 'median' in metric_data:
                                metrics[key] = metric_data['median']
                            elif isinstance(metric_data, (int, float)):
                                metrics[key] = metric_data

            # Process custom format
            elif 'results' in data:
                for result in data['results']:
                    metrics.update(result)

            return metrics

        except Exception as e:
            print(f"Error parsing {file_path}: {e}")
            return {}

    def process_all_benchmarks(self) -> Dict:
        """Process all benchmark files and aggregate results"""
        benchmark_files = self.find_benchmark_files()

        if not benchmark_files:
            print("⚠️ No benchmark files found")
            return {}

        print(f"📊 Processing {len(benchmark_files)} benchmark files...")

        all_metrics = {}
        for file_path in benchmark_files:
            metrics = self.parse_benchmark_file(file_path)
            for key, value in metrics.items():
                if key not in all_metrics:
                    all_metrics[key] = []
                all_metrics[key].append(value)

        # Aggregate metrics (use median)
        aggregated = {}
        for key, values in all_metrics.items():
            if values:
                aggregated[key] = statistics.median(values)

        return aggregated

    def detect_regressions(self, current_metrics: Dict, baseline: Dict) -> Tuple[List, List]:
        """Detect performance regressions and improvements"""
        regressions = []
        improvements = []

        regression_threshold = 1.05  # 5% increase is a regression
        improvement_threshold = 0.95  # 5% decrease is an improvement

        metric_mappings = {
            'poseDetection_timeNs': 'pose_detection_median_ns',
            'uiRendering_timeNs': 'ui_frame_time_median_ns',
            'memoryUsage_bytes': 'memory_peak_mb',
            'apiResponse_timeMs': 'api_response_time_ms',
            'websocket_latencyMs': 'websocket_latency_ms'
        }

        for current_key, baseline_key in metric_mappings.items():
            if current_key in current_metrics and baseline_key in baseline:
                current_value = current_metrics[current_key]
                baseline_value = baseline[baseline_key]

                # Convert bytes to MB for memory metrics
                if 'bytes' in current_key:
                    current_value = current_value / (1024 * 1024)

                ratio = current_value / baseline_value

                if ratio >= regression_threshold:
                    regressions.append({
                        'metric': current_key,
                        'current': current_value,
                        'baseline': baseline_value,
                        'ratio': ratio,
                        'percentage_change': (ratio - 1) * 100
                    })
                elif ratio <= improvement_threshold:
                    improvements.append({
                        'metric': current_key,
                        'current': current_value,
                        'baseline': baseline_value,
                        'ratio': ratio,
                        'percentage_change': (1 - ratio) * 100
                    })

        return regressions, improvements

    def generate_report(self, current_metrics: Dict, baseline: Dict,
                       regressions: List, improvements: List) -> str:
        """Generate a human-readable performance report"""
        report = []
        report.append("# 📊 Performance Benchmark Report")
        report.append(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        report.append("")

        # Summary
        if regressions:
            report.append("## ❌ Regressions Detected")
            for reg in regressions:
                report.append(f"- **{reg['metric']}**: {reg['current']:.2f} "
                             f"(+{reg['percentage_change']:.1f}% from baseline {reg['baseline']:.2f})")
            report.append("")

        if improvements:
            report.append("## ✅ Performance Improvements")
            for imp in improvements:
                report.append(f"- **{imp['metric']}**: {imp['current']:.2f} "
                             f"(-{imp['percentage_change']:.1f}% from baseline {imp['baseline']:.2f})")
            report.append("")

        # Detailed metrics
        report.append("## 📈 Detailed Metrics")
        report.append("| Metric | Current | Baseline | Status |")
        report.append("|--------|---------|----------|--------|")

        for metric, current_value in current_metrics.items():
            status = "✅ OK"
            baseline_value = baseline.get(metric, "N/A")

            if isinstance(baseline_value, (int, float)):
                ratio = current_value / baseline_value
                if ratio >= 1.05:
                    status = "❌ Regression"
                elif ratio <= 0.95:
                    status = "⬆️ Improvement"

            report.append(f"| {metric} | {current_value:.2f} | {baseline_value} | {status} |")

        return "\n".join(report)

    def save_results(self, results: Dict):
        """Save benchmark comparison results"""
        output_file = "benchmark-comparison.json"
        with open(output_file, 'w') as f:
            json.dump(results, f, indent=2)
        print(f"📁 Results saved to {output_file}")

    def check_critical_thresholds(self, current_metrics: Dict) -> bool:
        """Check if any critical performance thresholds are exceeded"""
        critical_failures = []

        thresholds = {
            'poseDetection_timeNs': 50000000,  # 50ms max (20 FPS minimum)
            'uiRendering_timeNs': 16666666,    # 16.67ms max (60 FPS)
            'memoryUsage_bytes': 512 * 1024 * 1024,  # 512MB max
            'apiResponse_timeMs': 5000,         # 5 seconds max
        }

        for metric, threshold in thresholds.items():
            if metric in current_metrics:
                current_value = current_metrics[metric]
                if current_value > threshold:
                    critical_failures.append(f"{metric}: {current_value} > {threshold}")

        if critical_failures:
            print("🚨 CRITICAL PERFORMANCE FAILURES:")
            for failure in critical_failures:
                print(f"  - {failure}")
            return False

        return True

    def run(self) -> bool:
        """Main processing function"""
        print("🚀 Starting benchmark processing...")

        # Load baseline metrics
        baseline = self.load_baseline()
        print(f"📋 Loaded baseline metrics: {len(baseline)} metrics")

        # Process current benchmark results
        current_metrics = self.process_all_benchmarks()
        if not current_metrics:
            print("❌ No benchmark data to process")
            return False

        print(f"📊 Processed current metrics: {len(current_metrics)} metrics")

        # Detect regressions and improvements
        regressions, improvements = self.detect_regressions(current_metrics, baseline)

        # Check critical thresholds
        critical_ok = self.check_critical_thresholds(current_metrics)

        # Generate report
        report = self.generate_report(current_metrics, baseline, regressions, improvements)
        print(report)

        # Save results
        results = {
            'timestamp': datetime.now().isoformat(),
            'current_metrics': current_metrics,
            'baseline_metrics': baseline,
            'regressions': regressions,
            'improvements': improvements,
            'critical_threshold_passed': critical_ok
        }
        self.save_results(results)

        # Determine overall success
        has_regressions = len(regressions) > 0
        success = critical_ok and not has_regressions

        if success:
            print("✅ All performance benchmarks passed!")
        else:
            if not critical_ok:
                print("❌ Critical performance thresholds exceeded")
            if has_regressions:
                print(f"❌ {len(regressions)} performance regressions detected")

        return success


def main():
    """Main entry point"""
    processor = BenchmarkProcessor()
    success = processor.run()
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()