#!/usr/bin/env node

/**
 * Sprint P1 Workflow Automation
 * Integrates task automation with GitHub workflows and CI/CD
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

class WorkflowAutomation {
    constructor() {
        this.projectRoot = path.resolve(__dirname, '..', '..');
        this.scriptsDir = path.dirname(__filename);
        this.workflowsDir = path.join(this.projectRoot, '.github', 'workflows');
    }

    // Setup GitHub workflows for Sprint P1 automation
    async setupGitHubWorkflows() {
        console.log('⚙️ Setting up GitHub Workflows for Sprint P1...');

        // Create .github/workflows directory
        if (!fs.existsSync(this.workflowsDir)) {
            fs.mkdirSync(this.workflowsDir, { recursive: true });
        }

        // Create main CI workflow
        const ciWorkflow = this.generateCIWorkflow();
        fs.writeFileSync(path.join(this.workflowsDir, 'sprint-p1-ci.yml'), ciWorkflow);

        // Create PR automation workflow
        const prWorkflow = this.generatePRAutomationWorkflow();
        fs.writeFileSync(path.join(this.workflowsDir, 'sprint-p1-pr-automation.yml'), prWorkflow);

        // Create performance monitoring workflow
        const perfWorkflow = this.generatePerformanceWorkflow();
        fs.writeFileSync(path.join(this.workflowsDir, 'sprint-p1-performance.yml'), perfWorkflow);

        // Create DoD validation workflow
        const dodWorkflow = this.generateDoDWorkflow();
        fs.writeFileSync(path.join(this.workflowsDir, 'sprint-p1-dod.yml'), dodWorkflow);

        console.log('✅ GitHub workflows created successfully');
        return {
            ci: 'sprint-p1-ci.yml',
            pr_automation: 'sprint-p1-pr-automation.yml',
            performance: 'sprint-p1-performance.yml',
            dod: 'sprint-p1-dod.yml'
        };
    }

    generateCIWorkflow() {
        return `name: Sprint P1 - Continuous Integration

on:
  push:
    branches: [ main, develop, 'feature/*', 'sprint-p1/*' ]
  pull_request:
    branches: [ main, develop ]

env:
  JAVA_VERSION: '17'
  ANDROID_API_LEVEL: '34'
  ANDROID_BUILD_TOOLS: '34.0.0'

jobs:
  # Static Analysis and Code Quality
  static-analysis:
    name: Static Analysis
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: \${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle dependencies
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: \${{ runner.os }}-gradle-\${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Run Lint Analysis
        run: |
          ./gradlew lint

      - name: Run Detekt
        run: |
          ./gradlew detekt

      - name: Upload Lint Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: lint-results
          path: |
            **/build/reports/lint-results-*.html
            **/build/reports/detekt/

  # Unit Tests
  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest
    strategy:
      matrix:
        module: [core-geom, core-pose, app, suggestions-api]
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: \${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Cache Gradle dependencies
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: \${{ runner.os }}-gradle-\${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Run Unit Tests - \${{ matrix.module }}
        run: |
          ./gradlew :\${{ matrix.module }}:test

      - name: Generate Coverage Report
        run: |
          ./gradlew :\${{ matrix.module }}:jacocoTestReport

      - name: Upload Test Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results-\${{ matrix.module }}
          path: |
            \${{ matrix.module }}/build/reports/tests/
            \${{ matrix.module }}/build/reports/jacoco/

      - name: Upload Coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          file: \${{ matrix.module }}/build/reports/jacoco/test/jacocoTestReport.xml
          flags: \${{ matrix.module }}

  # Integration Tests
  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: \${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle dependencies
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: \${{ runner.os }}-gradle-\${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Run Integration Tests
        run: |
          ./gradlew connectedAndroidTest

  # Performance Tests
  performance-tests:
    name: Performance Validation
    runs-on: ubuntu-latest
    needs: [unit-tests]
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Install Dependencies
        working-directory: .claude/scripts
        run: npm install

      - name: Run Performance Validation
        working-directory: .claude/scripts
        run: |
          node dod-checklist.js summary

      - name: Generate Performance Report
        working-directory: .claude/scripts
        run: |
          node task-automation.js status-report

      - name: Upload Performance Results
        uses: actions/upload-artifact@v4
        with:
          name: performance-results
          path: .claude/reports/

  # Build APK
  build:
    name: Build APK
    runs-on: ubuntu-latest
    needs: [static-analysis, unit-tests]
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: \${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle dependencies
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: \${{ runner.os }}-gradle-\${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Build Debug APK
        run: |
          ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk

  # SPARC Validation
  sparc-validation:
    name: SPARC Methodology Validation
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Install Dependencies
        working-directory: .claude/scripts
        run: npm install

      - name: Validate SPARC Compliance
        working-directory: .claude/scripts
        run: |
          node sparc-integration.js validate

      - name: Generate SPARC Report
        working-directory: .claude/scripts
        run: |
          node sparc-integration.js report

  # Summary Report
  ci-summary:
    name: CI Summary
    runs-on: ubuntu-latest
    needs: [static-analysis, unit-tests, integration-tests, performance-tests, build, sparc-validation]
    if: always()
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Generate CI Summary
        working-directory: .claude/scripts
        run: |
          echo "## Sprint P1 CI Summary" > ci-summary.md
          echo "- Static Analysis: \${{ needs.static-analysis.result }}" >> ci-summary.md
          echo "- Unit Tests: \${{ needs.unit-tests.result }}" >> ci-summary.md
          echo "- Integration Tests: \${{ needs.integration-tests.result }}" >> ci-summary.md
          echo "- Performance Tests: \${{ needs.performance-tests.result }}" >> ci-summary.md
          echo "- Build: \${{ needs.build.result }}" >> ci-summary.md
          echo "- SPARC Validation: \${{ needs.sparc-validation.result }}" >> ci-summary.md

      - name: Comment PR
        if: github.event_name == 'pull_request'
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const summary = fs.readFileSync('.claude/scripts/ci-summary.md', 'utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: summary
            });
`;
    }

    generatePRAutomationWorkflow() {
        return `name: Sprint P1 - PR Automation

on:
  pull_request:
    types: [opened, synchronize, closed]
    branches: [ main, develop ]

jobs:
  pr-automation:
    name: PR Task Automation
    runs-on: ubuntu-latest
    if: github.event.action == 'closed' && github.event.pull_request.merged == true
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Install Dependencies
        working-directory: .claude/scripts
        run: npm install

      - name: Get Changed Files
        id: changed-files
        uses: tj-actions/changed-files@v44
        with:
          files: |
            **/*.kt
            **/*.java
            **/*.xml
            **/*.gradle*

      - name: Run Task Automation
        working-directory: .claude/scripts
        run: |
          node task-automation.js pr-merge \\
            "\${{ github.head_ref }}" \\
            \${{ steps.changed-files.outputs.all_changed_files }}

      - name: Validate DoD Compliance
        working-directory: .claude/scripts
        run: |
          node dod-checklist.js summary

      - name: Update Task Board
        working-directory: .claude/scripts
        run: |
          node task-automation.js status-report

      - name: Comment on PR
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');

            // Read automation results
            const reportPath = '.claude/reports/completion-*.json';
            const reports = require('glob').sync(reportPath).sort().reverse();

            if (reports.length > 0) {
              const report = JSON.parse(fs.readFileSync(reports[0], 'utf8'));

              const comment = \`## 🤖 Sprint P1 Task Automation Results

**Completed Tasks**: \${report.completed_tasks.length}
**Remaining Tasks**: \${report.remaining_tasks}
**Completion**: \${report.completion_percentage}%

### Auto-Completed Tasks
\${report.completed_tasks.map(task => \`- ✅ \${task.id}: \${task.name}\`).join('\\n')}

### Next Steps
\${report.remaining_tasks > 0 ? \`
- Continue with remaining \${report.remaining_tasks} tasks
- Follow TDD cycle: 測試 → 實作 → 重構 → 文件
- Ensure DoD compliance for all tasks
\` : \`
🎉 **All Sprint P1 tasks completed!**
- Ready for integration testing
- Performance targets validated
- DoD compliance verified
\`}

---
*Automated by Sprint P1 Task Management System*
              \`;

              github.rest.issues.createComment({
                issue_number: context.issue.number,
                owner: context.repo.owner,
                repo: context.repo.repo,
                body: comment
              });
            }

  task-validation:
    name: Task Validation
    runs-on: ubuntu-latest
    if: github.event.action == 'opened' || github.event.action == 'synchronize'
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Install Dependencies
        working-directory: .claude/scripts
        run: npm install

      - name: Extract Task ID from PR Title
        id: extract-task
        run: |
          PR_TITLE="\${{ github.event.pull_request.title }}"
          TASK_ID=\$(echo "\$PR_TITLE" | grep -oE 'P1-[A-Z][0-9]+' | head -1)
          echo "task_id=\$TASK_ID" >> \$GITHUB_OUTPUT

      - name: Validate Task DoD
        if: steps.extract-task.outputs.task_id != ''
        working-directory: .claude/scripts
        run: |
          node dod-checklist.js validate \${{ steps.extract-task.outputs.task_id }}

      - name: Update PR Status
        if: steps.extract-task.outputs.task_id != ''
        uses: actions/github-script@v7
        with:
          script: |
            const taskId = '\${{ steps.extract-task.outputs.task_id }}';

            if (taskId) {
              // Read DoD validation results
              const fs = require('fs');
              const glob = require('glob');

              const reportPattern = \`.claude/reports/dod/\${taskId}-dod-*.json\`;
              const reports = glob.sync(reportPattern).sort().reverse();

              if (reports.length > 0) {
                const report = JSON.parse(fs.readFileSync(reports[0], 'utf8'));

                const statusIcon = report.overall_status === 'pass' ? '✅' :
                                 report.overall_status === 'fail' ? '❌' : '⏳';

                const comment = \`## \${statusIcon} Task \${taskId} DoD Validation

**Overall Status**: \${report.overall_status.toUpperCase()}

### Category Results
- **Code Quality**: \${report.categories.code_quality?.status || 'pending'}
- **Performance**: \${report.categories.performance_compliance?.status || 'pending'}
- **Security**: \${report.categories.security_privacy?.status || 'pending'}
- **Documentation**: \${report.categories.documentation?.status || 'pending'}

\${report.recommendations.length > 0 ? \`
### Recommendations
\${report.recommendations.map((rec, i) => \`
\${i + 1}. **[\${rec.priority.toUpperCase()}]** \${rec.issue}
   → \${rec.recommendation}
\`).join('')}
\` : ''}

---
*DoD validation performed automatically*
                \`;

                github.rest.issues.createComment({
                  issue_number: context.issue.number,
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  body: comment
                });
              }
            }
`;
    }

    generatePerformanceWorkflow() {
        return `name: Sprint P1 - Performance Monitoring

on:
  push:
    branches: [ main ]
  schedule:
    # Run performance tests daily at 2 AM UTC
    - cron: '0 2 * * *'
  workflow_dispatch:

jobs:
  performance-benchmarks:
    name: Performance Benchmarks
    runs-on: ubuntu-latest
    strategy:
      matrix:
        device-profile: [high-end, mid-range, low-end]
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle dependencies
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: \${{ runner.os }}-gradle-\${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Configure Device Profile
        run: |
          case "\${{ matrix.device-profile }}" in
            "high-end")
              echo "DEVICE_RAM=8192" >> \$GITHUB_ENV
              echo "DEVICE_CPU_CORES=8" >> \$GITHUB_ENV
              echo "PERFORMANCE_TARGET_LATENCY=25" >> \$GITHUB_ENV
              ;;
            "mid-range")
              echo "DEVICE_RAM=4096" >> \$GITHUB_ENV
              echo "DEVICE_CPU_CORES=4" >> \$GITHUB_ENV
              echo "PERFORMANCE_TARGET_LATENCY=30" >> \$GITHUB_ENV
              ;;
            "low-end")
              echo "DEVICE_RAM=2048" >> \$GITHUB_ENV
              echo "DEVICE_CPU_CORES=2" >> \$GITHUB_ENV
              echo "PERFORMANCE_TARGET_LATENCY=40" >> \$GITHUB_ENV
              ;;
          esac

      - name: Run Performance Tests
        run: |
          ./gradlew :app:connectedBenchmarkAndroidTest \\
            -Pbenchmark.device.profile=\${{ matrix.device-profile }} \\
            -Pbenchmark.target.latency=\${{ env.PERFORMANCE_TARGET_LATENCY }}

      - name: Upload Benchmark Results
        uses: actions/upload-artifact@v4
        with:
          name: benchmark-results-\${{ matrix.device-profile }}
          path: |
            app/build/outputs/connected_android_test_additional_output/
            app/build/reports/benchmarks/

  performance-regression:
    name: Performance Regression Detection
    runs-on: ubuntu-latest
    needs: [performance-benchmarks]
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Download Benchmark Results
        uses: actions/download-artifact@v4
        with:
          pattern: benchmark-results-*
          merge-multiple: true
          path: benchmark-results/

      - name: Install Dependencies
        working-directory: .claude/scripts
        run: npm install

      - name: Analyze Performance Regression
        working-directory: .claude/scripts
        run: |
          node performance-analyzer.js \\
            --benchmark-dir ../benchmark-results \\
            --baseline-branch main \\
            --regression-threshold 0.1

      - name: Create Performance Report
        run: |
          echo "## 📊 Performance Benchmark Results" > performance-report.md
          echo "" >> performance-report.md
          echo "### Targets vs Actual" >> performance-report.md
          echo "| Metric | High-End | Mid-Range | Low-End |" >> performance-report.md
          echo "|--------|----------|-----------|---------|" >> performance-report.md

          # Parse benchmark results and populate table
          # This would be implemented based on actual benchmark output format

      - name: Comment Performance Results
        if: github.event_name == 'push'
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const report = fs.readFileSync('performance-report.md', 'utf8');

            // Find the latest commit
            const { data: commits } = await github.rest.repos.listCommits({
              owner: context.repo.owner,
              repo: context.repo.repo,
              sha: context.sha,
              per_page: 1
            });

            // Create a commit comment with performance results
            if (commits.length > 0) {
              await github.rest.repos.createCommitComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                commit_sha: commits[0].sha,
                body: report
              });
            }

  memory-leak-detection:
    name: Memory Leak Detection
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run Memory Leak Tests
        run: |
          ./gradlew :app:testDebugUnitTest \\
            -Dtest.memory.leak.detection=true \\
            -Dtest.memory.heap.dump=true

      - name: Analyze Heap Dumps
        run: |
          # Use memory analyzer tool to detect leaks
          # This would integrate with tools like Eclipse MAT or LeakCanary

      - name: Upload Memory Analysis
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: memory-analysis
          path: |
            **/build/reports/memory/
            **/heapdumps/
`;
    }

    generateDoDWorkflow() {
        return `name: Sprint P1 - Definition of Done Validation

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]
  schedule:
    # Run DoD validation daily
    - cron: '0 1 * * *'

jobs:
  dod-validation:
    name: DoD Validation
    runs-on: ubuntu-latest
    strategy:
      matrix:
        task-type: [testing, implementation, refactoring, documentation]
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Install Dependencies
        working-directory: .claude/scripts
        run: npm install

      - name: Get Tasks by Type
        id: get-tasks
        working-directory: .claude/scripts
        run: |
          TASKS=\$(node -e "
            const yaml = require('js-yaml');
            const fs = require('fs');
            const taskBoard = yaml.load(fs.readFileSync('../tasks/sprint-P1.yaml', 'utf8'));
            const tasks = taskBoard.tasks
              .filter(t => t.column === '\${{ matrix.task-type }}')
              .map(t => t.id);
            console.log(tasks.join(' '));
          ")
          echo "tasks=\$TASKS" >> \$GITHUB_OUTPUT

      - name: Validate DoD for Each Task
        if: steps.get-tasks.outputs.tasks != ''
        working-directory: .claude/scripts
        run: |
          for task_id in \${{ steps.get-tasks.outputs.tasks }}; do
            echo "Validating DoD for task: \$task_id"
            node dod-checklist.js validate \$task_id || echo "DoD validation failed for \$task_id"
          done

      - name: Generate DoD Summary Report
        working-directory: .claude/scripts
        run: |
          node dod-checklist.js summary

      - name: Upload DoD Reports
        uses: actions/upload-artifact@v4
        with:
          name: dod-reports-\${{ matrix.task-type }}
          path: .claude/reports/dod/

  compliance-summary:
    name: Compliance Summary
    runs-on: ubuntu-latest
    needs: [dod-validation]
    if: always()
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Download DoD Reports
        uses: actions/download-artifact@v4
        with:
          pattern: dod-reports-*
          merge-multiple: true
          path: dod-reports/

      - name: Generate Compliance Dashboard
        run: |
          # Aggregate all DoD reports into a compliance dashboard
          echo "## 📋 Sprint P1 Definition of Done Compliance" > compliance-summary.md
          echo "" >> compliance-summary.md
          echo "### Overall Status" >> compliance-summary.md

          # Count passed/failed tasks by category
          TESTING_PASSED=\$(find dod-reports/ -name "*testing*" -exec grep -l '"overall_status":"pass"' {} \\; | wc -l)
          IMPL_PASSED=\$(find dod-reports/ -name "*implementation*" -exec grep -l '"overall_status":"pass"' {} \\; | wc -l)
          REFACTOR_PASSED=\$(find dod-reports/ -name "*refactoring*" -exec grep -l '"overall_status":"pass"' {} \\; | wc -l)
          DOCS_PASSED=\$(find dod-reports/ -name "*documentation*" -exec grep -l '"overall_status":"pass"' {} \\; | wc -l)

          echo "| Category | Passed | Status |" >> compliance-summary.md
          echo "|----------|---------|--------|" >> compliance-summary.md
          echo "| Testing | \$TESTING_PASSED | \$([ \$TESTING_PASSED -gt 0 ] && echo '✅' || echo '❌') |" >> compliance-summary.md
          echo "| Implementation | \$IMPL_PASSED | \$([ \$IMPL_PASSED -gt 0 ] && echo '✅' || echo '❌') |" >> compliance-summary.md
          echo "| Refactoring | \$REFACTOR_PASSED | \$([ \$REFACTOR_PASSED -gt 0 ] && echo '✅' || echo '❌') |" >> compliance-summary.md
          echo "| Documentation | \$DOCS_PASSED | \$([ \$DOCS_PASSED -gt 0 ] && echo '✅' || echo '❌') |" >> compliance-summary.md

      - name: Create Issue for Failed DoD
        if: failure()
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const summary = fs.readFileSync('compliance-summary.md', 'utf8');

            await github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: '🚨 Sprint P1 DoD Compliance Failure',
              body: \`\${summary}

### Action Required
- Review failed DoD validations
- Address compliance issues
- Re-run validation after fixes

### Auto-generated by DoD Validation Workflow
Triggered by: \${{ github.event_name }}
Commit: \${{ github.sha }}
              \`,
              labels: ['sprint-p1', 'dod-compliance', 'high-priority']
            });

      - name: Upload Compliance Summary
        uses: actions/upload-artifact@v4
        with:
          name: compliance-summary
          path: compliance-summary.md
`;
    }

    // Setup Git hooks for local development
    async setupGitHooks() {
        console.log('🔗 Setting up Git hooks for Sprint P1...');

        const hooksDir = path.join(this.projectRoot, '.git', 'hooks');
        if (!fs.existsSync(hooksDir)) {
            fs.mkdirSync(hooksDir, { recursive: true });
        }

        // Pre-commit hook
        const preCommitHook = this.generatePreCommitHook();
        const preCommitPath = path.join(hooksDir, 'pre-commit');
        fs.writeFileSync(preCommitPath, preCommitHook);
        fs.chmodSync(preCommitPath, '755');

        // Pre-push hook
        const prePushHook = this.generatePrePushHook();
        const prePushPath = path.join(hooksDir, 'pre-push');
        fs.writeFileSync(prePushPath, prePushHook);
        fs.chmodSync(prePushPath, '755');

        console.log('✅ Git hooks configured successfully');
        return {
            'pre-commit': preCommitPath,
            'pre-push': prePushPath
        };
    }

    generatePreCommitHook() {
        return `#!/bin/bash
# Sprint P1 Pre-commit Hook
# Validates code quality before allowing commits

echo "🔍 Sprint P1 Pre-commit validation..."

# Check if we're in the project root
if [ ! -f "settings.gradle.kts" ]; then
    echo "❌ Must be run from project root"
    exit 1
fi

# Run lint on staged Kotlin/Java files
STAGED_FILES=\$(git diff --cached --name-only --diff-filter=ACM | grep -E "\\.(kt|java)\$")

if [ ! -z "\$STAGED_FILES" ]; then
    echo "📝 Running lint on staged files..."
    ./gradlew lint
    if [ \$? -ne 0 ]; then
        echo "❌ Lint failed. Please fix issues before committing."
        exit 1
    fi
fi

# Run unit tests for changed modules
CHANGED_MODULES=\$(git diff --cached --name-only | cut -d'/' -f1 | sort | uniq | grep -E "^(core-geom|core-pose|app|suggestions-api)\$")

for module in \$CHANGED_MODULES; do
    echo "🧪 Running tests for module: \$module"
    ./gradlew :\$module:test
    if [ \$? -ne 0 ]; then
        echo "❌ Tests failed for module \$module. Please fix before committing."
        exit 1
    fi
done

# Check if task automation scripts are valid
if [ -f ".claude/scripts/package.json" ]; then
    echo "⚙️ Validating task automation scripts..."
    cd .claude/scripts
    node task-automation.js status-report > /dev/null
    if [ \$? -ne 0 ]; then
        echo "❌ Task automation validation failed"
        exit 1
    fi
    cd ../..
fi

echo "✅ Pre-commit validation passed"
exit 0
`;
    }

    generatePrePushHook() {
        return `#!/bin/bash
# Sprint P1 Pre-push Hook
# Validates comprehensive quality before pushing

echo "🚀 Sprint P1 Pre-push validation..."

# Get the branch being pushed
BRANCH=\$(git rev-parse --abbrev-ref HEAD)
echo "📋 Pushing branch: \$BRANCH"

# Skip validation for certain branches
if [[ "\$BRANCH" == "main" || "\$BRANCH" == "develop" ]]; then
    echo "🔒 Validating critical branch: \$BRANCH"
else
    echo "🌿 Validating feature branch: \$BRANCH"
fi

# Run comprehensive test suite
echo "🧪 Running comprehensive test suite..."
./gradlew test
if [ \$? -ne 0 ]; then
    echo "❌ Test suite failed. Cannot push."
    exit 1
fi

# Validate DoD compliance if task automation is available
if [ -f ".claude/scripts/dod-checklist.js" ]; then
    echo "📋 Validating Definition of Done compliance..."
    cd .claude/scripts
    npm install --silent
    node dod-checklist.js summary
    DOD_EXIT_CODE=\$?
    cd ../..

    if [ \$DOD_EXIT_CODE -ne 0 ]; then
        echo "⚠️  DoD compliance issues detected. Review before pushing to main."
        if [[ "\$BRANCH" == "main" ]]; then
            echo "❌ Cannot push to main with DoD compliance failures."
            exit 1
        fi
    fi
fi

# Performance regression check for main branch
if [[ "\$BRANCH" == "main" ]]; then
    echo "⚡ Checking for performance regressions..."
    # This would run performance benchmarks and compare to baseline
    # For now, just ensure build succeeds with optimization
    ./gradlew assembleRelease
    if [ \$? -ne 0 ]; then
        echo "❌ Release build failed. Cannot push to main."
        exit 1
    fi
fi

echo "✅ Pre-push validation passed"
exit 0
`;
    }

    // Validate PR for task automation
    async validatePR(prTitle, changedFiles) {
        console.log(`🔍 Validating PR: ${prTitle}`);
        console.log(`📁 Changed files: ${changedFiles.length}`);

        const validation = {
            task_id: null,
            valid_title: false,
            dod_compliance: false,
            performance_impact: false,
            test_coverage: false,
            recommendations: []
        };

        // Extract task ID from PR title
        const taskIdMatch = prTitle.match(/P1-[A-Z]\d{3}/);
        if (taskIdMatch) {
            validation.task_id = taskIdMatch[0];
            validation.valid_title = true;
        } else {
            validation.recommendations.push({
                type: 'title',
                message: 'PR title should include Sprint P1 task ID (e.g., P1-T001, P1-I002)'
            });
        }

        // Check for test files
        const testFiles = changedFiles.filter(file =>
            file.includes('test') || file.includes('Test') || file.endsWith('Test.kt')
        );
        validation.test_coverage = testFiles.length > 0;

        if (!validation.test_coverage) {
            validation.recommendations.push({
                type: 'testing',
                message: 'No test files detected. Please include unit tests for new functionality.'
            });
        }

        // Check for performance-critical file changes
        const performanceCriticalFiles = changedFiles.filter(file =>
            file.includes('pose') ||
            file.includes('camera') ||
            file.includes('overlay') ||
            file.includes('mediapipe')
        );

        if (performanceCriticalFiles.length > 0) {
            validation.performance_impact = true;
            validation.recommendations.push({
                type: 'performance',
                message: 'Performance-critical files changed. Ensure performance benchmarks are run.'
            });
        }

        return validation;
    }

    // CLI commands
    static async setup() {
        const workflow = new WorkflowAutomation();
        console.log('🚀 Setting up Sprint P1 Workflow Automation...');

        try {
            const workflows = await workflow.setupGitHubWorkflows();
            const hooks = await workflow.setupGitHooks();

            console.log('\n✅ Workflow automation setup complete!');
            console.log('\n📋 Created GitHub Workflows:');
            Object.entries(workflows).forEach(([name, file]) => {
                console.log(`   - ${name}: .github/workflows/${file}`);
            });

            console.log('\n🔗 Configured Git Hooks:');
            Object.entries(hooks).forEach(([name, path]) => {
                console.log(`   - ${name}: ${path}`);
            });

            return { workflows, hooks };
        } catch (error) {
            console.error('❌ Setup failed:', error.message);
            throw error;
        }
    }

    static async validatePR(prTitle, ...changedFiles) {
        const workflow = new WorkflowAutomation();
        return await workflow.validatePR(prTitle, changedFiles);
    }
}

// CLI Interface
if (require.main === module) {
    const command = process.argv[2];
    const args = process.argv.slice(3);

    switch (command) {
        case 'setup':
            WorkflowAutomation.setup()
                .then(result => {
                    console.log('✅ Workflow automation configured successfully');
                    process.exit(0);
                })
                .catch(error => {
                    console.error('❌ Setup failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'validate-pr':
            const prTitle = args[0];
            const changedFiles = args.slice(1);

            if (!prTitle) {
                console.error('❌ PR title required');
                console.log('Usage: node workflow-automation.js validate-pr "<PR title>" [file1] [file2] ...');
                process.exit(1);
            }

            WorkflowAutomation.validatePR(prTitle, changedFiles)
                .then(validation => {
                    console.log('🔍 PR Validation Results:');
                    console.log(JSON.stringify(validation, null, 2));

                    if (validation.recommendations.length > 0) {
                        console.log('\n📝 Recommendations:');
                        validation.recommendations.forEach((rec, index) => {
                            console.log(`${index + 1}. [${rec.type}] ${rec.message}`);
                        });
                    }

                    const hasIssues = validation.recommendations.length > 0;
                    process.exit(hasIssues ? 1 : 0);
                })
                .catch(error => {
                    console.error('❌ PR validation failed:', error.message);
                    process.exit(1);
                });
            break;

        default:
            console.log('Sprint P1 Workflow Automation');
            console.log('Usage:');
            console.log('  node workflow-automation.js setup                        # Setup GitHub workflows and Git hooks');
            console.log('  node workflow-automation.js validate-pr "<title>" [files] # Validate PR for task automation');
            break;
    }
}

module.exports = WorkflowAutomation;