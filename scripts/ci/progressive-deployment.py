#!/usr/bin/env python3
"""
Progressive Deployment Strategy Implementation for Pose Coach Android CI/CD Pipeline
Implements canary, blue-green, and gradual rollout deployment strategies
"""

import argparse
import json
import logging
import os
import sys
import time
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
import requests
from google.cloud import monitoring_v3
import subprocess

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class DeploymentStrategy(Enum):
    CANARY = "canary"
    BLUE_GREEN = "blue_green"
    GRADUAL = "gradual"
    IMMEDIATE = "immediate"

class DeploymentStatus(Enum):
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    SUCCESS = "success"
    FAILED = "failed"
    ROLLED_BACK = "rolled_back"

@dataclass
class HealthMetric:
    """Health metric for deployment monitoring"""
    name: str
    current_value: float
    threshold: float
    comparison: str  # "less_than", "greater_than"
    is_healthy: bool

@dataclass
class DeploymentStage:
    """Deployment stage configuration"""
    name: str
    percentage: int
    duration_minutes: int
    health_checks: List[str]
    success_criteria: Dict[str, float]

@dataclass
class DeploymentResult:
    """Deployment result"""
    stage: str
    status: DeploymentStatus
    metrics: List[HealthMetric]
    duration_seconds: float
    error_message: Optional[str] = None

class ProgressiveDeploymentManager:
    """Manages progressive deployment strategies"""

    def __init__(self, project_id: str, environment: str):
        self.project_id = project_id
        self.environment = environment
        self.monitoring_client = monitoring_v3.MetricServiceClient()
        self.project_name = f"projects/{project_id}"

    def execute_deployment(self, strategy: DeploymentStrategy, version: str,
                          config: Dict) -> List[DeploymentResult]:
        """Execute deployment using specified strategy"""
        logger.info(f"Starting {strategy.value} deployment for version {version}")

        if strategy == DeploymentStrategy.CANARY:
            return self._execute_canary_deployment(version, config)
        elif strategy == DeploymentStrategy.BLUE_GREEN:
            return self._execute_blue_green_deployment(version, config)
        elif strategy == DeploymentStrategy.GRADUAL:
            return self._execute_gradual_deployment(version, config)
        elif strategy == DeploymentStrategy.IMMEDIATE:
            return self._execute_immediate_deployment(version, config)
        else:
            raise ValueError(f"Unsupported deployment strategy: {strategy}")

    def _execute_canary_deployment(self, version: str, config: Dict) -> List[DeploymentResult]:
        """Execute canary deployment strategy"""
        logger.info("Executing canary deployment...")

        results = []
        canary_percentage = config.get("canary_percentage", 5)
        monitoring_duration = config.get("monitoring_duration", 1800)  # 30 minutes

        # Stage 1: Deploy to canary percentage
        logger.info(f"Deploying to {canary_percentage}% of users (canary)")
        canary_result = self._deploy_to_percentage(
            version, canary_percentage, "canary", config
        )
        results.append(canary_result)

        if canary_result.status != DeploymentStatus.SUCCESS:
            logger.error("Canary deployment failed")
            return results

        # Stage 2: Monitor canary deployment
        logger.info(f"Monitoring canary deployment for {monitoring_duration} seconds")
        monitoring_result = self._monitor_deployment(
            version, canary_percentage, monitoring_duration, config
        )
        results.append(monitoring_result)

        if monitoring_result.status != DeploymentStatus.SUCCESS:
            logger.error("Canary monitoring failed - initiating rollback")
            rollback_result = self._rollback_deployment(version, canary_percentage)
            results.append(rollback_result)
            return results

        # Stage 3: Full deployment
        logger.info("Canary successful - proceeding with full deployment")
        full_result = self._deploy_to_percentage(version, 100, "full", config)
        results.append(full_result)

        return results

    def _execute_blue_green_deployment(self, version: str, config: Dict) -> List[DeploymentResult]:
        """Execute blue-green deployment strategy"""
        logger.info("Executing blue-green deployment...")

        results = []

        # Stage 1: Deploy to green environment
        logger.info("Deploying to green environment")
        green_result = self._deploy_to_green_environment(version, config)
        results.append(green_result)

        if green_result.status != DeploymentStatus.SUCCESS:
            logger.error("Green environment deployment failed")
            return results

        # Stage 2: Health check green environment
        logger.info("Health checking green environment")
        health_result = self._health_check_green_environment(version, config)
        results.append(health_result)

        if health_result.status != DeploymentStatus.SUCCESS:
            logger.error("Green environment health check failed")
            return results

        # Stage 3: Switch traffic to green
        logger.info("Switching traffic to green environment")
        switch_result = self._switch_to_green_environment(version, config)
        results.append(switch_result)

        if switch_result.status != DeploymentStatus.SUCCESS:
            logger.error("Traffic switch failed - rolling back")
            rollback_result = self._rollback_to_blue_environment(version)
            results.append(rollback_result)

        return results

    def _execute_gradual_deployment(self, version: str, config: Dict) -> List[DeploymentResult]:
        """Execute gradual deployment strategy"""
        logger.info("Executing gradual deployment...")

        results = []
        stages = config.get("stages", [
            {"percentage": 1, "duration": 2},
            {"percentage": 5, "duration": 8},
            {"percentage": 25, "duration": 24},
            {"percentage": 50, "duration": 48},
            {"percentage": 100, "duration": 168}
        ])

        for i, stage_config in enumerate(stages):
            stage_name = f"gradual_stage_{i+1}"
            percentage = stage_config["percentage"]
            duration_hours = stage_config["duration"]

            logger.info(f"Deploying to {percentage}% of users (stage {i+1})")

            # Deploy to percentage
            deploy_result = self._deploy_to_percentage(
                version, percentage, stage_name, config
            )
            results.append(deploy_result)

            if deploy_result.status != DeploymentStatus.SUCCESS:
                logger.error(f"Stage {i+1} deployment failed")
                break

            # Monitor for duration
            monitor_result = self._monitor_deployment(
                version, percentage, duration_hours * 3600, config
            )
            results.append(monitor_result)

            if monitor_result.status != DeploymentStatus.SUCCESS:
                logger.error(f"Stage {i+1} monitoring failed - rolling back")
                rollback_result = self._rollback_deployment(version, percentage)
                results.append(rollback_result)
                break

            logger.info(f"Stage {i+1} successful")

        return results

    def _execute_immediate_deployment(self, version: str, config: Dict) -> List[DeploymentResult]:
        """Execute immediate deployment strategy"""
        logger.info("Executing immediate deployment...")

        return [self._deploy_to_percentage(version, 100, "immediate", config)]

    def _deploy_to_percentage(self, version: str, percentage: int, stage: str,
                             config: Dict) -> DeploymentResult:
        """Deploy to specified percentage of users"""
        start_time = time.time()

        try:
            # Update rollout percentage in Play Store
            result = self._update_play_store_rollout(version, percentage)

            if result["success"]:
                # Wait for deployment to propagate
                propagation_time = config.get("propagation_time", 300)  # 5 minutes
                logger.info(f"Waiting {propagation_time} seconds for deployment propagation")
                time.sleep(propagation_time)

                # Verify deployment
                if self._verify_deployment(version, percentage):
                    duration = time.time() - start_time
                    return DeploymentResult(
                        stage=stage,
                        status=DeploymentStatus.SUCCESS,
                        metrics=[],
                        duration_seconds=duration
                    )

            raise Exception(result.get("error", "Deployment verification failed"))

        except Exception as e:
            duration = time.time() - start_time
            return DeploymentResult(
                stage=stage,
                status=DeploymentStatus.FAILED,
                metrics=[],
                duration_seconds=duration,
                error_message=str(e)
            )

    def _deploy_to_green_environment(self, version: str, config: Dict) -> DeploymentResult:
        """Deploy to green environment for blue-green strategy"""
        start_time = time.time()

        try:
            # Deploy to internal track first (green environment)
            result = self._deploy_to_internal_track(version)

            if result["success"]:
                duration = time.time() - start_time
                return DeploymentResult(
                    stage="green_deployment",
                    status=DeploymentStatus.SUCCESS,
                    metrics=[],
                    duration_seconds=duration
                )

            raise Exception(result.get("error", "Green environment deployment failed"))

        except Exception as e:
            duration = time.time() - start_time
            return DeploymentResult(
                stage="green_deployment",
                status=DeploymentStatus.FAILED,
                metrics=[],
                duration_seconds=duration,
                error_message=str(e)
            )

    def _health_check_green_environment(self, version: str, config: Dict) -> DeploymentResult:
        """Health check green environment"""
        start_time = time.time()

        try:
            health_checks = config.get("health_checks", [])
            metrics = []

            for check in health_checks:
                metric = self._run_health_check(check, version)
                metrics.append(metric)

            # All health checks must pass
            all_healthy = all(metric.is_healthy for metric in metrics)

            duration = time.time() - start_time
            status = DeploymentStatus.SUCCESS if all_healthy else DeploymentStatus.FAILED

            return DeploymentResult(
                stage="green_health_check",
                status=status,
                metrics=metrics,
                duration_seconds=duration,
                error_message=None if all_healthy else "Health checks failed"
            )

        except Exception as e:
            duration = time.time() - start_time
            return DeploymentResult(
                stage="green_health_check",
                status=DeploymentStatus.FAILED,
                metrics=[],
                duration_seconds=duration,
                error_message=str(e)
            )

    def _switch_to_green_environment(self, version: str, config: Dict) -> DeploymentResult:
        """Switch traffic to green environment"""
        start_time = time.time()

        try:
            # Promote from internal to production track
            result = self._promote_to_production_track(version)

            if result["success"]:
                duration = time.time() - start_time
                return DeploymentResult(
                    stage="traffic_switch",
                    status=DeploymentStatus.SUCCESS,
                    metrics=[],
                    duration_seconds=duration
                )

            raise Exception(result.get("error", "Traffic switch failed"))

        except Exception as e:
            duration = time.time() - start_time
            return DeploymentResult(
                stage="traffic_switch",
                status=DeploymentStatus.FAILED,
                metrics=[],
                duration_seconds=duration,
                error_message=str(e)
            )

    def _monitor_deployment(self, version: str, percentage: int, duration_seconds: int,
                           config: Dict) -> DeploymentResult:
        """Monitor deployment health for specified duration"""
        start_time = time.time()
        end_time = start_time + duration_seconds

        metrics_history = []
        check_interval = config.get("check_interval", 300)  # 5 minutes

        try:
            while time.time() < end_time:
                # Run health checks
                current_metrics = []
                health_checks = config.get("health_checks", [
                    "crash_rate", "error_rate", "response_time", "user_satisfaction"
                ])

                for check in health_checks:
                    metric = self._run_health_check(check, version)
                    current_metrics.append(metric)

                metrics_history.append(current_metrics)

                # Check if any metric is unhealthy
                unhealthy_metrics = [m for m in current_metrics if not m.is_healthy]
                if unhealthy_metrics:
                    logger.warning(f"Unhealthy metrics detected: {[m.name for m in unhealthy_metrics]}")

                    # Check if we should trigger rollback
                    if self._should_trigger_rollback(unhealthy_metrics, config):
                        duration = time.time() - start_time
                        return DeploymentResult(
                            stage=f"monitoring_{percentage}pct",
                            status=DeploymentStatus.FAILED,
                            metrics=current_metrics,
                            duration_seconds=duration,
                            error_message=f"Rollback triggered due to unhealthy metrics: {[m.name for m in unhealthy_metrics]}"
                        )

                # Wait before next check
                time.sleep(check_interval)

            # Monitoring period completed successfully
            duration = time.time() - start_time
            final_metrics = metrics_history[-1] if metrics_history else []

            return DeploymentResult(
                stage=f"monitoring_{percentage}pct",
                status=DeploymentStatus.SUCCESS,
                metrics=final_metrics,
                duration_seconds=duration
            )

        except Exception as e:
            duration = time.time() - start_time
            return DeploymentResult(
                stage=f"monitoring_{percentage}pct",
                status=DeploymentStatus.FAILED,
                metrics=[],
                duration_seconds=duration,
                error_message=str(e)
            )

    def _run_health_check(self, check_name: str, version: str) -> HealthMetric:
        """Run a specific health check"""
        if check_name == "crash_rate":
            return self._check_crash_rate(version)
        elif check_name == "error_rate":
            return self._check_error_rate(version)
        elif check_name == "response_time":
            return self._check_response_time(version)
        elif check_name == "user_satisfaction":
            return self._check_user_satisfaction(version)
        elif check_name == "memory_usage":
            return self._check_memory_usage(version)
        elif check_name == "cpu_usage":
            return self._check_cpu_usage(version)
        else:
            logger.warning(f"Unknown health check: {check_name}")
            return HealthMetric(check_name, 0, 0, "less_than", True)

    def _check_crash_rate(self, version: str) -> HealthMetric:
        """Check application crash rate"""
        try:
            # Query crash rate from monitoring system
            crash_rate = self._query_metric(
                "custom.googleapis.com/pose_coach/crash_rate",
                {"version": version}
            )

            threshold = 1.0  # 1% crash rate threshold
            is_healthy = crash_rate < threshold

            return HealthMetric(
                name="crash_rate",
                current_value=crash_rate,
                threshold=threshold,
                comparison="less_than",
                is_healthy=is_healthy
            )

        except Exception as e:
            logger.error(f"Failed to check crash rate: {e}")
            return HealthMetric("crash_rate", 0, 1.0, "less_than", True)

    def _check_error_rate(self, version: str) -> HealthMetric:
        """Check application error rate"""
        try:
            error_rate = self._query_metric(
                "custom.googleapis.com/pose_coach/error_rate",
                {"version": version}
            )

            threshold = 2.0  # 2% error rate threshold
            is_healthy = error_rate < threshold

            return HealthMetric(
                name="error_rate",
                current_value=error_rate,
                threshold=threshold,
                comparison="less_than",
                is_healthy=is_healthy
            )

        except Exception as e:
            logger.error(f"Failed to check error rate: {e}")
            return HealthMetric("error_rate", 0, 2.0, "less_than", True)

    def _check_response_time(self, version: str) -> HealthMetric:
        """Check API response time"""
        try:
            response_time = self._query_metric(
                "custom.googleapis.com/pose_coach/api_response_time",
                {"version": version}
            )

            threshold = 500.0  # 500ms threshold
            is_healthy = response_time < threshold

            return HealthMetric(
                name="response_time",
                current_value=response_time,
                threshold=threshold,
                comparison="less_than",
                is_healthy=is_healthy
            )

        except Exception as e:
            logger.error(f"Failed to check response time: {e}")
            return HealthMetric("response_time", 0, 500.0, "less_than", True)

    def _check_user_satisfaction(self, version: str) -> HealthMetric:
        """Check user satisfaction metrics"""
        try:
            # This would typically come from app store ratings or in-app feedback
            satisfaction_score = self._query_metric(
                "custom.googleapis.com/pose_coach/user_satisfaction",
                {"version": version}
            )

            threshold = 4.0  # 4.0/5.0 satisfaction threshold
            is_healthy = satisfaction_score >= threshold

            return HealthMetric(
                name="user_satisfaction",
                current_value=satisfaction_score,
                threshold=threshold,
                comparison="greater_than",
                is_healthy=is_healthy
            )

        except Exception as e:
            logger.error(f"Failed to check user satisfaction: {e}")
            return HealthMetric("user_satisfaction", 4.5, 4.0, "greater_than", True)

    def _check_memory_usage(self, version: str) -> HealthMetric:
        """Check memory usage"""
        try:
            memory_usage = self._query_metric(
                "custom.googleapis.com/pose_coach/memory_usage",
                {"version": version}
            )

            threshold = 512.0  # 512MB threshold
            is_healthy = memory_usage < threshold

            return HealthMetric(
                name="memory_usage",
                current_value=memory_usage,
                threshold=threshold,
                comparison="less_than",
                is_healthy=is_healthy
            )

        except Exception as e:
            logger.error(f"Failed to check memory usage: {e}")
            return HealthMetric("memory_usage", 0, 512.0, "less_than", True)

    def _check_cpu_usage(self, version: str) -> HealthMetric:
        """Check CPU usage"""
        try:
            cpu_usage = self._query_metric(
                "custom.googleapis.com/pose_coach/cpu_usage",
                {"version": version}
            )

            threshold = 70.0  # 70% CPU threshold
            is_healthy = cpu_usage < threshold

            return HealthMetric(
                name="cpu_usage",
                current_value=cpu_usage,
                threshold=threshold,
                comparison="less_than",
                is_healthy=is_healthy
            )

        except Exception as e:
            logger.error(f"Failed to check CPU usage: {e}")
            return HealthMetric("cpu_usage", 0, 70.0, "less_than", True)

    def _query_metric(self, metric_type: str, labels: Dict[str, str]) -> float:
        """Query metric from monitoring system"""
        try:
            # Build filter for metric query
            label_filters = [f'metric.labels.{k}="{v}"' for k, v in labels.items()]
            filter_str = f'metric.type="{metric_type}"'
            if label_filters:
                filter_str += " AND " + " AND ".join(label_filters)

            # Create time interval (last 10 minutes)
            import time
            from google.protobuf import timestamp_pb2

            end_time = timestamp_pb2.Timestamp()
            end_time.GetCurrentTime()

            start_time = timestamp_pb2.Timestamp()
            start_time.seconds = end_time.seconds - 600  # 10 minutes ago

            interval = monitoring_v3.TimeInterval({
                "end_time": end_time,
                "start_time": start_time
            })

            # Query the metric
            request = monitoring_v3.ListTimeSeriesRequest(
                name=self.project_name,
                filter=filter_str,
                interval=interval,
                view=monitoring_v3.ListTimeSeriesRequest.TimeSeriesView.FULL
            )

            results = self.monitoring_client.list_time_series(request=request)

            # Calculate average value
            values = []
            for result in results:
                for point in result.points:
                    if hasattr(point.value, 'double_value'):
                        values.append(point.value.double_value)
                    elif hasattr(point.value, 'int64_value'):
                        values.append(float(point.value.int64_value))

            return sum(values) / len(values) if values else 0.0

        except Exception as e:
            logger.error(f"Failed to query metric {metric_type}: {e}")
            return 0.0

    def _should_trigger_rollback(self, unhealthy_metrics: List[HealthMetric],
                                config: Dict) -> bool:
        """Determine if rollback should be triggered"""
        rollback_config = config.get("rollback_triggers", {})

        # Check critical metrics
        critical_metrics = ["crash_rate", "error_rate"]
        for metric in unhealthy_metrics:
            if metric.name in critical_metrics:
                threshold = rollback_config.get(f"{metric.name}_threshold", 5.0)
                if metric.current_value > threshold:
                    logger.error(f"Critical metric {metric.name} exceeded rollback threshold: {metric.current_value} > {threshold}")
                    return True

        # Check if multiple metrics are unhealthy
        if len(unhealthy_metrics) >= rollback_config.get("max_unhealthy_metrics", 3):
            logger.error(f"Too many unhealthy metrics: {len(unhealthy_metrics)}")
            return True

        return False

    def _update_play_store_rollout(self, version: str, percentage: int) -> Dict:
        """Update Play Store rollout percentage"""
        try:
            # This would use the Google Play Developer API
            # For now, simulate the call
            logger.info(f"Updating Play Store rollout to {percentage}% for version {version}")

            # Simulate API call
            time.sleep(2)

            return {"success": True}

        except Exception as e:
            return {"success": False, "error": str(e)}

    def _deploy_to_internal_track(self, version: str) -> Dict:
        """Deploy to internal track (green environment)"""
        try:
            logger.info(f"Deploying version {version} to internal track")

            # Simulate deployment
            time.sleep(5)

            return {"success": True}

        except Exception as e:
            return {"success": False, "error": str(e)}

    def _promote_to_production_track(self, version: str) -> Dict:
        """Promote from internal to production track"""
        try:
            logger.info(f"Promoting version {version} to production track")

            # Simulate promotion
            time.sleep(3)

            return {"success": True}

        except Exception as e:
            return {"success": False, "error": str(e)}

    def _verify_deployment(self, version: str, percentage: int) -> bool:
        """Verify deployment was successful"""
        try:
            # Simulate verification
            time.sleep(1)
            return True

        except Exception as e:
            logger.error(f"Deployment verification failed: {e}")
            return False

    def _rollback_deployment(self, version: str, percentage: int) -> DeploymentResult:
        """Rollback deployment"""
        start_time = time.time()

        try:
            logger.info(f"Rolling back deployment for version {version}")

            # Get previous version
            previous_version = self._get_previous_version()

            # Rollback to previous version
            result = self._update_play_store_rollout(previous_version, percentage)

            if result["success"]:
                duration = time.time() - start_time
                return DeploymentResult(
                    stage="rollback",
                    status=DeploymentStatus.SUCCESS,
                    metrics=[],
                    duration_seconds=duration
                )

            raise Exception(result.get("error", "Rollback failed"))

        except Exception as e:
            duration = time.time() - start_time
            return DeploymentResult(
                stage="rollback",
                status=DeploymentStatus.FAILED,
                metrics=[],
                duration_seconds=duration,
                error_message=str(e)
            )

    def _rollback_to_blue_environment(self, version: str) -> DeploymentResult:
        """Rollback to blue environment"""
        start_time = time.time()

        try:
            logger.info("Rolling back to blue environment")

            # Switch traffic back to blue environment
            # This would involve reversing the traffic switch
            time.sleep(2)

            duration = time.time() - start_time
            return DeploymentResult(
                stage="blue_rollback",
                status=DeploymentStatus.SUCCESS,
                metrics=[],
                duration_seconds=duration
            )

        except Exception as e:
            duration = time.time() - start_time
            return DeploymentResult(
                stage="blue_rollback",
                status=DeploymentStatus.FAILED,
                metrics=[],
                duration_seconds=duration,
                error_message=str(e)
            )

    def _get_previous_version(self) -> str:
        """Get the previous stable version for rollback"""
        # This would query the deployment history
        return "1.0.0"  # Placeholder

    def generate_deployment_report(self, results: List[DeploymentResult],
                                  strategy: DeploymentStrategy, version: str,
                                  output_path: str):
        """Generate deployment report"""
        overall_success = all(r.status == DeploymentStatus.SUCCESS for r in results)
        total_duration = sum(r.duration_seconds for r in results)

        report = {
            "deployment": {
                "strategy": strategy.value,
                "version": version,
                "environment": self.environment,
                "overall_success": overall_success,
                "total_duration_seconds": total_duration,
                "timestamp": time.time()
            },
            "stages": [
                {
                    "name": result.stage,
                    "status": result.status.value,
                    "duration_seconds": result.duration_seconds,
                    "metrics": [
                        {
                            "name": metric.name,
                            "value": metric.current_value,
                            "threshold": metric.threshold,
                            "healthy": metric.is_healthy
                        }
                        for metric in result.metrics
                    ],
                    "error": result.error_message
                }
                for result in results
            ]
        }

        with open(output_path, 'w') as f:
            json.dump(report, f, indent=2)

        # Print summary
        self._print_deployment_summary(report)

        return report

    def _print_deployment_summary(self, report: Dict):
        """Print deployment summary"""
        deployment = report["deployment"]

        print(f"\n🚀 Progressive Deployment Summary")
        print("=" * 50)
        print(f"Strategy: {deployment['strategy'].title()}")
        print(f"Version: {deployment['version']}")
        print(f"Environment: {deployment['environment']}")
        print(f"Overall Success: {'✅ Yes' if deployment['overall_success'] else '❌ No'}")
        print(f"Total Duration: {deployment['total_duration_seconds']:.1f} seconds")

        print(f"\n📊 Stage Results:")
        for stage in report["stages"]:
            status_emoji = "✅" if stage["status"] == "success" else "❌"
            print(f"  {status_emoji} {stage['name']}: {stage['status']} ({stage['duration_seconds']:.1f}s)")

            if stage["error"]:
                print(f"    Error: {stage['error']}")

def main():
    parser = argparse.ArgumentParser(description='Execute progressive deployment')
    parser.add_argument('--strategy', required=True,
                       choices=['canary', 'blue_green', 'gradual', 'immediate'],
                       help='Deployment strategy')
    parser.add_argument('--version', required=True,
                       help='Version to deploy')
    parser.add_argument('--environment', required=True,
                       help='Target environment')
    parser.add_argument('--project-id', required=True,
                       help='Google Cloud Project ID')
    parser.add_argument('--config', required=True,
                       help='Deployment configuration file')
    parser.add_argument('--output', default='deployment-report.json',
                       help='Output report file path')

    args = parser.parse_args()

    # Load configuration
    with open(args.config, 'r') as f:
        config = json.load(f)

    # Initialize deployment manager
    manager = ProgressiveDeploymentManager(args.project_id, args.environment)

    try:
        # Execute deployment
        strategy = DeploymentStrategy(args.strategy)
        results = manager.execute_deployment(strategy, args.version, config)

        # Generate report
        report = manager.generate_deployment_report(
            results, strategy, args.version, args.output
        )

        # Check overall success
        if not report["deployment"]["overall_success"]:
            logger.error("Deployment failed!")
            sys.exit(1)

        logger.info("Deployment completed successfully!")

    except Exception as e:
        logger.error(f"Deployment failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()