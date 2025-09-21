#!/usr/bin/env python3
"""
AI Model Validation Script for Pose Coach Android CI/CD Pipeline
Tests pose detection accuracy, latency, and model performance
"""

import argparse
import json
import logging
import os
import sys
import time
from pathlib import Path
from typing import Dict, List, Tuple
from dataclasses import dataclass
import requests
import numpy as np
from concurrent.futures import ThreadPoolExecutor, as_completed

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class PoseTestCase:
    """Test case for pose detection"""
    image_path: str
    expected_poses: List[Dict]
    difficulty_level: str  # easy, medium, hard
    scene_type: str  # indoor, outdoor, gym, home

@dataclass
class ModelPerformanceResult:
    """Model performance test result"""
    test_case: PoseTestCase
    detected_poses: List[Dict]
    accuracy_score: float
    latency_ms: float
    confidence_scores: List[float]
    success: bool
    error_message: str = ""

class PoseModelValidator:
    """Validates AI pose detection model performance"""

    def __init__(self, gemini_api_key: str, accuracy_threshold: float = 0.85,
                 latency_threshold_ms: float = 200):
        self.gemini_api_key = gemini_api_key
        self.accuracy_threshold = accuracy_threshold
        self.latency_threshold_ms = latency_threshold_ms
        self.base_url = "https://generativelanguage.googleapis.com/v1"

    def load_test_cases(self, test_data_dir: str) -> List[PoseTestCase]:
        """Load test cases from directory"""
        test_cases = []
        test_data_path = Path(test_data_dir)

        # Load test manifest
        manifest_path = test_data_path / "test_manifest.json"
        if not manifest_path.exists():
            raise FileNotFoundError(f"Test manifest not found: {manifest_path}")

        with open(manifest_path) as f:
            manifest = json.load(f)

        for test_item in manifest["test_cases"]:
            image_path = test_data_path / test_item["image"]
            if not image_path.exists():
                logger.warning(f"Test image not found: {image_path}")
                continue

            test_cases.append(PoseTestCase(
                image_path=str(image_path),
                expected_poses=test_item["expected_poses"],
                difficulty_level=test_item.get("difficulty", "medium"),
                scene_type=test_item.get("scene_type", "indoor")
            ))

        logger.info(f"Loaded {len(test_cases)} test cases")
        return test_cases

    def detect_poses(self, image_path: str) -> Tuple[List[Dict], float]:
        """Detect poses in image using Gemini API"""
        start_time = time.time()

        try:
            # Read and encode image
            with open(image_path, 'rb') as f:
                image_data = f.read()

            # Convert to base64
            import base64
            image_b64 = base64.b64encode(image_data).decode('utf-8')

            # Prepare request for Gemini API
            prompt = """
            Analyze this image for human poses. For each person detected, provide:
            1. Bounding box coordinates (x, y, width, height) normalized to 0-1
            2. Key body landmarks (head, shoulders, elbows, wrists, hips, knees, ankles)
            3. Confidence score (0-1)
            4. Pose classification (standing, sitting, lying, exercising, etc.)

            Return the results as a JSON array with this structure:
            [
                {
                    "person_id": 1,
                    "bbox": {"x": 0.0, "y": 0.0, "width": 0.0, "height": 0.0},
                    "landmarks": {
                        "head": {"x": 0.0, "y": 0.0},
                        "left_shoulder": {"x": 0.0, "y": 0.0},
                        "right_shoulder": {"x": 0.0, "y": 0.0},
                        // ... other landmarks
                    },
                    "confidence": 0.95,
                    "pose_class": "standing",
                    "pose_quality": "good"
                }
            ]
            """

            headers = {
                'Content-Type': 'application/json',
            }

            payload = {
                "contents": [
                    {
                        "parts": [
                            {"text": prompt},
                            {
                                "inline_data": {
                                    "mime_type": "image/jpeg",
                                    "data": image_b64
                                }
                            }
                        ]
                    }
                ]
            }

            url = f"{self.base_url}/models/gemini-pro-vision:generateContent?key={self.gemini_api_key}"
            response = requests.post(url, json=payload, headers=headers, timeout=30)

            latency_ms = (time.time() - start_time) * 1000

            if response.status_code != 200:
                raise Exception(f"API request failed: {response.status_code} - {response.text}")

            result = response.json()
            content = result['candidates'][0]['content']['parts'][0]['text']

            # Parse JSON response
            try:
                poses = json.loads(content)
                return poses, latency_ms
            except json.JSONDecodeError:
                # Fallback: extract JSON from text response
                import re
                json_match = re.search(r'\[.*\]', content, re.DOTALL)
                if json_match:
                    poses = json.loads(json_match.group())
                    return poses, latency_ms
                else:
                    raise Exception("Failed to parse pose detection results")

        except Exception as e:
            logger.error(f"Pose detection failed for {image_path}: {e}")
            return [], (time.time() - start_time) * 1000

    def calculate_pose_accuracy(self, detected_poses: List[Dict],
                               expected_poses: List[Dict]) -> float:
        """Calculate pose detection accuracy using IoU and landmark similarity"""
        if not expected_poses:
            return 1.0 if not detected_poses else 0.0

        if not detected_poses:
            return 0.0

        total_score = 0.0
        matched_poses = 0

        for expected_pose in expected_poses:
            best_match_score = 0.0

            for detected_pose in detected_poses:
                # Calculate bounding box IoU
                iou_score = self._calculate_bbox_iou(
                    expected_pose.get('bbox', {}),
                    detected_pose.get('bbox', {})
                )

                # Calculate landmark similarity
                landmark_score = self._calculate_landmark_similarity(
                    expected_pose.get('landmarks', {}),
                    detected_pose.get('landmarks', {})
                )

                # Combine scores
                combined_score = (iou_score * 0.4 + landmark_score * 0.6)
                best_match_score = max(best_match_score, combined_score)

            if best_match_score > 0.5:  # Threshold for considering a match
                total_score += best_match_score
                matched_poses += 1

        # Penalize for unmatched poses
        precision = matched_poses / len(detected_poses) if detected_poses else 0
        recall = matched_poses / len(expected_poses) if expected_poses else 0

        if precision + recall == 0:
            return 0.0

        f1_score = 2 * (precision * recall) / (precision + recall)
        avg_accuracy = total_score / len(expected_poses) if expected_poses else 0

        return (f1_score + avg_accuracy) / 2

    def _calculate_bbox_iou(self, bbox1: Dict, bbox2: Dict) -> float:
        """Calculate Intersection over Union for bounding boxes"""
        if not bbox1 or not bbox2:
            return 0.0

        # Extract coordinates
        x1, y1, w1, h1 = bbox1.get('x', 0), bbox1.get('y', 0), bbox1.get('width', 0), bbox1.get('height', 0)
        x2, y2, w2, h2 = bbox2.get('x', 0), bbox2.get('y', 0), bbox2.get('width', 0), bbox2.get('height', 0)

        # Calculate intersection
        x_left = max(x1, x2)
        y_top = max(y1, y2)
        x_right = min(x1 + w1, x2 + w2)
        y_bottom = min(y1 + h1, y2 + h2)

        if x_right <= x_left or y_bottom <= y_top:
            return 0.0

        intersection = (x_right - x_left) * (y_bottom - y_top)
        union = w1 * h1 + w2 * h2 - intersection

        return intersection / union if union > 0 else 0.0

    def _calculate_landmark_similarity(self, landmarks1: Dict, landmarks2: Dict) -> float:
        """Calculate similarity between pose landmarks"""
        if not landmarks1 or not landmarks2:
            return 0.0

        common_landmarks = set(landmarks1.keys()) & set(landmarks2.keys())
        if not common_landmarks:
            return 0.0

        total_similarity = 0.0
        for landmark in common_landmarks:
            point1 = landmarks1[landmark]
            point2 = landmarks2[landmark]

            # Calculate Euclidean distance (normalized)
            dx = point1.get('x', 0) - point2.get('x', 0)
            dy = point1.get('y', 0) - point2.get('y', 0)
            distance = np.sqrt(dx * dx + dy * dy)

            # Convert distance to similarity (closer = higher similarity)
            similarity = max(0, 1 - distance / 0.2)  # 0.2 is threshold distance
            total_similarity += similarity

        return total_similarity / len(common_landmarks)

    def run_test_case(self, test_case: PoseTestCase) -> ModelPerformanceResult:
        """Run a single test case"""
        logger.info(f"Testing: {test_case.image_path}")

        try:
            detected_poses, latency_ms = self.detect_poses(test_case.image_path)

            accuracy_score = self.calculate_pose_accuracy(
                detected_poses, test_case.expected_poses
            )

            confidence_scores = [
                pose.get('confidence', 0.0) for pose in detected_poses
            ]

            success = (
                accuracy_score >= self.accuracy_threshold and
                latency_ms <= self.latency_threshold_ms
            )

            return ModelPerformanceResult(
                test_case=test_case,
                detected_poses=detected_poses,
                accuracy_score=accuracy_score,
                latency_ms=latency_ms,
                confidence_scores=confidence_scores,
                success=success
            )

        except Exception as e:
            logger.error(f"Test case failed: {e}")
            return ModelPerformanceResult(
                test_case=test_case,
                detected_poses=[],
                accuracy_score=0.0,
                latency_ms=0.0,
                confidence_scores=[],
                success=False,
                error_message=str(e)
            )

    def run_performance_tests(self, test_cases: List[PoseTestCase],
                            max_workers: int = 4) -> List[ModelPerformanceResult]:
        """Run all performance tests in parallel"""
        results = []

        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            future_to_test = {
                executor.submit(self.run_test_case, test_case): test_case
                for test_case in test_cases
            }

            for future in as_completed(future_to_test):
                result = future.result()
                results.append(result)

        return results

    def generate_performance_report(self, results: List[ModelPerformanceResult],
                                  output_path: str):
        """Generate comprehensive performance report"""
        successful_tests = [r for r in results if r.success]
        failed_tests = [r for r in results if not r.success]

        # Calculate aggregate metrics
        total_tests = len(results)
        success_rate = len(successful_tests) / total_tests if total_tests > 0 else 0

        avg_accuracy = np.mean([r.accuracy_score for r in results]) if results else 0
        avg_latency = np.mean([r.latency_ms for r in results]) if results else 0
        avg_confidence = np.mean([
            np.mean(r.confidence_scores) for r in results
            if r.confidence_scores
        ]) if results else 0

        # Breakdown by difficulty and scene type
        difficulty_breakdown = {}
        scene_breakdown = {}

        for result in results:
            difficulty = result.test_case.difficulty_level
            scene = result.test_case.scene_type

            if difficulty not in difficulty_breakdown:
                difficulty_breakdown[difficulty] = {'total': 0, 'passed': 0}
            if scene not in scene_breakdown:
                scene_breakdown[scene] = {'total': 0, 'passed': 0}

            difficulty_breakdown[difficulty]['total'] += 1
            scene_breakdown[scene]['total'] += 1

            if result.success:
                difficulty_breakdown[difficulty]['passed'] += 1
                scene_breakdown[scene]['passed'] += 1

        report = {
            "summary": {
                "total_tests": total_tests,
                "successful_tests": len(successful_tests),
                "failed_tests": len(failed_tests),
                "success_rate": round(success_rate * 100, 2),
                "average_accuracy": round(avg_accuracy * 100, 2),
                "average_latency_ms": round(avg_latency, 2),
                "average_confidence": round(avg_confidence * 100, 2),
                "accuracy_threshold": self.accuracy_threshold * 100,
                "latency_threshold_ms": self.latency_threshold_ms
            },
            "breakdown": {
                "by_difficulty": difficulty_breakdown,
                "by_scene_type": scene_breakdown
            },
            "failed_tests": [
                {
                    "image": result.test_case.image_path,
                    "difficulty": result.test_case.difficulty_level,
                    "scene_type": result.test_case.scene_type,
                    "accuracy": round(result.accuracy_score * 100, 2),
                    "latency_ms": round(result.latency_ms, 2),
                    "error": result.error_message
                }
                for result in failed_tests
            ],
            "detailed_results": [
                {
                    "image": result.test_case.image_path,
                    "difficulty": result.test_case.difficulty_level,
                    "scene_type": result.test_case.scene_type,
                    "accuracy": round(result.accuracy_score * 100, 2),
                    "latency_ms": round(result.latency_ms, 2),
                    "confidence_scores": result.confidence_scores,
                    "success": result.success,
                    "detected_poses_count": len(result.detected_poses),
                    "expected_poses_count": len(result.test_case.expected_poses)
                }
                for result in results
            ]
        }

        # Write report
        with open(output_path, 'w') as f:
            json.dump(report, f, indent=2)

        # Print summary
        self._print_summary(report)

        return report

    def _print_summary(self, report: Dict):
        """Print AI model performance summary"""
        summary = report["summary"]

        print("\n🤖 AI Model Performance Summary")
        print("=" * 50)
        print(f"📊 Total Tests: {summary['total_tests']}")
        print(f"✅ Success Rate: {summary['success_rate']}%")
        print(f"🎯 Average Accuracy: {summary['average_accuracy']}%")
        print(f"⚡ Average Latency: {summary['average_latency_ms']}ms")
        print(f"🔍 Average Confidence: {summary['average_confidence']}%")

        print(f"\n📋 Thresholds:")
        print(f"  Accuracy: ≥{summary['accuracy_threshold']}%")
        print(f"  Latency: ≤{summary['latency_threshold_ms']}ms")

        # Show breakdown
        if report["breakdown"]["by_difficulty"]:
            print(f"\n📊 Results by Difficulty:")
            for difficulty, stats in report["breakdown"]["by_difficulty"].items():
                success_rate = (stats['passed'] / stats['total'] * 100) if stats['total'] > 0 else 0
                print(f"  {difficulty.capitalize()}: {stats['passed']}/{stats['total']} ({success_rate:.1f}%)")

def main():
    parser = argparse.ArgumentParser(description='Validate AI model performance')
    parser.add_argument('--test-data', required=True,
                       help='Directory containing test data and manifest')
    parser.add_argument('--accuracy-threshold', type=float, default=0.85,
                       help='Minimum accuracy threshold (0-1, default: 0.85)')
    parser.add_argument('--latency-threshold', type=float, default=200,
                       help='Maximum latency threshold in ms (default: 200)')
    parser.add_argument('--output', default='ai-model-performance.json',
                       help='Output report file path')
    parser.add_argument('--max-workers', type=int, default=4,
                       help='Maximum parallel workers (default: 4)')

    args = parser.parse_args()

    # Get API key from environment
    gemini_api_key = os.getenv('GEMINI_API_KEY')
    if not gemini_api_key:
        logger.error("GEMINI_API_KEY environment variable not set")
        sys.exit(1)

    # Initialize validator
    validator = PoseModelValidator(
        gemini_api_key=gemini_api_key,
        accuracy_threshold=args.accuracy_threshold,
        latency_threshold_ms=args.latency_threshold
    )

    try:
        # Load test cases
        test_cases = validator.load_test_cases(args.test_data)

        if not test_cases:
            logger.error("No valid test cases found")
            sys.exit(1)

        # Run performance tests
        logger.info(f"Running {len(test_cases)} AI model performance tests...")
        results = validator.run_performance_tests(test_cases, args.max_workers)

        # Generate report
        report = validator.generate_performance_report(results, args.output)

        # Check if tests passed
        if report["summary"]["success_rate"] < 100:
            logger.warning(f"Some AI model tests failed: {report['summary']['failed_tests']} failures")
            sys.exit(1)

        logger.info(f"All AI model tests passed! Report saved to {args.output}")

    except Exception as e:
        logger.error(f"AI model validation failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()