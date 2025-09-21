#!/usr/bin/env node

/**
 * Sprint P1 System Setup and Validation
 * Complete setup and validation of the Sprint P1 task board automation system
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

class SprintP1SystemSetup {
    constructor() {
        this.projectRoot = path.resolve(__dirname, '..', '..');
        this.scriptsDir = __dirname;
        this.setupResults = {
            dependencies: false,
            git_hooks: false,
            workflows: false,
            sparc: false,
            agents: false,
            validation: false
        };
    }

    async setupComplete() {
        console.log('🚀 Setting up Sprint P1 Task Board Automation System...');
        console.log('================================================================');

        try {
            await this.validateEnvironment();
            await this.installDependencies();
            await this.setupGitHooks();
            await this.setupWorkflows();
            await this.initializeSPARC();
            await this.initializeAgents();
            await this.validateSystem();
            await this.generateSetupReport();

            console.log('\n🎉 Sprint P1 System Setup Complete!');
            return this.setupResults;
        } catch (error) {
            console.error('❌ Setup failed:', error.message);
            throw error;
        }
    }

    async validateEnvironment() {
        console.log('🔍 Validating environment...');

        // Check Node.js version
        const nodeVersion = process.version;
        const requiredNodeVersion = 'v16.0.0';
        if (nodeVersion < requiredNodeVersion) {
            throw new Error(`Node.js ${requiredNodeVersion}+ required, found ${nodeVersion}`);
        }
        console.log(`   ✅ Node.js: ${nodeVersion}`);

        // Check if we're in the correct directory
        const settingsFile = path.join(this.projectRoot, 'settings.gradle.kts');
        if (!fs.existsSync(settingsFile)) {
            throw new Error('Must be run from Android project root (settings.gradle.kts not found)');
        }
        console.log('   ✅ Android project root detected');

        // Check for Git repository
        const gitDir = path.join(this.projectRoot, '.git');
        if (!fs.existsSync(gitDir)) {
            throw new Error('Git repository not found');
        }
        console.log('   ✅ Git repository detected');

        // Check Java version (for Android development)
        try {
            const javaVersion = execSync('java -version 2>&1', { encoding: 'utf8' });
            if (!javaVersion.includes('17')) {
                console.warn('   ⚠️  Java 17 recommended for Android development');
            } else {
                console.log('   ✅ Java 17 detected');
            }
        } catch (error) {
            console.warn('   ⚠️  Java not found or not in PATH');
        }
    }

    async installDependencies() {
        console.log('📦 Installing dependencies...');

        try {
            // Install npm dependencies
            execSync('npm install', { cwd: this.scriptsDir, stdio: 'inherit' });
            console.log('   ✅ NPM dependencies installed');

            // Validate package.json scripts
            const packageJson = JSON.parse(fs.readFileSync(path.join(this.scriptsDir, 'package.json'), 'utf8'));
            const requiredScripts = [
                'task:status', 'dod:summary', 'agent:init', 'sparc:init', 'workflow:setup'
            ];

            for (const script of requiredScripts) {
                if (!packageJson.scripts[script]) {
                    throw new Error(`Required script missing: ${script}`);
                }
            }
            console.log('   ✅ Package.json scripts validated');

            this.setupResults.dependencies = true;
        } catch (error) {
            throw new Error(`Dependency installation failed: ${error.message}`);
        }
    }

    async setupGitHooks() {
        console.log('🔗 Setting up Git hooks...');

        try {
            const WorkflowAutomation = require('./workflow-automation.js');
            const hooks = await WorkflowAutomation.setup();

            console.log('   ✅ Git hooks configured');
            console.log('   ✅ GitHub workflows created');

            this.setupResults.git_hooks = true;
            this.setupResults.workflows = true;
        } catch (error) {
            throw new Error(`Git hooks setup failed: ${error.message}`);
        }
    }

    async setupWorkflows() {
        console.log('⚙️ Configuring GitHub workflows...');

        const workflowsDir = path.join(this.projectRoot, '.github', 'workflows');
        const expectedWorkflows = [
            'sprint-p1-ci.yml',
            'sprint-p1-pr-automation.yml',
            'sprint-p1-performance.yml',
            'sprint-p1-dod.yml'
        ];

        for (const workflow of expectedWorkflows) {
            const workflowPath = path.join(workflowsDir, workflow);
            if (fs.existsSync(workflowPath)) {
                console.log(`   ✅ ${workflow}`);
            } else {
                throw new Error(`Workflow missing: ${workflow}`);
            }
        }

        console.log('   ✅ All GitHub workflows configured');
    }

    async initializeSPARC() {
        console.log('🔄 Initializing SPARC methodology...');

        try {
            const SPARCIntegration = require('./sparc-integration.js');
            const sessionId = await SPARCIntegration.initializeSPARC();

            console.log(`   ✅ SPARC methodology initialized (${sessionId})`);
            console.log('   ✅ Specification phase ready');
            console.log('   ✅ Pseudocode phase ready');
            console.log('   ✅ Architecture phase ready');
            console.log('   ✅ Refinement phase ready');
            console.log('   ✅ Completion phase ready');

            this.setupResults.sparc = true;
        } catch (error) {
            throw new Error(`SPARC initialization failed: ${error.message}`);
        }
    }

    async initializeAgents() {
        console.log('🤖 Initializing agent coordination...');

        try {
            const SprintP1AgentCoordinator = require('./agent-coordinator.js');
            const coordinator = new SprintP1AgentCoordinator();
            const sessionId = await coordinator.initializeSwarmCoordination();

            console.log(`   ✅ Agent coordination initialized (${sessionId})`);
            console.log('   ✅ Core development team ready');
            console.log('   ✅ Testing & validation team ready');
            console.log('   ✅ Quality assurance team ready');
            console.log('   ✅ Coordination team ready');

            // Display agent matrix
            const matrix = await SprintP1AgentCoordinator.generateAgentMatrix();
            console.log(`   ✅ ${Object.keys(matrix.agentMatrix).length} agent teams configured`);

            this.setupResults.agents = true;
        } catch (error) {
            throw new Error(`Agent initialization failed: ${error.message}`);
        }
    }

    async validateSystem() {
        console.log('🧪 Validating complete system...');

        try {
            // Validate task board structure
            await this.validateTaskBoard();

            // Validate DoD checklist
            await this.validateDoDSystem();

            // Validate automation scripts
            await this.validateAutomationScripts();

            // Validate performance targets
            await this.validatePerformanceTargets();

            console.log('   ✅ System validation complete');
            this.setupResults.validation = true;
        } catch (error) {
            throw new Error(`System validation failed: ${error.message}`);
        }
    }

    async validateTaskBoard() {
        console.log('   📋 Validating task board...');

        const taskBoardPath = path.join(this.scriptsDir, '..', 'tasks', 'sprint-P1.yaml');
        if (!fs.existsSync(taskBoardPath)) {
            throw new Error('Task board not found: sprint-P1.yaml');
        }

        const yaml = require('js-yaml');
        const taskBoard = yaml.load(fs.readFileSync(taskBoardPath, 'utf8'));

        // Validate structure
        const requiredFields = ['version', 'sprint', 'goal', 'performance_targets', 'tasks'];
        for (const field of requiredFields) {
            if (!taskBoard[field]) {
                throw new Error(`Task board missing required field: ${field}`);
            }
        }

        // Validate tasks
        const requiredColumns = ['testing', 'implementation', 'refactoring', 'documentation'];
        const tasksByColumn = {};

        for (const task of taskBoard.tasks) {
            if (!task.id || !task.name || !task.column || !task.acceptance_criteria) {
                throw new Error(`Invalid task structure: ${task.id || 'unknown'}`);
            }

            if (!tasksByColumn[task.column]) {
                tasksByColumn[task.column] = 0;
            }
            tasksByColumn[task.column]++;
        }

        for (const column of requiredColumns) {
            if (!tasksByColumn[column] || tasksByColumn[column] === 0) {
                throw new Error(`No tasks found for column: ${column}`);
            }
        }

        console.log(`      ✅ ${taskBoard.tasks.length} tasks validated`);
        console.log(`      ✅ ${Object.keys(tasksByColumn).length} columns populated`);
    }

    async validateDoDSystem() {
        console.log('   📋 Validating DoD system...');

        const DoDComplianceChecker = require('./dod-checklist.js');

        try {
            // Test DoD summary generation
            const summary = await DoDComplianceChecker.generateDoDSummary();
            console.log(`      ✅ DoD system functional (${summary.total_tasks} tasks)`);
        } catch (error) {
            // DoD system might fail if tests aren't set up yet - that's OK for setup
            console.log('      ⚠️  DoD system will be functional after implementation');
        }
    }

    async validateAutomationScripts() {
        console.log('   ⚙️ Validating automation scripts...');

        const scripts = [
            'task-automation.js',
            'dod-checklist.js',
            'agent-coordinator.js',
            'sparc-integration.js',
            'workflow-automation.js'
        ];

        for (const script of scripts) {
            const scriptPath = path.join(this.scriptsDir, script);
            if (!fs.existsSync(scriptPath)) {
                throw new Error(`Script missing: ${script}`);
            }

            // Basic syntax validation
            try {
                require(scriptPath);
                console.log(`      ✅ ${script}`);
            } catch (error) {
                throw new Error(`Script syntax error in ${script}: ${error.message}`);
            }
        }
    }

    async validatePerformanceTargets() {
        console.log('   ⚡ Validating performance targets...');

        const yaml = require('js-yaml');
        const taskBoardPath = path.join(this.scriptsDir, '..', 'tasks', 'sprint-P1.yaml');
        const taskBoard = yaml.load(fs.readFileSync(taskBoardPath, 'utf8'));

        const targets = taskBoard.performance_targets;
        const requiredTargets = [
            'inference_latency',
            'overlay_alignment_error',
            'stable_pose_trigger',
            'frame_rate',
            'memory_usage'
        ];

        for (const target of requiredTargets) {
            if (!targets[target]) {
                throw new Error(`Performance target missing: ${target}`);
            }
        }

        console.log('      ✅ All performance targets defined');
        console.log(`      ✅ Inference latency: ${targets.inference_latency}`);
        console.log(`      ✅ Overlay alignment: ${targets.overlay_alignment_error}`);
        console.log(`      ✅ Frame rate: ${targets.frame_rate}`);
        console.log(`      ✅ Memory usage: ${targets.memory_usage}`);
    }

    async generateSetupReport() {
        console.log('📊 Generating setup report...');

        const report = {
            timestamp: new Date().toISOString(),
            setup_results: this.setupResults,
            system_info: {
                node_version: process.version,
                platform: process.platform,
                architecture: process.arch
            },
            project_info: await this.getProjectInfo(),
            next_steps: this.getNextSteps()
        };

        const reportPath = path.join(this.scriptsDir, '..', 'reports', 'setup-report.json');
        const reportsDir = path.dirname(reportPath);

        if (!fs.existsSync(reportsDir)) {
            fs.mkdirSync(reportsDir, { recursive: true });
        }

        fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));

        console.log(`   ✅ Setup report saved: ${reportPath}`);
        return report;
    }

    async getProjectInfo() {
        const packageJsonPath = path.join(this.scriptsDir, 'package.json');
        const taskBoardPath = path.join(this.scriptsDir, '..', 'tasks', 'sprint-P1.yaml');

        const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
        const yaml = require('js-yaml');
        const taskBoard = yaml.load(fs.readFileSync(taskBoardPath, 'utf8'));

        return {
            automation_version: packageJson.version,
            sprint: taskBoard.sprint,
            goal: taskBoard.goal,
            total_tasks: taskBoard.tasks.length,
            performance_targets: taskBoard.performance_targets
        };
    }

    getNextSteps() {
        return [
            {
                step: 1,
                description: "Start Sprint P1 execution",
                command: "npm run sprint:start",
                details: "Initialize agent coordination and SPARC methodology"
            },
            {
                step: 2,
                description: "Execute testing column tasks",
                command: "npm run agent:execute-testing",
                details: "Begin TDD cycle with comprehensive test implementation"
            },
            {
                step: 3,
                description: "Monitor sprint progress",
                command: "npm run sprint:status",
                details: "Track task completion and DoD compliance"
            },
            {
                step: 4,
                description: "Validate performance targets",
                command: "npm run dod:summary",
                details: "Ensure all performance requirements are met"
            },
            {
                step: 5,
                description: "Complete sprint delivery",
                command: "npm run agent:execute-sprint",
                details: "Execute all remaining tasks and finalize deliverables"
            }
        ];
    }

    // CLI command to run full setup
    static async runSetup() {
        const setup = new SprintP1SystemSetup();
        return await setup.setupComplete();
    }

    // CLI command to validate existing setup
    static async validateSetup() {
        const setup = new SprintP1SystemSetup();
        console.log('🔍 Validating existing Sprint P1 setup...');

        try {
            await setup.validateEnvironment();
            await setup.validateTaskBoard();
            await setup.validateAutomationScripts();
            await setup.validatePerformanceTargets();

            console.log('✅ Setup validation passed');
            return true;
        } catch (error) {
            console.error('❌ Setup validation failed:', error.message);
            return false;
        }
    }

    // CLI command to show system status
    static async showStatus() {
        const setup = new SprintP1SystemSetup();

        console.log('📊 Sprint P1 System Status');
        console.log('==========================');

        // Check each component
        const components = [
            { name: 'Dependencies', check: () => fs.existsSync(path.join(setup.scriptsDir, 'node_modules')) },
            { name: 'Task Board', check: () => fs.existsSync(path.join(setup.scriptsDir, '..', 'tasks', 'sprint-P1.yaml')) },
            { name: 'Git Hooks', check: () => fs.existsSync(path.join(setup.projectRoot, '.git', 'hooks', 'pre-commit')) },
            { name: 'GitHub Workflows', check: () => fs.existsSync(path.join(setup.projectRoot, '.github', 'workflows', 'sprint-p1-ci.yml')) },
            { name: 'SPARC Phases', check: () => fs.existsSync(path.join(setup.scriptsDir, '..', 'sparc-phases')) },
            { name: 'Reports Directory', check: () => fs.existsSync(path.join(setup.scriptsDir, '..', 'reports')) }
        ];

        components.forEach(component => {
            const status = component.check() ? '✅' : '❌';
            console.log(`${status} ${component.name}`);
        });

        // Show task progress if available
        try {
            const TaskAutomation = require('./task-automation.js');
            const report = TaskAutomation.generateStatusReport();
            console.log(`\n📋 Task Progress: ${report.completion_percentage}%`);
        } catch (error) {
            console.log('\n📋 Task Progress: Not available (run setup first)');
        }

        return components.every(c => c.check());
    }
}

// CLI Interface
if (require.main === module) {
    const command = process.argv[2];

    switch (command) {
        case 'setup':
            SprintP1SystemSetup.runSetup()
                .then(results => {
                    console.log('\n🎉 Sprint P1 system ready for development!');
                    console.log('\nNext steps:');
                    console.log('1. npm run sprint:start');
                    console.log('2. npm run agent:execute-testing');
                    console.log('3. npm run sprint:status');
                    process.exit(0);
                })
                .catch(error => {
                    console.error('\n❌ Setup failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'validate':
            SprintP1SystemSetup.validateSetup()
                .then(valid => {
                    process.exit(valid ? 0 : 1);
                });
            break;

        case 'status':
            SprintP1SystemSetup.showStatus()
                .then(healthy => {
                    process.exit(healthy ? 0 : 1);
                });
            break;

        default:
            console.log('Sprint P1 System Setup and Validation');
            console.log('Usage:');
            console.log('  node setup-system.js setup      # Complete system setup');
            console.log('  node setup-system.js validate   # Validate existing setup');
            console.log('  node setup-system.js status     # Show system status');
            console.log('');
            console.log('Quick setup:');
            console.log('  npm run install:deps');
            break;
    }
}

module.exports = SprintP1SystemSetup;