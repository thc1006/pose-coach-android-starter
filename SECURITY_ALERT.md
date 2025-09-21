# 🚨 SECURITY ALERT - API KEY LEAK DETECTED

## Issue
A Google API key was accidentally committed to the repository:
- **File**: `app/src/main/kotlin/com/posecoach/app/livecoach/config/LiveApiKeyManager.kt:14`
- **Key**: `AIzaSyDAckkkZGtSOjAnyUJsWvG3hZGFM39TLXI`

## Immediate Actions Required

### 1. 🔑 Revoke the Leaked API Key
- Go to [Google Cloud Console](https://console.cloud.google.com/)
- Navigate to APIs & Services > Credentials
- Find the leaked API key and **REVOKE IT IMMEDIATELY**
- Generate a new API key

### 2. 🛡️ Secure Implementation
- API keys are now loaded from `local.properties` only
- Use `SecureApiKeyManager` instead of `LiveApiKeyManager`
- Never commit API keys to source code

### 3. 📝 Setup local.properties
Create `local.properties` file (already in .gitignore):
```properties
GEMINI_API_KEY=your_new_api_key_here
```

### 4. 🧹 Clean Git History
If needed, use BFG Repo-Cleaner to remove the key from git history:
```bash
git filter-branch --force --index-filter \
'git rm --cached --ignore-unmatch app/src/main/kotlin/com/posecoach/app/livecoach/config/LiveApiKeyManager.kt' \
--prune-empty --tag-name-filter cat -- --all
```

## Prevention
- ✅ API keys now read from local.properties only
- ✅ local.properties is in .gitignore
- ✅ SecureApiKeyManager enforces secure practices
- ✅ No hardcoded secrets in source code

## Status
- ❌ **LEAKED KEY NEEDS IMMEDIATE REVOCATION**
- ✅ Code fixed to prevent future leaks
- ✅ Secure implementation deployed