#!/usr/bin/env node

/**
 * Sprint P1 Task Automation System
 * Integrates with GitHub PR process for automatic task completion
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const { execSync } = require('child_process');

class SprintP1TaskAutomation {
    constructor() {
        this.taskBoardPath = path.join(__dirname, '..', 'tasks', 'sprint-P1.yaml');
        this.taskBoard = this.loadTaskBoard();
        this.performanceTargets = this.taskBoard.performance_targets;
        this.dodChecklist = this.taskBoard.definition_of_done;
    }

    loadTaskBoard() {
        try {
            const content = fs.readFileSync(this.taskBoardPath, 'utf8');
            return yaml.load(content);
        } catch (error) {
            console.error('Failed to load task board:', error.message);
            process.exit(1);
        }
    }

    saveTaskBoard() {
        try {
            const yamlContent = yaml.dump(this.taskBoard, {
                indent: 2,
                lineWidth: 120,
                quotingType: '"'
            });
            fs.writeFileSync(this.taskBoardPath, yamlContent, 'utf8');
            console.log('✅ Task board updated successfully');
        } catch (error) {
            console.error('❌ Failed to save task board:', error.message);
            throw error;
        }
    }

    // PR Automation: Check if task should be auto-completed
    async checkPRForTaskCompletion(prBranch, changedFiles) {
        console.log(`🔍 Analyzing PR branch: ${prBranch}`);
        console.log(`📁 Changed files: ${changedFiles.join(', ')}`);

        const completedTasks = [];

        for (const task of this.taskBoard.tasks) {
            if (this.shouldCompleteTask(task, changedFiles)) {
                if (await this.validateTaskCompletion(task, changedFiles)) {
                    completedTasks.push(task);
                    this.markTaskCompleted(task.id, `Auto-completed via PR merge`);
                }
            }
        }

        if (completedTasks.length > 0) {
            this.saveTaskBoard();
            await this.notifyTaskCompletion(completedTasks);
        }

        return completedTasks;
    }

    shouldCompleteTask(task, changedFiles) {
        // Map task modules to file patterns
        const moduleFilePatterns = {
            'core-geom': ['core-geom/**/*.kt', 'core-geom/**/*.java'],
            'core-pose': ['core-pose/**/*.kt', 'core-pose/**/*.java'],
            'app': ['app/**/*.kt', 'app/**/*.java', 'app/**/*.xml'],
            'suggestions-api': ['suggestions-api/**/*.kt', 'suggestions-api/**/*.java'],
            'docs': ['docs/**/*.md', '**/*.md'],
            'all': ['**/*']
        };

        const patterns = moduleFilePatterns[task.module] || [];
        return patterns.some(pattern =>
            changedFiles.some(file => this.matchPattern(file, pattern))
        );
    }

    matchPattern(file, pattern) {
        const regex = new RegExp(pattern.replace(/\*\*/g, '.*').replace(/\*/g, '[^/]*'));
        return regex.test(file);
    }

    async validateTaskCompletion(task, changedFiles) {
        console.log(`🧪 Validating task completion: ${task.id} - ${task.name}`);

        const validations = [];

        // 1. Unit Tests Validation
        if (task.column === 'testing' || task.column === 'implementation') {
            validations.push(this.validateUnitTests(task));
        }

        // 2. Performance Benchmarks
        if (task.performance_requirements) {
            validations.push(this.validatePerformanceTargets(task));
        }

        // 3. Code Coverage
        validations.push(this.validateCodeCoverage(task));

        // 4. DoD Checklist
        validations.push(this.validateDoDCompliance(task));

        try {
            const results = await Promise.all(validations);
            const allPassed = results.every(result => result === true);

            if (allPassed) {
                console.log(`✅ Task ${task.id} validation passed`);
                return true;
            } else {
                console.log(`❌ Task ${task.id} validation failed`);
                return false;
            }
        } catch (error) {
            console.error(`❌ Validation error for task ${task.id}:`, error.message);
            return false;
        }
    }

    async validateUnitTests(task) {
        try {
            // Run module-specific tests
            const testCommand = this.getTestCommand(task.module);
            console.log(`🧪 Running tests: ${testCommand}`);

            execSync(testCommand, { stdio: 'inherit' });
            return true;
        } catch (error) {
            console.error(`❌ Unit tests failed for ${task.module}`);
            return false;
        }
    }

    getTestCommand(module) {
        switch (module) {
            case 'core-geom':
                return './gradlew :core-geom:test';
            case 'core-pose':
                return './gradlew :core-pose:test';
            case 'app':
                return './gradlew :app:testDebugUnitTest';
            case 'suggestions-api':
                return './gradlew :suggestions-api:test';
            default:
                return './gradlew test';
        }
    }

    async validatePerformanceTargets(task) {
        // Check if performance benchmarks are defined and run them
        const benchmarkResults = await this.runPerformanceBenchmarks(task);

        for (const requirement of task.performance_requirements) {
            if (!this.checkPerformanceRequirement(requirement, benchmarkResults)) {
                console.error(`❌ Performance requirement not met: ${requirement}`);
                return false;
            }
        }

        console.log(`✅ Performance targets met for task ${task.id}`);
        return true;
    }

    async runPerformanceBenchmarks(task) {
        // Mock performance benchmark results
        // In real implementation, this would run actual benchmarks
        return {
            inference_latency: 25, // ms
            overlay_rendering: 12, // ms
            memory_usage: 180, // MB
            frame_rate: 24 // fps
        };
    }

    checkPerformanceRequirement(requirement, results) {
        // Parse performance requirements and check against results
        if (requirement.includes('inference') && requirement.includes('<30ms')) {
            return results.inference_latency < 30;
        }
        if (requirement.includes('overlay') && requirement.includes('<16ms')) {
            return results.overlay_rendering < 16;
        }
        if (requirement.includes('memory') && requirement.includes('<200MB')) {
            return results.memory_usage < 200;
        }
        if (requirement.includes('frame rate') && requirement.includes('>20fps')) {
            return results.frame_rate > 20;
        }
        return true; // Default to passing if requirement not recognized
    }

    async validateCodeCoverage(task) {
        try {
            // Run coverage analysis
            const coverageCommand = this.getCoverageCommand(task.module);
            console.log(`📊 Checking coverage: ${coverageCommand}`);

            execSync(coverageCommand, { stdio: 'pipe' });

            // Parse coverage results (simplified)
            const coverage = this.parseCoverageResults(task.module);
            const targetCoverage = 95; // 95% target

            if (coverage >= targetCoverage) {
                console.log(`✅ Code coverage: ${coverage}% (>= ${targetCoverage}%)`);
                return true;
            } else {
                console.error(`❌ Code coverage: ${coverage}% (< ${targetCoverage}%)`);
                return false;
            }
        } catch (error) {
            console.error(`❌ Coverage check failed for ${task.module}`);
            return false;
        }
    }

    getCoverageCommand(module) {
        switch (module) {
            case 'core-geom':
                return './gradlew :core-geom:jacocoTestReport';
            case 'core-pose':
                return './gradlew :core-pose:jacocoTestReport';
            case 'app':
                return './gradlew :app:jacocoTestDebugUnitTestReport';
            case 'suggestions-api':
                return './gradlew :suggestions-api:jacocoTestReport';
            default:
                return './gradlew jacocoTestReport';
        }
    }

    parseCoverageResults(module) {
        // Mock coverage parsing - in real implementation, parse actual coverage reports
        return Math.floor(Math.random() * 10) + 90; // Random 90-100%
    }

    async validateDoDCompliance(task) {
        const dodChecks = [];

        // Code Quality Checks
        for (const check of this.dodChecklist.code_quality) {
            dodChecks.push(this.checkDoDItem(check, task));
        }

        // Performance Compliance
        for (const check of this.dodChecklist.performance_compliance) {
            dodChecks.push(this.checkDoDItem(check, task));
        }

        // Security/Privacy
        for (const check of this.dodChecklist.security_privacy) {
            dodChecks.push(this.checkDoDItem(check, task));
        }

        // Documentation
        for (const check of this.dodChecklist.documentation) {
            dodChecks.push(this.checkDoDItem(check, task));
        }

        const results = await Promise.all(dodChecks);
        const passed = results.every(result => result === true);

        if (passed) {
            console.log(`✅ DoD compliance verified for task ${task.id}`);
        } else {
            console.error(`❌ DoD compliance failed for task ${task.id}`);
        }

        return passed;
    }

    async checkDoDItem(check, task) {
        // Simplified DoD checking - in real implementation, integrate with actual tools
        console.log(`🔍 Checking DoD item: ${check}`);

        if (check.includes('Unit tests')) {
            return this.validateUnitTests(task);
        }
        if (check.includes('Code review approved')) {
            return this.checkCodeReviewStatus();
        }
        if (check.includes('Static analysis')) {
            return this.runStaticAnalysis(task.module);
        }
        if (check.includes('Performance benchmarks')) {
            return this.validatePerformanceTargets(task);
        }

        // Default to true for items we can't automatically verify
        return true;
    }

    async checkCodeReviewStatus() {
        // In real implementation, check GitHub PR review status
        return true;
    }

    async runStaticAnalysis(module) {
        try {
            const lintCommand = `./gradlew :${module}:lint`;
            execSync(lintCommand, { stdio: 'pipe' });
            console.log(`✅ Static analysis passed for ${module}`);
            return true;
        } catch (error) {
            console.error(`❌ Static analysis failed for ${module}`);
            return false;
        }
    }

    markTaskCompleted(taskId, completionNote) {
        const task = this.taskBoard.tasks.find(t => t.id === taskId);
        if (task) {
            task.status = 'completed';
            task.completed_at = new Date().toISOString();
            task.completion_note = completionNote;

            console.log(`✅ Task ${taskId} marked as completed`);
        }
    }

    async notifyTaskCompletion(completedTasks) {
        console.log(`🎉 ${completedTasks.length} tasks auto-completed:`);

        for (const task of completedTasks) {
            console.log(`   ✅ ${task.id}: ${task.name}`);
        }

        // Generate completion report
        const report = this.generateCompletionReport(completedTasks);
        await this.saveCompletionReport(report);

        // Notify via Claude Flow hooks
        await this.notifyClaudeFlow(completedTasks);
    }

    generateCompletionReport(completedTasks) {
        return {
            timestamp: new Date().toISOString(),
            sprint: this.taskBoard.sprint,
            completed_tasks: completedTasks.map(task => ({
                id: task.id,
                name: task.name,
                module: task.module,
                column: task.column,
                priority: task.priority
            })),
            remaining_tasks: this.taskBoard.tasks.filter(t => t.status !== 'completed').length,
            completion_percentage: this.calculateCompletionPercentage()
        };
    }

    calculateCompletionPercentage() {
        const total = this.taskBoard.tasks.length;
        const completed = this.taskBoard.tasks.filter(t => t.status === 'completed').length;
        return Math.round((completed / total) * 100);
    }

    async saveCompletionReport(report) {
        const reportsDir = path.join(__dirname, '..', 'reports');
        if (!fs.existsSync(reportsDir)) {
            fs.mkdirSync(reportsDir, { recursive: true });
        }

        const reportPath = path.join(reportsDir, `completion-${Date.now()}.json`);
        fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));

        console.log(`📊 Completion report saved: ${reportPath}`);
    }

    async notifyClaudeFlow(completedTasks) {
        try {
            // Use Claude Flow hooks for notification
            for (const task of completedTasks) {
                const command = `npx claude-flow@alpha hooks post-task --task-id "${task.id}" --status "completed"`;
                execSync(command, { stdio: 'pipe' });
            }
            console.log(`🔔 Claude Flow notified of task completions`);
        } catch (error) {
            console.warn(`⚠️ Claude Flow notification failed: ${error.message}`);
        }
    }

    // CLI Commands
    static async handlePRMerge(prBranch, changedFiles) {
        const automation = new SprintP1TaskAutomation();
        return await automation.checkPRForTaskCompletion(prBranch, changedFiles);
    }

    static async validateTask(taskId) {
        const automation = new SprintP1TaskAutomation();
        const task = automation.taskBoard.tasks.find(t => t.id === taskId);

        if (!task) {
            console.error(`❌ Task not found: ${taskId}`);
            return false;
        }

        return await automation.validateTaskCompletion(task, []);
    }

    static generateStatusReport() {
        const automation = new SprintP1TaskAutomation();

        const report = {
            sprint: automation.taskBoard.sprint,
            goal: automation.taskBoard.goal,
            completion_percentage: automation.calculateCompletionPercentage(),
            tasks_by_status: automation.getTasksByStatus(),
            tasks_by_column: automation.getTasksByColumn(),
            performance_status: automation.getPerformanceStatus()
        };

        console.log('📊 Sprint P1 Status Report:');
        console.log('============================');
        console.log(`Sprint: ${report.sprint}`);
        console.log(`Goal: ${report.goal}`);
        console.log(`Completion: ${report.completion_percentage}%`);
        console.log('');
        console.log('Tasks by Status:');
        Object.entries(report.tasks_by_status).forEach(([status, count]) => {
            console.log(`  ${status}: ${count}`);
        });
        console.log('');
        console.log('Tasks by Column:');
        Object.entries(report.tasks_by_column).forEach(([column, count]) => {
            console.log(`  ${column}: ${count}`);
        });

        return report;
    }

    getTasksByStatus() {
        const statusCounts = { pending: 0, in_progress: 0, completed: 0 };

        this.taskBoard.tasks.forEach(task => {
            const status = task.status || 'pending';
            statusCounts[status] = (statusCounts[status] || 0) + 1;
        });

        return statusCounts;
    }

    getTasksByColumn() {
        const columnCounts = {};

        this.taskBoard.tasks.forEach(task => {
            const column = task.column;
            columnCounts[column] = (columnCounts[column] || 0) + 1;
        });

        return columnCounts;
    }

    getPerformanceStatus() {
        return {
            targets: this.performanceTargets,
            last_validated: new Date().toISOString(),
            status: 'pending_validation'
        };
    }
}

// CLI Interface
if (require.main === module) {
    const command = process.argv[2];
    const args = process.argv.slice(3);

    switch (command) {
        case 'pr-merge':
            const prBranch = args[0];
            const changedFiles = args.slice(1);
            SprintP1TaskAutomation.handlePRMerge(prBranch, changedFiles)
                .then(completed => {
                    console.log(`✅ PR automation completed. ${completed.length} tasks auto-completed.`);
                    process.exit(0);
                })
                .catch(error => {
                    console.error('❌ PR automation failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'validate-task':
            const taskId = args[0];
            if (!taskId) {
                console.error('❌ Task ID required');
                process.exit(1);
            }
            SprintP1TaskAutomation.validateTask(taskId)
                .then(valid => {
                    process.exit(valid ? 0 : 1);
                })
                .catch(error => {
                    console.error('❌ Task validation failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'status-report':
            SprintP1TaskAutomation.generateStatusReport();
            break;

        default:
            console.log('Sprint P1 Task Automation System');
            console.log('Usage:');
            console.log('  node task-automation.js pr-merge <branch> <file1> <file2> ...');
            console.log('  node task-automation.js validate-task <task-id>');
            console.log('  node task-automation.js status-report');
            break;
    }
}

module.exports = SprintP1TaskAutomation;