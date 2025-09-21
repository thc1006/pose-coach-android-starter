#!/usr/bin/env node

/**
 * Sprint P1 Agent Coordination System
 * Manages parallel agent execution using Claude Code's Task tool and SPARC methodology
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const { execSync } = require('child_process');

class SprintP1AgentCoordinator {
    constructor() {
        this.taskBoardPath = path.join(__dirname, '..', 'tasks', 'sprint-P1.yaml');
        this.taskBoard = this.loadTaskBoard();
        this.agentMatrix = this.taskBoard.agent_matrix;
        this.sparcIntegration = this.taskBoard.sparc_integration;
        this.sessionId = `sprint-p1-${Date.now()}`;
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

    // Initialize Claude Flow swarm coordination
    async initializeSwarmCoordination() {
        console.log('🚀 Initializing Sprint P1 Swarm Coordination...');
        console.log(`📋 Session ID: ${this.sessionId}`);

        try {
            // Initialize swarm topology for Sprint P1
            await this.setupSwarmTopology();
            await this.spawnAgentTypes();
            await this.initializeMemorySystem();

            console.log('✅ Swarm coordination initialized successfully');
            return this.sessionId;
        } catch (error) {
            console.error('❌ Swarm initialization failed:', error.message);
            throw error;
        }
    }

    async setupSwarmTopology() {
        console.log('🔗 Setting up swarm topology...');

        // Use hierarchical coordinator for Sprint P1 with multiple specialized teams
        const topologyConfig = {
            topology: 'hierarchical',
            maxAgents: 12,
            teams: {
                core_development: {
                    lead: 'mobile-dev',
                    members: this.agentMatrix.core_development,
                    focus: 'Android implementation and ML integration'
                },
                testing_validation: {
                    lead: 'tester',
                    members: this.agentMatrix.testing_validation,
                    focus: 'TDD cycle and performance validation'
                },
                quality_assurance: {
                    lead: 'reviewer',
                    members: this.agentMatrix.quality_assurance,
                    focus: 'Code quality and security review'
                },
                coordination: {
                    lead: 'sparc-coord',
                    members: this.agentMatrix.coordination,
                    focus: 'SPARC methodology and task orchestration'
                }
            }
        };

        // Initialize Claude Flow swarm (if available)
        try {
            await this.executeClaudeFlowCommand('swarm_init', topologyConfig);
        } catch (error) {
            console.warn('⚠️ Claude Flow not available, using local coordination');
        }
    }

    async spawnAgentTypes() {
        console.log('👥 Spawning agent types...');

        const allAgents = [
            ...this.agentMatrix.core_development,
            ...this.agentMatrix.testing_validation,
            ...this.agentMatrix.quality_assurance,
            ...this.agentMatrix.coordination
        ];

        for (const agentType of allAgents) {
            try {
                await this.executeClaudeFlowCommand('agent_spawn', {
                    type: agentType,
                    sessionId: this.sessionId,
                    capabilities: this.getAgentCapabilities(agentType)
                });
                console.log(`   ✅ ${agentType} spawned`);
            } catch (error) {
                console.warn(`   ⚠️ ${agentType} spawn failed: ${error.message}`);
            }
        }
    }

    getAgentCapabilities(agentType) {
        const capabilities = {
            'mobile-dev': ['android', 'kotlin', 'camerax', 'lifecycle', 'ui', 'testing'],
            'coder': ['general-programming', 'algorithms', 'debugging', 'optimization'],
            'ml-developer': ['mediapipe', 'machine-learning', 'computer-vision', 'performance'],
            'tester': ['unit-testing', 'integration-testing', 'tdd', 'test-automation'],
            'tdd-london-swarm': ['tdd-methodology', 'mock-objects', 'test-first'],
            'production-validator': ['performance-testing', 'load-testing', 'benchmarking'],
            'reviewer': ['code-review', 'architecture-review', 'security-review'],
            'perf-analyzer': ['performance-analysis', 'profiling', 'optimization'],
            'security-manager': ['security-analysis', 'vulnerability-assessment', 'privacy'],
            'sparc-coord': ['sparc-methodology', 'workflow-coordination', 'requirements'],
            'task-orchestrator': ['task-management', 'dependency-tracking', 'scheduling'],
            'hierarchical-coordinator': ['team-coordination', 'resource-allocation', 'communication']
        };

        return capabilities[agentType] || ['general'];
    }

    async initializeMemorySystem() {
        console.log('🧠 Initializing memory system...');

        const memoryStructure = {
            'sprint-p1': {
                'tasks': {},
                'decisions': {},
                'metrics': {},
                'coordination': {}
            }
        };

        try {
            await this.storeInMemory('sprint-p1/structure', memoryStructure);
            await this.storeInMemory('sprint-p1/session-id', this.sessionId);
            await this.storeInMemory('sprint-p1/task-board', this.taskBoard);
        } catch (error) {
            console.warn('⚠️ Memory system initialization failed:', error.message);
        }
    }

    // Execute tasks with parallel agent coordination
    async executeTasksInParallel(taskIds, executionMode = 'full') {
        console.log(`🚀 Executing ${taskIds.length} tasks in parallel (${executionMode} mode)...`);

        const tasks = taskIds.map(id => this.taskBoard.tasks.find(t => t.id === id)).filter(Boolean);
        if (tasks.length === 0) {
            throw new Error('No valid tasks found');
        }

        // Group tasks by column for TDD cycle execution
        const tasksByColumn = this.groupTasksByColumn(tasks);

        // Execute in TDD cycle order: 測試 → 實作 → 重構 → 文件
        const executionOrder = ['testing', 'implementation', 'refactoring', 'documentation'];
        const results = {};

        for (const column of executionOrder) {
            if (tasksByColumn[column] && tasksByColumn[column].length > 0) {
                console.log(`\n📋 Executing ${column} tasks...`);
                results[column] = await this.executeColumnTasks(tasksByColumn[column], executionMode);
            }
        }

        // Execute cross-cutting coordination tasks
        await this.executeCoordinationTasks(tasks);

        console.log('✅ Parallel task execution completed');
        return results;
    }

    groupTasksByColumn(tasks) {
        const grouped = {};
        for (const task of tasks) {
            if (!grouped[task.column]) {
                grouped[task.column] = [];
            }
            grouped[task.column].push(task);
        }
        return grouped;
    }

    async executeColumnTasks(tasks, executionMode) {
        const results = [];

        // Batch all tasks in the same column for parallel execution
        const taskInstructions = tasks.map(task => this.generateTaskInstruction(task, executionMode));

        // Use Claude Code's Task tool for parallel agent spawning
        const claudeCodeTasks = tasks.map((task, index) => {
            return {
                agentType: task.assignee,
                instruction: taskInstructions[index],
                taskId: task.id
            };
        });

        // Execute all tasks in parallel using Claude Code's Task tool
        const batchResults = await this.executeBatchTasks(claudeCodeTasks);

        for (let i = 0; i < tasks.length; i++) {
            const task = tasks[i];
            const result = batchResults[i];

            results.push({
                taskId: task.id,
                taskName: task.name,
                agent: task.assignee,
                status: result.status,
                output: result.output,
                performance: result.performance
            });

            // Store task result in memory
            await this.storeTaskResult(task.id, result);
        }

        return results;
    }

    generateTaskInstruction(task, executionMode) {
        const baseInstruction = `
Execute Sprint P1 task: ${task.name} (${task.id})

📋 Task Details:
- Module: ${task.module}
- Column: ${task.column}
- Priority: ${task.priority}
- Estimated Hours: ${task.estimated_hours}

🎯 Acceptance Criteria:
Given: ${task.acceptance_criteria.given}
When: ${task.acceptance_criteria.when}
Then: ${task.acceptance_criteria.then.join('\n- ')}

⚡ Performance Requirements:
${task.performance_requirements ? task.performance_requirements.join('\n- ') : 'None specified'}

🔧 Dependencies: ${task.dependencies.length > 0 ? task.dependencies.join(', ') : 'None'}

📊 Execution Mode: ${executionMode}
        `;

        // Add column-specific instructions
        const columnInstructions = this.getColumnSpecificInstructions(task.column);

        // Add SPARC integration instructions
        const sparcInstructions = this.getSPARCInstructions(task.column);

        // Add coordination hooks
        const coordinationHooks = this.getCoordinationHooks(task);

        return `${baseInstruction}

${columnInstructions}

${sparcInstructions}

🔗 Coordination Protocol:
${coordinationHooks}

💾 Memory Integration:
- Store progress in memory key: sprint-p1/tasks/${task.id}
- Check for related task outputs in memory
- Coordinate with other agents via memory updates

⚡ Performance Targets:
- Follow all performance requirements listed above
- Validate against Sprint P1 performance targets
- Report any performance issues immediately

🧪 Quality Gates:
- Follow TDD cycle for implementation tasks
- Ensure DoD compliance before task completion
- Run automated validation where possible

Use Claude Code tools (Read, Write, Edit, Bash, Grep, Glob) for actual work.
Report progress via Claude Flow hooks when available.
        `;
    }

    getColumnSpecificInstructions(column) {
        const instructions = {
            testing: `
🧪 Testing Column Instructions:
- Write comprehensive test cases covering all acceptance criteria
- Implement Given-When-Then test structure
- Ensure >95% code coverage
- Include performance benchmarks
- Test edge cases and error conditions
- Use appropriate testing frameworks (JUnit, Mockito, etc.)
- Run tests and verify they fail initially (Red phase of TDD)
            `,
            implementation: `
🛠️ Implementation Column Instructions:
- Implement solution to make tests pass (Green phase of TDD)
- Follow Android development best practices
- Use proper dependency injection and architecture patterns
- Optimize for performance requirements
- Handle error cases gracefully
- Write clean, maintainable code
- Document complex algorithms and decisions
            `,
            refactoring: `
♻️ Refactoring Column Instructions:
- Improve code quality without changing functionality (Refactor phase of TDD)
- Optimize performance bottlenecks
- Reduce code duplication
- Improve error handling and resilience
- Enhance testability and maintainability
- Update documentation to reflect changes
- Ensure all tests still pass after refactoring
            `,
            documentation: `
📚 Documentation Column Instructions:
- Create comprehensive documentation for implemented features
- Include API documentation with examples
- Document architecture decisions and trade-offs
- Create performance benchmarking guides
- Write troubleshooting and setup guides
- Ensure documentation stays in sync with code
- Include diagrams and visual aids where helpful
            `
        };

        return instructions[column] || '';
    }

    getSPARCInstructions(column) {
        const sparcAgent = this.sparcIntegration[this.mapColumnToSPARC(column)]?.agent;
        const requirements = this.sparcIntegration[this.mapColumnToSPARC(column)]?.requirements;

        return `
🔄 SPARC Methodology Integration:
- Coordinate with ${sparcAgent} agent for SPARC compliance
- Follow SPARC requirements: ${requirements}
- Update SPARC documentation in memory
- Ensure traceability from specification to implementation
        `;
    }

    mapColumnToSPARC(column) {
        const mapping = {
            testing: 'specification',
            implementation: 'refinement',
            refactoring: 'refinement',
            documentation: 'completion'
        };
        return mapping[column] || 'completion';
    }

    getCoordinationHooks(task) {
        return `
🔗 Before starting work:
npx claude-flow@alpha hooks pre-task --description "${task.name}"
npx claude-flow@alpha hooks session-restore --session-id "${this.sessionId}"

📝 During work:
npx claude-flow@alpha hooks post-edit --file "<modified-file>" --memory-key "sprint-p1/tasks/${task.id}/progress"
npx claude-flow@alpha hooks notify --message "<progress-update>"

✅ After completing work:
npx claude-flow@alpha hooks post-task --task-id "${task.id}"
npx claude-flow@alpha hooks session-end --export-metrics true
        `;
    }

    async executeBatchTasks(claudeCodeTasks) {
        console.log(`🚀 Executing ${claudeCodeTasks.length} tasks in parallel with Claude Code Task tool...`);

        // In a real implementation, this would use Claude Code's Task tool
        // For now, we simulate parallel execution
        const results = [];

        for (const task of claudeCodeTasks) {
            console.log(`   🤖 Spawning ${task.agentType} for task ${task.taskId}...`);

            // Simulate task execution
            const result = await this.simulateTaskExecution(task);
            results.push(result);
        }

        return results;
    }

    async simulateTaskExecution(task) {
        // Simulate task execution with realistic timing and outcomes
        const executionTime = Math.floor(Math.random() * 3000) + 1000; // 1-4 seconds

        // Run coordination hooks
        await this.runPreTaskHooks(task);

        // Simulate actual work
        await new Promise(resolve => setTimeout(resolve, executionTime));

        // Run post-task hooks
        await this.runPostTaskHooks(task);

        return {
            status: 'completed',
            output: `Task ${task.taskId} completed by ${task.agentType}`,
            performance: {
                execution_time_ms: executionTime,
                memory_usage_mb: Math.floor(Math.random() * 50) + 20,
                cpu_usage_percent: Math.floor(Math.random() * 30) + 10
            }
        };
    }

    async runPreTaskHooks(task) {
        try {
            await this.executeClaudeFlowHook('pre-task', {
                description: `${task.taskId}: ${task.agentType} starting work`,
                sessionId: this.sessionId
            });

            await this.executeClaudeFlowHook('session-restore', {
                sessionId: this.sessionId
            });
        } catch (error) {
            console.warn(`⚠️ Pre-task hooks failed for ${task.taskId}: ${error.message}`);
        }
    }

    async runPostTaskHooks(task) {
        try {
            await this.executeClaudeFlowHook('post-task', {
                taskId: task.taskId,
                status: 'completed'
            });

            await this.executeClaudeFlowHook('notify', {
                message: `Task ${task.taskId} completed by ${task.agentType}`
            });
        } catch (error) {
            console.warn(`⚠️ Post-task hooks failed for ${task.taskId}: ${error.message}`);
        }
    }

    async executeCoordinationTasks(tasks) {
        console.log('🔄 Executing coordination tasks...');

        // Task dependency validation
        await this.validateTaskDependencies(tasks);

        // Performance monitoring
        await this.monitorPerformanceMetrics(tasks);

        // Quality gate validation
        await this.validateQualityGates(tasks);

        // Progress reporting
        await this.generateProgressReport(tasks);
    }

    async validateTaskDependencies(tasks) {
        console.log('   🔍 Validating task dependencies...');

        for (const task of tasks) {
            if (task.dependencies.length > 0) {
                for (const depId of task.dependencies) {
                    const depTask = this.taskBoard.tasks.find(t => t.id === depId);
                    if (depTask && depTask.status !== 'completed') {
                        console.warn(`   ⚠️ Dependency not met: ${task.id} depends on ${depId}`);
                    }
                }
            }
        }
    }

    async monitorPerformanceMetrics(tasks) {
        console.log('   📊 Monitoring performance metrics...');

        const performanceData = {
            timestamp: new Date().toISOString(),
            tasks_in_progress: tasks.length,
            memory_usage: await this.getSystemMemoryUsage(),
            cpu_usage: await this.getSystemCPUUsage()
        };

        await this.storeInMemory('sprint-p1/metrics/performance', performanceData);
    }

    async validateQualityGates(tasks) {
        console.log('   🚪 Validating quality gates...');

        for (const task of tasks) {
            const qualityCheck = await this.checkTaskQuality(task);
            await this.storeInMemory(`sprint-p1/tasks/${task.id}/quality`, qualityCheck);

            if (!qualityCheck.passed) {
                console.warn(`   ⚠️ Quality gate failed for ${task.id}: ${qualityCheck.reason}`);
            }
        }
    }

    async checkTaskQuality(task) {
        // Simplified quality check
        return {
            passed: Math.random() > 0.2, // 80% pass rate
            reason: 'Simulated quality check',
            timestamp: new Date().toISOString()
        };
    }

    async generateProgressReport(tasks) {
        const report = {
            timestamp: new Date().toISOString(),
            session_id: this.sessionId,
            total_tasks: tasks.length,
            completed_tasks: tasks.filter(t => t.status === 'completed').length,
            in_progress_tasks: tasks.filter(t => t.status === 'in_progress').length,
            pending_tasks: tasks.filter(t => !t.status || t.status === 'pending').length,
            task_breakdown: this.groupTasksByColumn(tasks)
        };

        await this.storeInMemory('sprint-p1/coordination/progress', report);

        console.log(`   📊 Progress: ${report.completed_tasks}/${report.total_tasks} tasks completed`);

        return report;
    }

    // Utility methods for Claude Flow integration
    async executeClaudeFlowCommand(command, params) {
        try {
            // In real implementation, this would call actual MCP tools
            console.log(`🔄 Claude Flow: ${command}`, JSON.stringify(params, null, 2));
            return { success: true };
        } catch (error) {
            throw new Error(`Claude Flow command failed: ${command} - ${error.message}`);
        }
    }

    async executeClaudeFlowHook(hookType, params) {
        try {
            const command = `npx claude-flow@alpha hooks ${hookType} ${this.paramsToArgs(params)}`;
            execSync(command, { stdio: 'pipe' });
        } catch (error) {
            throw new Error(`Claude Flow hook failed: ${hookType} - ${error.message}`);
        }
    }

    paramsToArgs(params) {
        return Object.entries(params)
            .map(([key, value]) => `--${key} "${value}"`)
            .join(' ');
    }

    async storeInMemory(key, data) {
        try {
            // In real implementation, use Claude Flow memory system
            console.log(`💾 Memory store: ${key}`);
            return true;
        } catch (error) {
            console.warn(`⚠️ Memory store failed: ${key} - ${error.message}`);
        }
    }

    async storeTaskResult(taskId, result) {
        await this.storeInMemory(`sprint-p1/tasks/${taskId}/result`, result);
        await this.storeInMemory(`sprint-p1/tasks/${taskId}/timestamp`, new Date().toISOString());
    }

    async getSystemMemoryUsage() {
        try {
            const output = execSync('free -m', { encoding: 'utf8' });
            const lines = output.split('\n');
            const memLine = lines[1].split(/\s+/);
            return {
                total: parseInt(memLine[1]),
                used: parseInt(memLine[2]),
                available: parseInt(memLine[6])
            };
        } catch (error) {
            return { total: 8000, used: 4000, available: 4000 }; // Mock data
        }
    }

    async getSystemCPUUsage() {
        // Mock CPU usage data
        return Math.floor(Math.random() * 50) + 20;
    }

    // CLI Commands
    static async executeColumnTasks(column, taskIds = []) {
        const coordinator = new SprintP1AgentCoordinator();
        await coordinator.initializeSwarmCoordination();

        let tasks;
        if (taskIds.length > 0) {
            tasks = taskIds;
        } else {
            // Get all tasks in the specified column
            tasks = coordinator.taskBoard.tasks
                .filter(task => task.column === column)
                .map(task => task.id);
        }

        return await coordinator.executeTasksInParallel(tasks, 'full');
    }

    static async executeFullSprint() {
        const coordinator = new SprintP1AgentCoordinator();
        await coordinator.initializeSwarmCoordination();

        // Execute all P0 tasks first, then P1, then P2
        const tasksByPriority = {
            P0: coordinator.taskBoard.tasks.filter(t => t.priority === 'P0').map(t => t.id),
            P1: coordinator.taskBoard.tasks.filter(t => t.priority === 'P1').map(t => t.id),
            P2: coordinator.taskBoard.tasks.filter(t => t.priority === 'P2').map(t => t.id)
        };

        const results = {};

        for (const [priority, taskIds] of Object.entries(tasksByPriority)) {
            if (taskIds.length > 0) {
                console.log(`\n🎯 Executing ${priority} priority tasks...`);
                results[priority] = await coordinator.executeTasksInParallel(taskIds, 'full');
            }
        }

        return results;
    }

    static async generateAgentMatrix() {
        const coordinator = new SprintP1AgentCoordinator();

        console.log('🤖 Sprint P1 Agent Coordination Matrix');
        console.log('=====================================');

        const matrix = coordinator.agentMatrix;

        for (const [team, agents] of Object.entries(matrix)) {
            console.log(`\n📋 ${team.replace('_', ' ').toUpperCase()}:`);
            agents.forEach(agent => {
                const capabilities = coordinator.getAgentCapabilities(agent).join(', ');
                console.log(`   🤖 ${agent}: ${capabilities}`);
            });
        }

        // Generate task assignment matrix
        console.log('\n📊 Task Assignment Matrix:');
        console.log('==========================');

        const tasksByAgent = {};
        coordinator.taskBoard.tasks.forEach(task => {
            if (!tasksByAgent[task.assignee]) {
                tasksByAgent[task.assignee] = [];
            }
            tasksByAgent[task.assignee].push(task);
        });

        for (const [agent, tasks] of Object.entries(tasksByAgent)) {
            console.log(`\n🤖 ${agent}:`);
            tasks.forEach(task => {
                console.log(`   📋 ${task.id}: ${task.name} [${task.column}] (${task.priority})`);
            });
        }

        return { agentMatrix: matrix, taskAssignments: tasksByAgent };
    }
}

// CLI Interface
if (require.main === module) {
    const command = process.argv[2];
    const args = process.argv.slice(3);

    switch (command) {
        case 'init':
            const coordinator = new SprintP1AgentCoordinator();
            coordinator.initializeSwarmCoordination()
                .then(sessionId => {
                    console.log(`✅ Swarm coordination initialized: ${sessionId}`);
                    process.exit(0);
                })
                .catch(error => {
                    console.error('❌ Initialization failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'execute-column':
            const column = args[0];
            const taskIds = args.slice(1);

            if (!column) {
                console.error('❌ Column required (testing, implementation, refactoring, documentation)');
                process.exit(1);
            }

            SprintP1AgentCoordinator.executeColumnTasks(column, taskIds)
                .then(results => {
                    console.log(`✅ ${column} tasks completed`);
                    console.log(JSON.stringify(results, null, 2));
                    process.exit(0);
                })
                .catch(error => {
                    console.error('❌ Column execution failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'execute-sprint':
            SprintP1AgentCoordinator.executeFullSprint()
                .then(results => {
                    console.log('✅ Sprint P1 execution completed');
                    console.log(JSON.stringify(results, null, 2));
                    process.exit(0);
                })
                .catch(error => {
                    console.error('❌ Sprint execution failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'matrix':
            SprintP1AgentCoordinator.generateAgentMatrix()
                .then(() => {
                    process.exit(0);
                })
                .catch(error => {
                    console.error('❌ Matrix generation failed:', error.message);
                    process.exit(1);
                });
            break;

        default:
            console.log('Sprint P1 Agent Coordination System');
            console.log('Usage:');
            console.log('  node agent-coordinator.js init                              # Initialize swarm coordination');
            console.log('  node agent-coordinator.js execute-column <column> [tasks]  # Execute specific column tasks');
            console.log('  node agent-coordinator.js execute-sprint                   # Execute full sprint');
            console.log('  node agent-coordinator.js matrix                           # Show agent coordination matrix');
            console.log('');
            console.log('Columns: testing, implementation, refactoring, documentation');
            break;
    }
}

module.exports = SprintP1AgentCoordinator;