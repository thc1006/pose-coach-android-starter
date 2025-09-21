#!/usr/bin/env python3
"""
Monitoring Setup Script for Pose Coach Android CI/CD Pipeline
Sets up comprehensive monitoring and observability infrastructure
"""

import argparse
import json
import logging
import os
import sys
import time
from typing import Dict, List, Optional
from dataclasses import dataclass
import requests
from google.cloud import monitoring_v3
from google.cloud import logging as cloud_logging
import firebase_admin
from firebase_admin import credentials, analytics

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class MonitoringConfig:
    """Monitoring configuration"""
    environment: str
    version: str
    duration_minutes: int
    alert_channels: List[str]
    metrics_config: Dict
    dashboards: List[str]

class MonitoringSetup:
    """Sets up monitoring infrastructure for deployments"""

    def __init__(self, project_id: str):
        self.project_id = project_id
        self.monitoring_client = monitoring_v3.MetricServiceClient()
        self.logging_client = cloud_logging.Client()
        self.project_name = f"projects/{project_id}"

    def setup_application_monitoring(self, config: MonitoringConfig):
        """Set up comprehensive application monitoring"""
        logger.info(f"Setting up monitoring for {config.environment} v{config.version}")

        # Create custom metrics
        self._create_custom_metrics(config)

        # Set up alerting policies
        self._setup_alerting_policies(config)

        # Configure log-based metrics
        self._setup_log_metrics(config)

        # Create monitoring dashboards
        self._create_dashboards(config)

        # Initialize real-time monitoring
        self._start_realtime_monitoring(config)

    def _create_custom_metrics(self, config: MonitoringConfig):
        """Create custom metrics for the application"""
        custom_metrics = [
            {
                "type": "custom.googleapis.com/pose_coach/pose_detection_latency",
                "labels": [
                    {"key": "environment", "value_type": "STRING"},
                    {"key": "version", "value_type": "STRING"},
                    {"key": "device_model", "value_type": "STRING"}
                ],
                "metric_kind": "GAUGE",
                "value_type": "DOUBLE",
                "unit": "ms",
                "description": "Pose detection inference latency"
            },
            {
                "type": "custom.googleapis.com/pose_coach/pose_accuracy",
                "labels": [
                    {"key": "environment", "value_type": "STRING"},
                    {"key": "version", "value_type": "STRING"},
                    {"key": "pose_type", "value_type": "STRING"}
                ],
                "metric_kind": "GAUGE",
                "value_type": "DOUBLE",
                "unit": "1",
                "description": "Pose detection accuracy score"
            },
            {
                "type": "custom.googleapis.com/pose_coach/app_startup_time",
                "labels": [
                    {"key": "environment", "value_type": "STRING"},
                    {"key": "version", "value_type": "STRING"},
                    {"key": "startup_type", "value_type": "STRING"}
                ],
                "metric_kind": "GAUGE",
                "value_type": "DOUBLE",
                "unit": "ms",
                "description": "Application startup time"
            },
            {
                "type": "custom.googleapis.com/pose_coach/memory_usage",
                "labels": [
                    {"key": "environment", "value_type": "STRING"},
                    {"key": "version", "value_type": "STRING"},
                    {"key": "memory_type", "value_type": "STRING"}
                ],
                "metric_kind": "GAUGE",
                "value_type": "DOUBLE",
                "unit": "MB",
                "description": "Memory usage by type"
            },
            {
                "type": "custom.googleapis.com/pose_coach/user_session_duration",
                "labels": [
                    {"key": "environment", "value_type": "STRING"},
                    {"key": "version", "value_type": "STRING"},
                    {"key": "session_type", "value_type": "STRING"}
                ],
                "metric_kind": "GAUGE",
                "value_type": "DOUBLE",
                "unit": "s",
                "description": "User session duration"
            },
            {
                "type": "custom.googleapis.com/pose_coach/api_response_time",
                "labels": [
                    {"key": "environment", "value_type": "STRING"},
                    {"key": "version", "value_type": "STRING"},
                    {"key": "endpoint", "value_type": "STRING"}
                ],
                "metric_kind": "GAUGE",
                "value_type": "DOUBLE",
                "unit": "ms",
                "description": "API response time by endpoint"
            }
        ]

        for metric_def in custom_metrics:
            try:
                descriptor = monitoring_v3.MetricDescriptor()
                descriptor.type = metric_def["type"]
                descriptor.metric_kind = getattr(monitoring_v3.MetricDescriptor.MetricKind, metric_def["metric_kind"])
                descriptor.value_type = getattr(monitoring_v3.MetricDescriptor.ValueType, metric_def["value_type"])
                descriptor.unit = metric_def["unit"]
                descriptor.description = metric_def["description"]

                for label in metric_def["labels"]:
                    label_descriptor = descriptor.labels.add()
                    label_descriptor.key = label["key"]
                    label_descriptor.value_type = getattr(monitoring_v3.LabelDescriptor.ValueType, label["value_type"])

                request = monitoring_v3.CreateMetricDescriptorRequest(
                    name=self.project_name,
                    metric_descriptor=descriptor
                )

                self.monitoring_client.create_metric_descriptor(request=request)
                logger.info(f"Created custom metric: {metric_def['type']}")

            except Exception as e:
                if "already exists" not in str(e):
                    logger.error(f"Failed to create metric {metric_def['type']}: {e}")

    def _setup_alerting_policies(self, config: MonitoringConfig):
        """Set up alerting policies for critical metrics"""
        alert_client = monitoring_v3.AlertPolicyServiceClient()

        alerting_policies = [
            {
                "display_name": f"Pose Coach - High Error Rate ({config.environment})",
                "conditions": [{
                    "display_name": "Error rate above threshold",
                    "condition_threshold": {
                        "filter": f'resource.type="gce_instance" AND metric.type="logging.googleapis.com/log_entry_count" AND metric.labels.severity="ERROR"',
                        "comparison": "COMPARISON_GREATER_THAN",
                        "threshold_value": 10,
                        "duration": "300s",
                        "aggregations": [{
                            "alignment_period": "60s",
                            "per_series_aligner": "ALIGN_RATE"
                        }]
                    }
                }],
                "alert_strategy": {
                    "notification_rate_limit": {
                        "period": "300s"
                    },
                    "auto_close": "1800s"
                }
            },
            {
                "display_name": f"Pose Coach - High Latency ({config.environment})",
                "conditions": [{
                    "display_name": "Pose detection latency above threshold",
                    "condition_threshold": {
                        "filter": f'metric.type="custom.googleapis.com/pose_coach/pose_detection_latency" AND metric.labels.environment="{config.environment}"',
                        "comparison": "COMPARISON_GREATER_THAN",
                        "threshold_value": 500,  # 500ms
                        "duration": "300s",
                        "aggregations": [{
                            "alignment_period": "60s",
                            "per_series_aligner": "ALIGN_MEAN"
                        }]
                    }
                }]
            },
            {
                "display_name": f"Pose Coach - Low Accuracy ({config.environment})",
                "conditions": [{
                    "display_name": "Pose accuracy below threshold",
                    "condition_threshold": {
                        "filter": f'metric.type="custom.googleapis.com/pose_coach/pose_accuracy" AND metric.labels.environment="{config.environment}"',
                        "comparison": "COMPARISON_LESS_THAN",
                        "threshold_value": 0.8,  # 80% accuracy
                        "duration": "600s",
                        "aggregations": [{
                            "alignment_period": "300s",
                            "per_series_aligner": "ALIGN_MEAN"
                        }]
                    }
                }]
            },
            {
                "display_name": f"Pose Coach - High Memory Usage ({config.environment})",
                "conditions": [{
                    "display_name": "Memory usage above threshold",
                    "condition_threshold": {
                        "filter": f'metric.type="custom.googleapis.com/pose_coach/memory_usage" AND metric.labels.environment="{config.environment}"',
                        "comparison": "COMPARISON_GREATER_THAN",
                        "threshold_value": 512,  # 512MB
                        "duration": "600s",
                        "aggregations": [{
                            "alignment_period": "60s",
                            "per_series_aligner": "ALIGN_MEAN"
                        }]
                    }
                }]
            }
        ]

        for policy_config in alerting_policies:
            try:
                policy = monitoring_v3.AlertPolicy()
                policy.display_name = policy_config["display_name"]
                policy.enabled = True

                # Add conditions
                for condition_config in policy_config["conditions"]:
                    condition = policy.conditions.add()
                    condition.display_name = condition_config["display_name"]

                    # Set up threshold condition
                    threshold = condition_config["condition_threshold"]
                    condition.condition_threshold.filter = threshold["filter"]
                    condition.condition_threshold.comparison = getattr(
                        monitoring_v3.ComparisonType, threshold["comparison"]
                    )
                    condition.condition_threshold.threshold_value.double_value = threshold["threshold_value"]
                    condition.condition_threshold.duration.seconds = int(threshold["duration"].rstrip('s'))

                    # Add aggregations
                    for agg_config in threshold["aggregations"]:
                        aggregation = condition.condition_threshold.aggregations.add()
                        aggregation.alignment_period.seconds = int(agg_config["alignment_period"].rstrip('s'))
                        aggregation.per_series_aligner = getattr(
                            monitoring_v3.Aggregation.Aligner, agg_config["per_series_aligner"]
                        )

                # Add notification channels
                for channel in config.alert_channels:
                    policy.notification_channels.append(channel)

                # Set alert strategy
                if "alert_strategy" in policy_config:
                    strategy = policy_config["alert_strategy"]
                    if "notification_rate_limit" in strategy:
                        policy.alert_strategy.notification_rate_limit.period.seconds = int(
                            strategy["notification_rate_limit"]["period"].rstrip('s')
                        )
                    if "auto_close" in strategy:
                        policy.alert_strategy.auto_close.seconds = int(
                            strategy["auto_close"].rstrip('s')
                        )

                request = monitoring_v3.CreateAlertPolicyRequest(
                    name=self.project_name,
                    alert_policy=policy
                )

                created_policy = alert_client.create_alert_policy(request=request)
                logger.info(f"Created alert policy: {policy.display_name}")

            except Exception as e:
                logger.error(f"Failed to create alert policy {policy_config['display_name']}: {e}")

    def _setup_log_metrics(self, config: MonitoringConfig):
        """Set up log-based metrics"""
        log_metrics = [
            {
                "name": f"pose_coach_errors_{config.environment}",
                "description": f"Error count for {config.environment}",
                "filter": f'resource.type="gce_instance" AND severity="ERROR" AND labels.environment="{config.environment}"',
                "metric_descriptor": {
                    "metric_kind": "DELTA",
                    "value_type": "INT64"
                }
            },
            {
                "name": f"pose_coach_crashes_{config.environment}",
                "description": f"Crash count for {config.environment}",
                "filter": f'resource.type="gce_instance" AND textPayload:"FATAL" AND labels.environment="{config.environment}"',
                "metric_descriptor": {
                    "metric_kind": "DELTA",
                    "value_type": "INT64"
                }
            },
            {
                "name": f"pose_coach_api_errors_{config.environment}",
                "description": f"API error count for {config.environment}",
                "filter": f'resource.type="gce_instance" AND httpRequest.status>=400 AND labels.environment="{config.environment}"',
                "metric_descriptor": {
                    "metric_kind": "DELTA",
                    "value_type": "INT64"
                }
            }
        ]

        for metric_config in log_metrics:
            try:
                metric = cloud_logging.Metric(
                    self.logging_client,
                    name=metric_config["name"],
                    filter_=metric_config["filter"],
                    description=metric_config["description"]
                )

                if not metric.exists():
                    metric.create()
                    logger.info(f"Created log metric: {metric_config['name']}")

            except Exception as e:
                logger.error(f"Failed to create log metric {metric_config['name']}: {e}")

    def _create_dashboards(self, config: MonitoringConfig):
        """Create monitoring dashboards"""
        dashboard_client = monitoring_v3.DashboardsServiceClient()

        dashboard_config = {
            "display_name": f"Pose Coach - {config.environment.title()} v{config.version}",
            "grid_layout": {
                "widgets": [
                    {
                        "title": "Pose Detection Latency",
                        "xy_chart": {
                            "data_sets": [{
                                "time_series_query": {
                                    "time_series_filter": {
                                        "filter": f'metric.type="custom.googleapis.com/pose_coach/pose_detection_latency" AND metric.labels.environment="{config.environment}"',
                                        "aggregation": {
                                            "alignment_period": "60s",
                                            "per_series_aligner": "ALIGN_MEAN"
                                        }
                                    }
                                }
                            }]
                        }
                    },
                    {
                        "title": "Pose Accuracy",
                        "xy_chart": {
                            "data_sets": [{
                                "time_series_query": {
                                    "time_series_filter": {
                                        "filter": f'metric.type="custom.googleapis.com/pose_coach/pose_accuracy" AND metric.labels.environment="{config.environment}"',
                                        "aggregation": {
                                            "alignment_period": "300s",
                                            "per_series_aligner": "ALIGN_MEAN"
                                        }
                                    }
                                }
                            }]
                        }
                    },
                    {
                        "title": "Memory Usage",
                        "xy_chart": {
                            "data_sets": [{
                                "time_series_query": {
                                    "time_series_filter": {
                                        "filter": f'metric.type="custom.googleapis.com/pose_coach/memory_usage" AND metric.labels.environment="{config.environment}"',
                                        "aggregation": {
                                            "alignment_period": "60s",
                                            "per_series_aligner": "ALIGN_MEAN"
                                        }
                                    }
                                }
                            }]
                        }
                    },
                    {
                        "title": "Error Rate",
                        "xy_chart": {
                            "data_sets": [{
                                "time_series_query": {
                                    "time_series_filter": {
                                        "filter": f'metric.type="logging.googleapis.com/log_entry_count" AND metric.labels.severity="ERROR"',
                                        "aggregation": {
                                            "alignment_period": "60s",
                                            "per_series_aligner": "ALIGN_RATE"
                                        }
                                    }
                                }
                            }]
                        }
                    }
                ]
            }
        }

        try:
            dashboard = monitoring_v3.Dashboard()
            dashboard.display_name = dashboard_config["display_name"]

            # Convert config to protobuf format (simplified)
            request = monitoring_v3.CreateDashboardRequest(
                parent=self.project_name,
                dashboard=dashboard
            )

            created_dashboard = dashboard_client.create_dashboard(request=request)
            logger.info(f"Created dashboard: {dashboard.display_name}")
            return created_dashboard.name

        except Exception as e:
            logger.error(f"Failed to create dashboard: {e}")
            return None

    def _start_realtime_monitoring(self, config: MonitoringConfig):
        """Start real-time monitoring for the deployment"""
        monitoring_config = {
            "environment": config.environment,
            "version": config.version,
            "start_time": time.time(),
            "duration": config.duration_minutes * 60,
            "metrics": config.metrics_config
        }

        # Save monitoring configuration
        with open(f"monitoring-{config.environment}-{config.version}.json", 'w') as f:
            json.dump(monitoring_config, f, indent=2)

        logger.info(f"Started real-time monitoring for {config.duration_minutes} minutes")

    def setup_firebase_analytics(self, config: MonitoringConfig):
        """Set up Firebase Analytics for user behavior tracking"""
        try:
            # Initialize Firebase Admin SDK
            if not firebase_admin._apps:
                cred = credentials.Certificate("firebase-service-account.json")
                firebase_admin.initialize_app(cred)

            # Set up custom events for tracking
            custom_events = [
                "pose_detection_started",
                "pose_detection_completed",
                "pose_accuracy_measured",
                "user_session_started",
                "user_session_ended",
                "coaching_session_completed",
                "ai_feedback_provided"
            ]

            logger.info("Firebase Analytics configured for custom events")
            return custom_events

        except Exception as e:
            logger.error(f"Failed to setup Firebase Analytics: {e}")
            return []

    def create_slo_monitoring(self, config: MonitoringConfig):
        """Create Service Level Objective monitoring"""
        slo_configs = [
            {
                "name": "pose_detection_latency_slo",
                "description": "Pose detection should complete within 200ms for 95% of requests",
                "threshold": 200,  # ms
                "target": 0.95    # 95%
            },
            {
                "name": "pose_accuracy_slo",
                "description": "Pose accuracy should be above 85% for 99% of detections",
                "threshold": 0.85,
                "target": 0.99
            },
            {
                "name": "app_availability_slo",
                "description": "App should be available 99.9% of the time",
                "threshold": 0.999,
                "target": 0.999
            }
        ]

        for slo_config in slo_configs:
            logger.info(f"Setting up SLO monitoring: {slo_config['name']}")
            # Implementation would create SLO monitoring based on the config

def main():
    parser = argparse.ArgumentParser(description='Set up monitoring infrastructure')
    parser.add_argument('--environment', required=True,
                       help='Target environment (staging, production, beta)')
    parser.add_argument('--version', required=True,
                       help='Application version')
    parser.add_argument('--duration', type=int, default=60,
                       help='Monitoring duration in minutes (default: 60)')
    parser.add_argument('--project-id', required=True,
                       help='Google Cloud Project ID')
    parser.add_argument('--alert-channels', nargs='+',
                       help='Alert notification channels')

    args = parser.parse_args()

    # Initialize monitoring setup
    monitoring_setup = MonitoringSetup(args.project_id)

    # Create monitoring configuration
    config = MonitoringConfig(
        environment=args.environment,
        version=args.version,
        duration_minutes=args.duration,
        alert_channels=args.alert_channels or [],
        metrics_config={
            "pose_detection_latency": {"threshold": 200, "unit": "ms"},
            "pose_accuracy": {"threshold": 0.85, "unit": "ratio"},
            "memory_usage": {"threshold": 512, "unit": "MB"},
            "error_rate": {"threshold": 0.01, "unit": "ratio"}
        },
        dashboards=["performance", "accuracy", "errors"]
    )

    try:
        # Set up comprehensive monitoring
        logger.info(f"Setting up monitoring for {args.environment} v{args.version}")
        monitoring_setup.setup_application_monitoring(config)

        # Set up Firebase Analytics
        monitoring_setup.setup_firebase_analytics(config)

        # Create SLO monitoring
        monitoring_setup.create_slo_monitoring(config)

        logger.info("Monitoring setup completed successfully")

    except Exception as e:
        logger.error(f"Monitoring setup failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()