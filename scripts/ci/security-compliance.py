#!/usr/bin/env python3
"""
Security and Compliance Automation Script for Pose Coach Android CI/CD Pipeline
Comprehensive security scanning, compliance validation, and privacy protection
"""

import argparse
import json
import logging
import os
import sys
import subprocess
import hashlib
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
import requests
import zipfile
import xml.etree.ElementTree as ET

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class SecurityIssue:
    """Security issue data structure"""
    category: str
    severity: str  # critical, high, medium, low
    title: str
    description: str
    file_path: str
    line_number: int
    cwe_id: Optional[str] = None
    recommendation: Optional[str] = None

@dataclass
class ComplianceResult:
    """Compliance validation result"""
    framework: str  # GDPR, CCPA, COPPA, etc.
    status: str    # compliant, non-compliant, warning
    issues: List[str]
    recommendations: List[str]

class SecurityComplianceValidator:
    """Comprehensive security and compliance validation"""

    def __init__(self):
        self.security_issues = []
        self.compliance_results = []
        self.privacy_scan_results = {}

    def run_comprehensive_security_scan(self, apk_path: str, source_dir: str) -> Dict:
        """Run comprehensive security analysis"""
        logger.info("Starting comprehensive security scan...")

        results = {
            "static_analysis": self._run_static_analysis(source_dir),
            "apk_analysis": self._analyze_apk_security(apk_path),
            "dependency_scan": self._scan_dependencies(source_dir),
            "privacy_scan": self._scan_privacy_compliance(source_dir),
            "api_security": self._validate_api_security(source_dir),
            "data_protection": self._validate_data_protection(source_dir),
            "authentication": self._validate_authentication(source_dir),
            "network_security": self._validate_network_security(source_dir)
        }

        return results

    def _run_static_analysis(self, source_dir: str) -> Dict:
        """Run static code analysis for security vulnerabilities"""
        logger.info("Running static security analysis...")

        issues = []

        # Check for hardcoded secrets
        issues.extend(self._scan_hardcoded_secrets(source_dir))

        # Check for SQL injection vulnerabilities
        issues.extend(self._scan_sql_injection(source_dir))

        # Check for insecure cryptography
        issues.extend(self._scan_crypto_issues(source_dir))

        # Check for insecure network usage
        issues.extend(self._scan_network_security_issues(source_dir))

        # Check for improper input validation
        issues.extend(self._scan_input_validation(source_dir))

        return {
            "total_issues": len(issues),
            "critical_issues": len([i for i in issues if i.severity == "critical"]),
            "high_issues": len([i for i in issues if i.severity == "high"]),
            "medium_issues": len([i for i in issues if i.severity == "medium"]),
            "low_issues": len([i for i in issues if i.severity == "low"]),
            "issues": [self._issue_to_dict(issue) for issue in issues]
        }

    def _scan_hardcoded_secrets(self, source_dir: str) -> List[SecurityIssue]:
        """Scan for hardcoded secrets and credentials"""
        issues = []
        secret_patterns = [
            (r"password\s*=\s*[\"'][^\"']+[\"']", "Hardcoded password"),
            (r"api_key\s*=\s*[\"'][^\"']+[\"']", "Hardcoded API key"),
            (r"secret\s*=\s*[\"'][^\"']+[\"']", "Hardcoded secret"),
            (r"token\s*=\s*[\"'][^\"']+[\"']", "Hardcoded token"),
            (r"[A-Za-z0-9+/]{40,}", "Potential base64 encoded secret"),
            (r"[A-Fa-f0-9]{32,}", "Potential hexadecimal secret"),
            (r"sk_[a-zA-Z0-9]{24,}", "Stripe secret key"),
            (r"pk_[a-zA-Z0-9]{24,}", "Stripe public key"),
            (r"AIza[0-9A-Za-z\\-_]{35}", "Google API key"),
            (r"ya29\\.[0-9A-Za-z\\-_]+", "Google OAuth access token")
        ]

        import re

        for root, dirs, files in os.walk(source_dir):
            for file in files:
                if file.endswith(('.kt', '.java', '.xml', '.properties', '.json')):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                            content = f.read()
                            lines = content.split('\n')

                            for line_num, line in enumerate(lines, 1):
                                for pattern, description in secret_patterns:
                                    if re.search(pattern, line, re.IGNORECASE):
                                        issues.append(SecurityIssue(
                                            category="secrets",
                                            severity="critical",
                                            title=description,
                                            description=f"Potential {description.lower()} found in source code",
                                            file_path=file_path,
                                            line_number=line_num,
                                            cwe_id="CWE-798",
                                            recommendation="Use environment variables or secure configuration management"
                                        ))
                    except Exception as e:
                        logger.warning(f"Could not scan file {file_path}: {e}")

        return issues

    def _scan_sql_injection(self, source_dir: str) -> List[SecurityIssue]:
        """Scan for SQL injection vulnerabilities"""
        issues = []
        vulnerable_patterns = [
            (r"query\s*\(\s*[\"'][^\"']*\$\{[^}]+\}[^\"']*[\"']", "String interpolation in SQL query"),
            (r"rawQuery\s*\(\s*[\"'][^\"']*\+[^\"']*[\"']", "String concatenation in SQL query"),
            (r"execSQL\s*\(\s*[\"'][^\"']*\+[^\"']*[\"']", "String concatenation in SQL execution"),
            (r"\.query\([^)]*\+[^)]*\)", "Potential SQL injection in query method")
        ]

        import re

        for root, dirs, files in os.walk(source_dir):
            for file in files:
                if file.endswith(('.kt', '.java')):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                            content = f.read()
                            lines = content.split('\n')

                            for line_num, line in enumerate(lines, 1):
                                for pattern, description in vulnerable_patterns:
                                    if re.search(pattern, line):
                                        issues.append(SecurityIssue(
                                            category="injection",
                                            severity="high",
                                            title="Potential SQL Injection",
                                            description=f"{description}: {line.strip()}",
                                            file_path=file_path,
                                            line_number=line_num,
                                            cwe_id="CWE-89",
                                            recommendation="Use parameterized queries or prepared statements"
                                        ))
                    except Exception as e:
                        logger.warning(f"Could not scan file {file_path}: {e}")

        return issues

    def _scan_crypto_issues(self, source_dir: str) -> List[SecurityIssue]:
        """Scan for cryptographic vulnerabilities"""
        issues = []
        crypto_issues = [
            (r"DES|TripleDES", "Weak encryption algorithm", "high"),
            (r"MD5|SHA1", "Weak hashing algorithm", "medium"),
            (r"RSA.*1024", "Weak RSA key size", "high"),
            (r"Random\(\)", "Weak random number generation", "medium"),
            (r"TrustAllCerts|TrustManager", "Insecure certificate validation", "critical"),
            (r"setHostnameVerifier.*ALLOW_ALL", "Disabled hostname verification", "critical"),
            (r"HttpsURLConnection.*setDefaultHostnameVerifier", "Modified hostname verification", "high")
        ]

        import re

        for root, dirs, files in os.walk(source_dir):
            for file in files:
                if file.endswith(('.kt', '.java')):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                            content = f.read()
                            lines = content.split('\n')

                            for line_num, line in enumerate(lines, 1):
                                for pattern, description, severity in crypto_issues:
                                    if re.search(pattern, line, re.IGNORECASE):
                                        issues.append(SecurityIssue(
                                            category="cryptography",
                                            severity=severity,
                                            title="Cryptographic Vulnerability",
                                            description=f"{description}: {line.strip()}",
                                            file_path=file_path,
                                            line_number=line_num,
                                            cwe_id="CWE-327",
                                            recommendation="Use strong, modern cryptographic algorithms"
                                        ))
                    except Exception as e:
                        logger.warning(f"Could not scan file {file_path}: {e}")

        return issues

    def _scan_network_security_issues(self, source_dir: str) -> List[SecurityIssue]:
        """Scan for network security issues"""
        issues = []
        network_issues = [
            (r"http://", "Insecure HTTP usage", "medium"),
            (r"allowBackup.*true", "Backup allowed", "low"),
            (r"debuggable.*true", "Debug mode enabled", "medium"),
            (r"usesCleartextTraffic.*true", "Cleartext traffic allowed", "high"),
            (r"networkSecurityConfig", "Network security config", "info")
        ]

        import re

        for root, dirs, files in os.walk(source_dir):
            for file in files:
                if file.endswith(('.kt', '.java', '.xml')):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                            content = f.read()
                            lines = content.split('\n')

                            for line_num, line in enumerate(lines, 1):
                                for pattern, description, severity in network_issues:
                                    if re.search(pattern, line, re.IGNORECASE):
                                        if severity != "info":  # Skip info-level findings
                                            issues.append(SecurityIssue(
                                                category="network",
                                                severity=severity,
                                                title="Network Security Issue",
                                                description=f"{description}: {line.strip()}",
                                                file_path=file_path,
                                                line_number=line_num,
                                                cwe_id="CWE-319",
                                                recommendation="Use HTTPS and secure network configurations"
                                            ))
                    except Exception as e:
                        logger.warning(f"Could not scan file {file_path}: {e}")

        return issues

    def _scan_input_validation(self, source_dir: str) -> List[SecurityIssue]:
        """Scan for input validation issues"""
        issues = []
        validation_issues = [
            (r"Intent\.getStringExtra\([^)]+\)(?!\s*\.let|\s*\?\.)", "Unchecked intent data", "medium"),
            (r"getSharedPreferences\([^)]+\)\.getString\([^)]+\)(?!\s*\?\.)", "Unchecked preferences data", "low"),
            (r"Uri\.parse\([^)]+\)(?!\s*\.let|\s*\?\.)", "Unchecked URI parsing", "medium"),
            (r"JSON\.parse\([^)]+\)(?!\s*try|\s*catch)", "Unchecked JSON parsing", "medium")
        ]

        import re

        for root, dirs, files in os.walk(source_dir):
            for file in files:
                if file.endswith(('.kt', '.java')):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                            content = f.read()
                            lines = content.split('\n')

                            for line_num, line in enumerate(lines, 1):
                                for pattern, description, severity in validation_issues:
                                    if re.search(pattern, line):
                                        issues.append(SecurityIssue(
                                            category="input_validation",
                                            severity=severity,
                                            title="Input Validation Issue",
                                            description=f"{description}: {line.strip()}",
                                            file_path=file_path,
                                            line_number=line_num,
                                            cwe_id="CWE-20",
                                            recommendation="Implement proper input validation and sanitization"
                                        ))
                    except Exception as e:
                        logger.warning(f"Could not scan file {file_path}: {e}")

        return issues

    def _analyze_apk_security(self, apk_path: str) -> Dict:
        """Analyze APK for security issues"""
        logger.info("Analyzing APK security...")

        if not os.path.exists(apk_path):
            logger.warning(f"APK file not found: {apk_path}")
            return {"error": "APK file not found"}

        results = {
            "file_info": self._get_apk_info(apk_path),
            "permissions": self._analyze_permissions(apk_path),
            "manifest_security": self._analyze_manifest_security(apk_path),
            "certificate": self._analyze_certificate(apk_path),
            "binary_analysis": self._analyze_binary_security(apk_path)
        }

        return results

    def _get_apk_info(self, apk_path: str) -> Dict:
        """Get basic APK information"""
        try:
            file_size = os.path.getsize(apk_path)

            # Calculate checksums
            with open(apk_path, 'rb') as f:
                content = f.read()
                md5_hash = hashlib.md5(content).hexdigest()
                sha256_hash = hashlib.sha256(content).hexdigest()

            return {
                "file_size": file_size,
                "md5": md5_hash,
                "sha256": sha256_hash
            }
        except Exception as e:
            logger.error(f"Failed to get APK info: {e}")
            return {}

    def _analyze_permissions(self, apk_path: str) -> Dict:
        """Analyze APK permissions for security risks"""
        try:
            # Extract manifest using aapt
            result = subprocess.run(
                ["aapt", "dump", "permissions", apk_path],
                capture_output=True, text=True, timeout=60
            )

            if result.returncode != 0:
                logger.warning("Failed to extract permissions with aapt")
                return {}

            permissions = []
            dangerous_permissions = []

            dangerous_perms = [
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.READ_CONTACTS",
                "android.permission.READ_SMS",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE"
            ]

            for line in result.stdout.split('\n'):
                if 'uses-permission:' in line:
                    perm = line.split("'")[1] if "'" in line else ""
                    if perm:
                        permissions.append(perm)
                        if perm in dangerous_perms:
                            dangerous_permissions.append(perm)

            return {
                "total_permissions": len(permissions),
                "dangerous_permissions": dangerous_permissions,
                "all_permissions": permissions,
                "risk_score": len(dangerous_permissions) / max(len(permissions), 1)
            }

        except Exception as e:
            logger.error(f"Failed to analyze permissions: {e}")
            return {}

    def _analyze_manifest_security(self, apk_path: str) -> Dict:
        """Analyze Android manifest for security configurations"""
        try:
            # Extract and parse AndroidManifest.xml
            result = subprocess.run(
                ["aapt", "dump", "xmltree", apk_path, "AndroidManifest.xml"],
                capture_output=True, text=True, timeout=60
            )

            if result.returncode != 0:
                return {}

            manifest_content = result.stdout
            security_issues = []

            # Check for security configurations
            if "android:allowBackup=true" in manifest_content:
                security_issues.append("Backup is allowed")

            if "android:debuggable=true" in manifest_content:
                security_issues.append("Debug mode is enabled")

            if "android:usesCleartextTraffic=true" in manifest_content:
                security_issues.append("Cleartext traffic is allowed")

            # Check for exported components
            exported_components = manifest_content.count("android:exported=true")

            return {
                "security_issues": security_issues,
                "exported_components": exported_components,
                "manifest_size": len(manifest_content)
            }

        except Exception as e:
            logger.error(f"Failed to analyze manifest: {e}")
            return {}

    def _analyze_certificate(self, apk_path: str) -> Dict:
        """Analyze APK signing certificate"""
        try:
            # Extract certificate info using keytool
            result = subprocess.run(
                ["jarsigner", "-verify", "-verbose", "-certs", apk_path],
                capture_output=True, text=True, timeout=60
            )

            cert_info = {
                "is_signed": "jar verified" in result.stdout.lower(),
                "signature_algorithm": "",
                "key_size": 0,
                "valid_until": "",
                "issuer": ""
            }

            # Parse certificate details
            if cert_info["is_signed"]:
                lines = result.stdout.split('\n')
                for line in lines:
                    if "Signature algorithm" in line:
                        cert_info["signature_algorithm"] = line.split(":")[1].strip()
                    elif "Key size" in line:
                        cert_info["key_size"] = int(line.split(":")[1].strip().split()[0])

            return cert_info

        except Exception as e:
            logger.error(f"Failed to analyze certificate: {e}")
            return {}

    def _analyze_binary_security(self, apk_path: str) -> Dict:
        """Analyze binary security features"""
        try:
            # Extract and analyze native libraries
            with zipfile.ZipFile(apk_path, 'r') as apk_zip:
                lib_files = [f for f in apk_zip.namelist() if f.startswith('lib/')]

                security_features = {
                    "has_native_libraries": len(lib_files) > 0,
                    "library_count": len(lib_files),
                    "architectures": list(set(f.split('/')[1] for f in lib_files if '/' in f)),
                    "obfuscated": self._check_obfuscation(apk_zip)
                }

            return security_features

        except Exception as e:
            logger.error(f"Failed to analyze binary security: {e}")
            return {}

    def _check_obfuscation(self, apk_zip: zipfile.ZipFile) -> bool:
        """Check if the APK appears to be obfuscated"""
        try:
            # Look for ProGuard/R8 mapping file
            return 'META-INF/proguard/' in apk_zip.namelist()
        except:
            return False

    def _scan_dependencies(self, source_dir: str) -> Dict:
        """Scan dependencies for known vulnerabilities"""
        logger.info("Scanning dependencies for vulnerabilities...")

        try:
            # Run OWASP Dependency Check
            result = subprocess.run([
                "dependency-check",
                "--project", "Pose Coach Android",
                "--scan", source_dir,
                "--format", "JSON",
                "--out", "dependency-check-report"
            ], capture_output=True, text=True, timeout=300)

            if os.path.exists("dependency-check-report/dependency-check-report.json"):
                with open("dependency-check-report/dependency-check-report.json", 'r') as f:
                    report = json.load(f)

                vulnerabilities = []
                for dependency in report.get("dependencies", []):
                    for vuln in dependency.get("vulnerabilities", []):
                        vulnerabilities.append({
                            "name": dependency.get("fileName", "Unknown"),
                            "cve": vuln.get("name", ""),
                            "severity": vuln.get("severity", ""),
                            "description": vuln.get("description", "")
                        })

                return {
                    "total_dependencies": len(report.get("dependencies", [])),
                    "vulnerable_dependencies": len([d for d in report.get("dependencies", []) if d.get("vulnerabilities")]),
                    "total_vulnerabilities": len(vulnerabilities),
                    "vulnerabilities": vulnerabilities
                }

        except Exception as e:
            logger.error(f"Dependency scan failed: {e}")

        return {"error": "Dependency scan failed"}

    def _scan_privacy_compliance(self, source_dir: str) -> Dict:
        """Scan for privacy compliance issues"""
        logger.info("Scanning for privacy compliance...")

        compliance_results = {
            "gdpr": self._check_gdpr_compliance(source_dir),
            "ccpa": self._check_ccpa_compliance(source_dir),
            "coppa": self._check_coppa_compliance(source_dir),
            "data_collection": self._analyze_data_collection(source_dir),
            "privacy_policy": self._check_privacy_policy(source_dir)
        }

        return compliance_results

    def _check_gdpr_compliance(self, source_dir: str) -> ComplianceResult:
        """Check GDPR compliance"""
        issues = []
        recommendations = []

        # Check for consent management
        consent_patterns = ["consent", "gdpr", "cookie", "tracking"]
        consent_found = False

        for root, dirs, files in os.walk(source_dir):
            for file in files:
                if file.endswith(('.kt', '.java')):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                            content = f.read().lower()
                            if any(pattern in content for pattern in consent_patterns):
                                consent_found = True
                                break
                    except:
                        continue

        if not consent_found:
            issues.append("No consent management implementation found")
            recommendations.append("Implement user consent collection and management")

        # Check for data subject rights
        rights_patterns = ["delete", "export", "portability", "rectification"]
        rights_found = any(self._search_in_source(source_dir, pattern) for pattern in rights_patterns)

        if not rights_found:
            issues.append("No data subject rights implementation found")
            recommendations.append("Implement data subject rights (access, rectification, erasure, portability)")

        status = "compliant" if not issues else "non-compliant"
        return ComplianceResult("GDPR", status, issues, recommendations)

    def _check_ccpa_compliance(self, source_dir: str) -> ComplianceResult:
        """Check CCPA compliance"""
        issues = []
        recommendations = []

        # Check for "Do Not Sell" implementation
        ccpa_patterns = ["do not sell", "ccpa", "california privacy"]
        ccpa_found = any(self._search_in_source(source_dir, pattern) for pattern in ccpa_patterns)

        if not ccpa_found:
            issues.append("No CCPA 'Do Not Sell' implementation found")
            recommendations.append("Implement CCPA compliance including 'Do Not Sell' option")

        status = "compliant" if not issues else "warning"
        return ComplianceResult("CCPA", status, issues, recommendations)

    def _check_coppa_compliance(self, source_dir: str) -> ComplianceResult:
        """Check COPPA compliance"""
        issues = []
        recommendations = []

        # Check for age verification
        age_patterns = ["age", "birth", "parental", "13", "child"]
        age_verification = any(self._search_in_source(source_dir, pattern) for pattern in age_patterns)

        if not age_verification:
            issues.append("No age verification implementation found")
            recommendations.append("Implement age verification and parental consent for users under 13")

        status = "warning" if issues else "compliant"
        return ComplianceResult("COPPA", status, issues, recommendations)

    def _search_in_source(self, source_dir: str, pattern: str) -> bool:
        """Search for pattern in source code"""
        import re
        for root, dirs, files in os.walk(source_dir):
            for file in files:
                if file.endswith(('.kt', '.java', '.xml')):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                            content = f.read()
                            if re.search(pattern, content, re.IGNORECASE):
                                return True
                    except:
                        continue
        return False

    def _analyze_data_collection(self, source_dir: str) -> Dict:
        """Analyze data collection practices"""
        data_types = {
            "location": ["location", "gps", "latitude", "longitude"],
            "personal": ["name", "email", "phone", "address"],
            "biometric": ["fingerprint", "face", "voice", "pose", "gesture"],
            "device": ["device_id", "imei", "android_id", "advertising_id"],
            "usage": ["analytics", "telemetry", "usage", "behavior"]
        }

        collected_data = {}
        for data_type, patterns in data_types.items():
            collected_data[data_type] = any(
                self._search_in_source(source_dir, pattern) for pattern in patterns
            )

        return {
            "data_types_collected": [k for k, v in collected_data.items() if v],
            "high_risk_data": [k for k, v in collected_data.items() if v and k in ["biometric", "location", "personal"]],
            "collection_summary": collected_data
        }

    def _check_privacy_policy(self, source_dir: str) -> Dict:
        """Check for privacy policy implementation"""
        privacy_patterns = ["privacy policy", "privacy notice", "data policy"]
        policy_found = any(self._search_in_source(source_dir, pattern) for pattern in privacy_patterns)

        return {
            "has_privacy_policy_reference": policy_found,
            "recommendation": "Ensure privacy policy is accessible and up-to-date" if policy_found else "Add privacy policy reference in the app"
        }

    def _validate_api_security(self, source_dir: str) -> Dict:
        """Validate API security implementations"""
        logger.info("Validating API security...")

        api_security = {
            "authentication": self._check_api_authentication(source_dir),
            "encryption": self._check_api_encryption(source_dir),
            "input_validation": self._check_api_input_validation(source_dir),
            "rate_limiting": self._check_rate_limiting(source_dir)
        }

        return api_security

    def _check_api_authentication(self, source_dir: str) -> Dict:
        """Check API authentication implementation"""
        auth_patterns = ["authorization", "bearer", "token", "oauth", "jwt"]
        auth_found = any(self._search_in_source(source_dir, pattern) for pattern in auth_patterns)

        return {
            "has_authentication": auth_found,
            "recommendation": "Ensure proper API authentication is implemented" if not auth_found else "Review authentication implementation"
        }

    def _check_api_encryption(self, source_dir: str) -> Dict:
        """Check API encryption usage"""
        https_usage = self._search_in_source(source_dir, "https://")
        tls_patterns = ["tls", "ssl", "secure"]
        tls_found = any(self._search_in_source(source_dir, pattern) for pattern in tls_patterns)

        return {
            "uses_https": https_usage,
            "has_tls_config": tls_found,
            "recommendation": "Ensure all API communications use HTTPS/TLS"
        }

    def _check_api_input_validation(self, source_dir: str) -> Dict:
        """Check API input validation"""
        validation_patterns = ["validate", "sanitize", "escape", "filter"]
        validation_found = any(self._search_in_source(source_dir, pattern) for pattern in validation_patterns)

        return {
            "has_input_validation": validation_found,
            "recommendation": "Implement comprehensive input validation for all API endpoints"
        }

    def _check_rate_limiting(self, source_dir: str) -> Dict:
        """Check for rate limiting implementation"""
        rate_patterns = ["rate limit", "throttle", "quota", "circuit breaker"]
        rate_limiting = any(self._search_in_source(source_dir, pattern) for pattern in rate_patterns)

        return {
            "has_rate_limiting": rate_limiting,
            "recommendation": "Implement rate limiting to prevent abuse"
        }

    def _validate_data_protection(self, source_dir: str) -> Dict:
        """Validate data protection measures"""
        logger.info("Validating data protection...")

        return {
            "encryption_at_rest": self._check_data_encryption(source_dir),
            "secure_storage": self._check_secure_storage(source_dir),
            "data_minimization": self._check_data_minimization(source_dir),
            "retention_policy": self._check_retention_policy(source_dir)
        }

    def _check_data_encryption(self, source_dir: str) -> Dict:
        """Check data encryption implementation"""
        encryption_patterns = ["encrypt", "aes", "rsa", "cipher"]
        encryption_found = any(self._search_in_source(source_dir, pattern) for pattern in encryption_patterns)

        return {
            "has_encryption": encryption_found,
            "recommendation": "Implement strong encryption for sensitive data"
        }

    def _check_secure_storage(self, source_dir: str) -> Dict:
        """Check secure storage implementation"""
        secure_patterns = ["keystore", "encrypted_shared_prefs", "security-crypto"]
        secure_storage = any(self._search_in_source(source_dir, pattern) for pattern in secure_patterns)

        return {
            "uses_secure_storage": secure_storage,
            "recommendation": "Use Android Keystore and EncryptedSharedPreferences for sensitive data"
        }

    def _check_data_minimization(self, source_dir: str) -> Dict:
        """Check data minimization practices"""
        # This is a simplified check - in practice, this would be more comprehensive
        return {
            "practices_minimization": True,  # Placeholder
            "recommendation": "Collect only necessary data for app functionality"
        }

    def _check_retention_policy(self, source_dir: str) -> Dict:
        """Check data retention policy implementation"""
        retention_patterns = ["delete", "purge", "cleanup", "retention"]
        retention_found = any(self._search_in_source(source_dir, pattern) for pattern in retention_patterns)

        return {
            "has_retention_policy": retention_found,
            "recommendation": "Implement data retention and deletion policies"
        }

    def _validate_authentication(self, source_dir: str) -> Dict:
        """Validate authentication security"""
        logger.info("Validating authentication security...")

        return {
            "biometric_auth": self._check_biometric_auth(source_dir),
            "session_management": self._check_session_management(source_dir),
            "password_policy": self._check_password_policy(source_dir),
            "multi_factor": self._check_multi_factor_auth(source_dir)
        }

    def _check_biometric_auth(self, source_dir: str) -> Dict:
        """Check biometric authentication implementation"""
        biometric_patterns = ["biometric", "fingerprint", "faceauth", "biometricprompt"]
        biometric_found = any(self._search_in_source(source_dir, pattern) for pattern in biometric_patterns)

        return {
            "supports_biometric": biometric_found,
            "recommendation": "Implement biometric authentication for enhanced security"
        }

    def _check_session_management(self, source_dir: str) -> Dict:
        """Check session management implementation"""
        session_patterns = ["session", "logout", "timeout", "expiry"]
        session_mgmt = any(self._search_in_source(source_dir, pattern) for pattern in session_patterns)

        return {
            "has_session_management": session_mgmt,
            "recommendation": "Implement proper session management with timeouts"
        }

    def _check_password_policy(self, source_dir: str) -> Dict:
        """Check password policy implementation"""
        password_patterns = ["password.*complexity", "password.*strength", "password.*policy"]
        policy_found = any(self._search_in_source(source_dir, pattern) for pattern in password_patterns)

        return {
            "has_password_policy": policy_found,
            "recommendation": "Implement strong password policy enforcement"
        }

    def _check_multi_factor_auth(self, source_dir: str) -> Dict:
        """Check multi-factor authentication"""
        mfa_patterns = ["mfa", "2fa", "multi.factor", "two.factor", "otp"]
        mfa_found = any(self._search_in_source(source_dir, pattern) for pattern in mfa_patterns)

        return {
            "supports_mfa": mfa_found,
            "recommendation": "Consider implementing multi-factor authentication"
        }

    def _validate_network_security(self, source_dir: str) -> Dict:
        """Validate network security configurations"""
        logger.info("Validating network security...")

        return {
            "certificate_pinning": self._check_cert_pinning(source_dir),
            "network_config": self._check_network_security_config(source_dir),
            "proxy_detection": self._check_proxy_detection(source_dir)
        }

    def _check_cert_pinning(self, source_dir: str) -> Dict:
        """Check certificate pinning implementation"""
        pinning_patterns = ["certificate.*pinning", "pin.*certificate", "trustkit"]
        pinning_found = any(self._search_in_source(source_dir, pattern) for pattern in pinning_patterns)

        return {
            "has_cert_pinning": pinning_found,
            "recommendation": "Implement certificate pinning for critical connections"
        }

    def _check_network_security_config(self, source_dir: str) -> Dict:
        """Check network security configuration"""
        config_found = self._search_in_source(source_dir, "network_security_config")

        return {
            "has_network_config": config_found,
            "recommendation": "Use Android Network Security Configuration for additional protection"
        }

    def _check_proxy_detection(self, source_dir: str) -> Dict:
        """Check proxy detection implementation"""
        proxy_patterns = ["proxy", "vpn.*detect", "network.*detection"]
        proxy_detection = any(self._search_in_source(source_dir, pattern) for pattern in proxy_patterns)

        return {
            "detects_proxy": proxy_detection,
            "recommendation": "Consider implementing proxy/VPN detection for sensitive operations"
        }

    def _issue_to_dict(self, issue: SecurityIssue) -> Dict:
        """Convert SecurityIssue to dictionary"""
        return {
            "category": issue.category,
            "severity": issue.severity,
            "title": issue.title,
            "description": issue.description,
            "file_path": issue.file_path,
            "line_number": issue.line_number,
            "cwe_id": issue.cwe_id,
            "recommendation": issue.recommendation
        }

    def generate_security_report(self, results: Dict, output_path: str):
        """Generate comprehensive security and compliance report"""

        # Calculate overall security score
        total_issues = results.get("static_analysis", {}).get("total_issues", 0)
        critical_issues = results.get("static_analysis", {}).get("critical_issues", 0)
        high_issues = results.get("static_analysis", {}).get("high_issues", 0)

        security_score = max(0, 100 - (critical_issues * 20 + high_issues * 10 + (total_issues - critical_issues - high_issues) * 2))

        report = {
            "summary": {
                "overall_security_score": security_score,
                "total_security_issues": total_issues,
                "critical_issues": critical_issues,
                "high_issues": high_issues,
                "compliance_status": self._calculate_compliance_status(results),
                "timestamp": time.time()
            },
            "detailed_results": results,
            "recommendations": self._generate_recommendations(results),
            "compliance_summary": self._generate_compliance_summary(results)
        }

        with open(output_path, 'w') as f:
            json.dump(report, f, indent=2)

        # Print summary
        self._print_security_summary(report)

        return report

    def _calculate_compliance_status(self, results: Dict) -> str:
        """Calculate overall compliance status"""
        privacy_results = results.get("privacy_scan", {})
        if not privacy_results:
            return "unknown"

        compliance_frameworks = ["gdpr", "ccpa", "coppa"]
        compliant_count = 0

        for framework in compliance_frameworks:
            framework_result = privacy_results.get(framework, {})
            if isinstance(framework_result, dict) and framework_result.get("status") == "compliant":
                compliant_count += 1

        if compliant_count == len(compliance_frameworks):
            return "fully_compliant"
        elif compliant_count > 0:
            return "partially_compliant"
        else:
            return "non_compliant"

    def _generate_recommendations(self, results: Dict) -> List[str]:
        """Generate security recommendations"""
        recommendations = []

        static_analysis = results.get("static_analysis", {})
        if static_analysis.get("critical_issues", 0) > 0:
            recommendations.append("Address all critical security vulnerabilities immediately")

        if static_analysis.get("high_issues", 0) > 0:
            recommendations.append("Resolve high-severity security issues before deployment")

        # Add specific recommendations based on findings
        if results.get("api_security", {}).get("authentication", {}).get("has_authentication") is False:
            recommendations.append("Implement proper API authentication")

        if results.get("data_protection", {}).get("encryption_at_rest", {}).get("has_encryption") is False:
            recommendations.append("Implement data encryption for sensitive information")

        return recommendations

    def _generate_compliance_summary(self, results: Dict) -> Dict:
        """Generate compliance summary"""
        privacy_scan = results.get("privacy_scan", {})

        return {
            "gdpr_status": privacy_scan.get("gdpr", {}).get("status", "unknown"),
            "ccpa_status": privacy_scan.get("ccpa", {}).get("status", "unknown"),
            "coppa_status": privacy_scan.get("coppa", {}).get("status", "unknown"),
            "data_types_collected": privacy_scan.get("data_collection", {}).get("data_types_collected", []),
            "high_risk_data": privacy_scan.get("data_collection", {}).get("high_risk_data", [])
        }

    def _print_security_summary(self, report: Dict):
        """Print security analysis summary"""
        summary = report["summary"]

        print("\n🔒 Security & Compliance Analysis Summary")
        print("=" * 60)
        print(f"🏆 Overall Security Score: {summary['overall_security_score']}/100")
        print(f"🚨 Total Security Issues: {summary['total_security_issues']}")
        print(f"🔴 Critical Issues: {summary['critical_issues']}")
        print(f"🟡 High Severity Issues: {summary['high_issues']}")
        print(f"📋 Compliance Status: {summary['compliance_status'].replace('_', ' ').title()}")

        if report.get("recommendations"):
            print("\n📋 Key Recommendations:")
            for i, rec in enumerate(report["recommendations"][:5], 1):
                print(f"  {i}. {rec}")

        compliance_summary = report.get("compliance_summary", {})
        if compliance_summary:
            print(f"\n🌐 Privacy Compliance:")
            print(f"  GDPR: {compliance_summary.get('gdpr_status', 'unknown').title()}")
            print(f"  CCPA: {compliance_summary.get('ccpa_status', 'unknown').title()}")
            print(f"  COPPA: {compliance_summary.get('coppa_status', 'unknown').title()}")

def main():
    parser = argparse.ArgumentParser(description='Run comprehensive security and compliance analysis')
    parser.add_argument('--apk-path', required=True,
                       help='Path to APK file for analysis')
    parser.add_argument('--source-dir', required=True,
                       help='Path to source code directory')
    parser.add_argument('--output', default='security-compliance-report.json',
                       help='Output report file path')
    parser.add_argument('--fail-on-critical', action='store_true',
                       help='Exit with non-zero code if critical issues found')

    args = parser.parse_args()

    validator = SecurityComplianceValidator()

    try:
        logger.info("Starting comprehensive security and compliance analysis...")

        # Run comprehensive security scan
        results = validator.run_comprehensive_security_scan(
            args.apk_path, args.source_dir
        )

        # Generate report
        report = validator.generate_security_report(results, args.output)

        # Check for critical issues
        if args.fail_on_critical and report["summary"]["critical_issues"] > 0:
            logger.error(f"Critical security issues found: {report['summary']['critical_issues']}")
            sys.exit(1)

        logger.info(f"Security analysis completed. Report saved to {args.output}")

    except Exception as e:
        logger.error(f"Security analysis failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()