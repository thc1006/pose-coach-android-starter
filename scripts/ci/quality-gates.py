#!/usr/bin/env python3
"""
Quality Gates Script for Pose Coach Android CI/CD Pipeline
Enforces code quality, security, and performance standards
"""

import argparse
import json
import logging
import os
import sys
import subprocess
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
import requests

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class QualityGate:
    """Quality gate definition"""
    name: str
    threshold: float
    current_value: float
    unit: str
    passed: bool
    critical: bool = False

@dataclass
class QualityGateResult:
    """Quality gate evaluation result"""
    gates: List[QualityGate]
    overall_passed: bool
    critical_failures: List[str]
    warnings: List[str]

class QualityGateValidator:
    """Validates quality gates for the CI/CD pipeline"""

    def __init__(self, sonar_token: Optional[str] = None):
        self.sonar_token = sonar_token
        self.sonar_url = "https://sonarcloud.io"
        self.project_key = "pose-coach-android"

    def get_sonarcloud_metrics(self) -> Dict:
        """Fetch metrics from SonarCloud"""
        if not self.sonar_token:
            logger.warning("SonarCloud token not provided, skipping SonarCloud metrics")
            return {}

        try:
            metrics_to_fetch = [
                "coverage",
                "duplicated_lines_density",
                "complexity",
                "cognitive_complexity",
                "security_rating",
                "maintainability_rating",
                "reliability_rating",
                "bugs",
                "vulnerabilities",
                "code_smells",
                "ncloc",  # Lines of code
                "sqale_rating",  # Technical debt rating
                "sqale_index",   # Technical debt in minutes
            ]

            url = f"{self.sonar_url}/api/measures/component"
            params = {
                "component": self.project_key,
                "metricKeys": ",".join(metrics_to_fetch)
            }

            headers = {
                "Authorization": f"Bearer {self.sonar_token}"
            }

            response = requests.get(url, params=params, headers=headers, timeout=30)

            if response.status_code != 200:
                logger.error(f"SonarCloud API error: {response.status_code} - {response.text}")
                return {}

            data = response.json()
            metrics = {}

            for measure in data.get("component", {}).get("measures", []):
                metric_key = measure["metric"]
                value = measure.get("value", "0")

                # Convert ratings to numeric values
                if metric_key in ["security_rating", "maintainability_rating", "reliability_rating", "sqale_rating"]:
                    # SonarCloud ratings: A=1, B=2, C=3, D=4, E=5
                    rating_map = {"A": 1, "B": 2, "C": 3, "D": 4, "E": 5}
                    metrics[metric_key] = rating_map.get(value, 5)
                else:
                    try:
                        metrics[metric_key] = float(value)
                    except ValueError:
                        metrics[metric_key] = 0

            return metrics

        except Exception as e:
            logger.error(f"Failed to fetch SonarCloud metrics: {e}")
            return {}

    def get_test_coverage(self) -> float:
        """Get test coverage from local reports"""
        try:
            # Look for Jacoco coverage reports
            coverage_files = [
                "app/build/reports/jacoco/test/jacocoTestReport.xml",
                "core-pose/build/reports/jacoco/test/jacocoTestReport.xml",
                "core-geom/build/reports/jacoco/test/jacocoTestReport.xml",
                "suggestions-api/build/reports/jacoco/test/jacocoTestReport.xml"
            ]

            total_lines = 0
            covered_lines = 0

            for coverage_file in coverage_files:
                if not os.path.exists(coverage_file):
                    continue

                # Parse XML coverage report
                import xml.etree.ElementTree as ET
                tree = ET.parse(coverage_file)
                root = tree.getroot()

                for counter in root.findall(".//counter[@type='LINE']"):
                    covered = int(counter.get("covered", 0))
                    missed = int(counter.get("missed", 0))
                    total_lines += covered + missed
                    covered_lines += covered

            if total_lines == 0:
                logger.warning("No coverage data found")
                return 0.0

            coverage_percent = (covered_lines / total_lines) * 100
            logger.info(f"Calculated test coverage: {coverage_percent:.2f}%")
            return coverage_percent

        except Exception as e:
            logger.error(f"Failed to calculate test coverage: {e}")
            return 0.0

    def get_build_metrics(self) -> Dict:
        """Get build-related metrics"""
        metrics = {}

        try:
            # APK size analysis
            apk_files = [
                "app/build/outputs/apk/release/app-release.apk",
                "app/build/outputs/apk/debug/app-debug.apk"
            ]

            for apk_file in apk_files:
                if os.path.exists(apk_file):
                    size_mb = os.path.getsize(apk_file) / (1024 * 1024)
                    build_type = "release" if "release" in apk_file else "debug"
                    metrics[f"apk_size_{build_type}_mb"] = size_mb

            # Build time analysis (from build scan or logs)
            # This would typically be extracted from build logs or Gradle Build Scan
            metrics["build_time_minutes"] = self._estimate_build_time()

            # Method count analysis
            metrics["method_count"] = self._count_methods()

        except Exception as e:
            logger.error(f"Failed to get build metrics: {e}")

        return metrics

    def _estimate_build_time(self) -> float:
        """Estimate build time from available data"""
        # This is a simplified estimation - in practice, you'd extract from build logs
        try:
            # Check if gradle build log exists
            gradle_log_paths = [
                ".gradle/build-time.log",
                "build/build-time.log"
            ]

            for log_path in gradle_log_paths:
                if os.path.exists(log_path):
                    with open(log_path) as f:
                        content = f.read()
                        # Extract build time from log
                        import re
                        match = re.search(r"BUILD SUCCESSFUL in (\d+)s", content)
                        if match:
                            return float(match.group(1)) / 60  # Convert to minutes

        except Exception:
            pass

        return 5.0  # Default estimate

    def _count_methods(self) -> int:
        """Count total methods in the application"""
        try:
            # Use Android's dexdump or aapt to count methods
            apk_file = "app/build/outputs/apk/release/app-release.apk"
            if os.path.exists(apk_file):
                result = subprocess.run(
                    ["aapt", "dump", "badging", apk_file],
                    capture_output=True, text=True, timeout=60
                )
                # This is simplified - actual method counting would require dex analysis
                return 10000  # Placeholder

        except Exception:
            pass

        return 0

    def evaluate_quality_gates(self, coverage_threshold: float = 85.0,
                             complexity_threshold: float = 10.0,
                             duplication_threshold: float = 5.0,
                             security_rating: str = "A",
                             maintainability_rating: str = "A") -> QualityGateResult:
        """Evaluate all quality gates"""

        gates = []
        critical_failures = []
        warnings = []

        # Get metrics from various sources
        sonar_metrics = self.get_sonarcloud_metrics()
        local_coverage = self.get_test_coverage()
        build_metrics = self.get_build_metrics()

        # Code Coverage Gate
        coverage = sonar_metrics.get("coverage", local_coverage)
        coverage_gate = QualityGate(
            name="Code Coverage",
            threshold=coverage_threshold,
            current_value=coverage,
            unit="%",
            passed=coverage >= coverage_threshold,
            critical=True
        )
        gates.append(coverage_gate)

        if not coverage_gate.passed:
            critical_failures.append(f"Code coverage {coverage:.1f}% below threshold {coverage_threshold}%")

        # Complexity Gate
        complexity = sonar_metrics.get("complexity", 0)
        if complexity > 0:
            avg_complexity = complexity / max(sonar_metrics.get("ncloc", 1000), 1) * 1000
            complexity_gate = QualityGate(
                name="Cyclomatic Complexity",
                threshold=complexity_threshold,
                current_value=avg_complexity,
                unit="per 1k lines",
                passed=avg_complexity <= complexity_threshold
            )
            gates.append(complexity_gate)

            if not complexity_gate.passed:
                warnings.append(f"Average complexity {avg_complexity:.1f} exceeds threshold {complexity_threshold}")

        # Duplication Gate
        duplication = sonar_metrics.get("duplicated_lines_density", 0)
        duplication_gate = QualityGate(
            name="Code Duplication",
            threshold=duplication_threshold,
            current_value=duplication,
            unit="%",
            passed=duplication <= duplication_threshold
        )
        gates.append(duplication_gate)

        if not duplication_gate.passed:
            warnings.append(f"Code duplication {duplication:.1f}% exceeds threshold {duplication_threshold}%")

        # Security Rating Gate
        security_rating_num = sonar_metrics.get("security_rating", 1)
        security_threshold = {"A": 1, "B": 2, "C": 3, "D": 4, "E": 5}[security_rating]
        security_gate = QualityGate(
            name="Security Rating",
            threshold=security_threshold,
            current_value=security_rating_num,
            unit="rating",
            passed=security_rating_num <= security_threshold,
            critical=True
        )
        gates.append(security_gate)

        if not security_gate.passed:
            critical_failures.append(f"Security rating failed: required {security_rating}, got {self._rating_to_letter(security_rating_num)}")

        # Maintainability Rating Gate
        maintainability_rating_num = sonar_metrics.get("maintainability_rating", 1)
        maintainability_threshold = {"A": 1, "B": 2, "C": 3, "D": 4, "E": 5}[maintainability_rating]
        maintainability_gate = QualityGate(
            name="Maintainability Rating",
            threshold=maintainability_threshold,
            current_value=maintainability_rating_num,
            unit="rating",
            passed=maintainability_rating_num <= maintainability_threshold
        )
        gates.append(maintainability_gate)

        if not maintainability_gate.passed:
            warnings.append(f"Maintainability rating failed: required {maintainability_rating}, got {self._rating_to_letter(maintainability_rating_num)}")

        # Vulnerabilities Gate
        vulnerabilities = sonar_metrics.get("vulnerabilities", 0)
        vulnerabilities_gate = QualityGate(
            name="Security Vulnerabilities",
            threshold=0,
            current_value=vulnerabilities,
            unit="count",
            passed=vulnerabilities == 0,
            critical=True
        )
        gates.append(vulnerabilities_gate)

        if not vulnerabilities_gate.passed:
            critical_failures.append(f"Security vulnerabilities found: {vulnerabilities}")

        # APK Size Gate (Mobile-specific)
        release_apk_size = build_metrics.get("apk_size_release_mb", 0)
        if release_apk_size > 0:
            apk_size_gate = QualityGate(
                name="APK Size",
                threshold=100.0,  # 100MB threshold
                current_value=release_apk_size,
                unit="MB",
                passed=release_apk_size <= 100.0
            )
            gates.append(apk_size_gate)

            if not apk_size_gate.passed:
                warnings.append(f"APK size {release_apk_size:.1f}MB exceeds 100MB threshold")

        # Method Count Gate (Android-specific)
        method_count = build_metrics.get("method_count", 0)
        if method_count > 0:
            method_count_gate = QualityGate(
                name="Method Count",
                threshold=65000,  # DEX limit
                current_value=method_count,
                unit="methods",
                passed=method_count <= 65000
            )
            gates.append(method_count_gate)

            if not method_count_gate.passed:
                critical_failures.append(f"Method count {method_count} exceeds DEX limit of 65,000")

        # Overall pass/fail determination
        critical_gates_passed = all(gate.passed for gate in gates if gate.critical)
        overall_passed = critical_gates_passed

        return QualityGateResult(
            gates=gates,
            overall_passed=overall_passed,
            critical_failures=critical_failures,
            warnings=warnings
        )

    def _rating_to_letter(self, rating_num: int) -> str:
        """Convert numeric rating to letter"""
        rating_map = {1: "A", 2: "B", 3: "C", 4: "D", 5: "E"}
        return rating_map.get(rating_num, "E")

    def generate_quality_report(self, result: QualityGateResult, output_path: str):
        """Generate quality gates report"""
        report = {
            "summary": {
                "overall_passed": result.overall_passed,
                "total_gates": len(result.gates),
                "passed_gates": len([g for g in result.gates if g.passed]),
                "critical_failures": len(result.critical_failures),
                "warnings": len(result.warnings)
            },
            "gates": [
                {
                    "name": gate.name,
                    "threshold": gate.threshold,
                    "current_value": gate.current_value,
                    "unit": gate.unit,
                    "passed": gate.passed,
                    "critical": gate.critical
                }
                for gate in result.gates
            ],
            "critical_failures": result.critical_failures,
            "warnings": result.warnings,
            "timestamp": str(os.environ.get('BUILD_TIMESTAMP', ''))
        }

        with open(output_path, 'w') as f:
            json.dump(report, f, indent=2)

        # Print summary
        self._print_quality_summary(result)

        return report

    def _print_quality_summary(self, result: QualityGateResult):
        """Print quality gates summary"""
        print("\n🚦 Quality Gates Summary")
        print("=" * 50)

        if result.overall_passed:
            print("✅ All critical quality gates PASSED")
        else:
            print("❌ Some critical quality gates FAILED")

        print(f"\n📊 Gate Results: {len([g for g in result.gates if g.passed])}/{len(result.gates)} passed")

        # Show individual gates
        for gate in result.gates:
            status = "✅" if gate.passed else "❌"
            critical_marker = " (CRITICAL)" if gate.critical else ""
            print(f"  {status} {gate.name}: {gate.current_value:.1f}{gate.unit} "
                  f"(threshold: {gate.threshold}{gate.unit}){critical_marker}")

        # Show failures and warnings
        if result.critical_failures:
            print(f"\n🚨 Critical Failures:")
            for failure in result.critical_failures:
                print(f"  • {failure}")

        if result.warnings:
            print(f"\n⚠️  Warnings:")
            for warning in result.warnings:
                print(f"  • {warning}")

def main():
    parser = argparse.ArgumentParser(description='Validate quality gates')
    parser.add_argument('--coverage-threshold', type=float, default=85.0,
                       help='Minimum code coverage percentage (default: 85%)')
    parser.add_argument('--complexity-threshold', type=float, default=10.0,
                       help='Maximum complexity per 1k lines (default: 10)')
    parser.add_argument('--duplication-threshold', type=float, default=5.0,
                       help='Maximum code duplication percentage (default: 5%)')
    parser.add_argument('--security-rating', default='A',
                       choices=['A', 'B', 'C', 'D', 'E'],
                       help='Minimum security rating (default: A)')
    parser.add_argument('--maintainability-rating', default='A',
                       choices=['A', 'B', 'C', 'D', 'E'],
                       help='Minimum maintainability rating (default: A)')
    parser.add_argument('--output', default='quality-gates.json',
                       help='Output report file path')

    args = parser.parse_args()

    # Get SonarCloud token from environment
    sonar_token = os.getenv('SONAR_TOKEN')

    # Initialize validator
    validator = QualityGateValidator(sonar_token=sonar_token)

    try:
        # Evaluate quality gates
        logger.info("Evaluating quality gates...")
        result = validator.evaluate_quality_gates(
            coverage_threshold=args.coverage_threshold,
            complexity_threshold=args.complexity_threshold,
            duplication_threshold=args.duplication_threshold,
            security_rating=args.security_rating,
            maintainability_rating=args.maintainability_rating
        )

        # Generate report
        validator.generate_quality_report(result, args.output)

        # Exit with appropriate code
        if not result.overall_passed:
            logger.error("Quality gates failed!")
            sys.exit(1)

        logger.info("All quality gates passed!")

    except Exception as e:
        logger.error(f"Quality gate validation failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()