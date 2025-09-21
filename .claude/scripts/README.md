# Sprint P1 Task Board Automation System

A comprehensive task management and automation system for Sprint P1 of the Pose Coach Android project, integrating with Claude Code's agent system and SPARC methodology.

## 🚀 Features

- **Comprehensive Task Board**: Given-When-Then acceptance criteria with performance targets
- **TDD Cycle Management**: 測試 → 實作 → 重構 → 文件 workflow
- **Auto-completion**: Tasks auto-complete on PR merge with validation
- **DoD Compliance**: Automated Definition of Done validation
- **Agent Coordination**: Parallel execution using Claude Code's 54 agents
- **SPARC Integration**: Full methodology implementation
- **Performance Monitoring**: Real-time performance target validation
- **GitHub Workflows**: Complete CI/CD integration

## 📋 Sprint P1 Overview

### Goal
端上骨架：CameraX + Pose + Overlay + StablePoseGate + Gemini Integration

### Performance Targets
- **Inference Latency**: <30ms @720p
- **Overlay Alignment Error**: <2px
- **Stable Pose Trigger**: 1-2s intervals
- **Frame Rate**: >20fps
- **Memory Usage**: <200MB

### Task Board Structure
- **測試 (Testing)**: 6 tasks (P1-T001 to P1-T006)
- **實作 (Implementation)**: 6 tasks (P1-I001 to P1-I006)
- **重構 (Refactoring)**: 3 tasks (P1-R001 to P1-R003)
- **文件 (Documentation)**: 3 tasks (P1-D001 to P1-D003)

## 🛠️ Setup

### Prerequisites
- Node.js 16+
- Android SDK 34
- Java 17
- Git

### Installation
```bash
# Navigate to scripts directory
cd .claude/scripts

# Install dependencies
npm install

# Setup workflows and git hooks
npm run workflow:setup

# Initialize SPARC methodology
npm run sparc:init

# Initialize agent coordination
npm run agent:init
```

## 📚 Usage

### Task Management
```bash
# View sprint status
npm run sprint:status

# Validate specific task DoD
npm run dod:validate P1-T001

# Generate DoD summary for all tasks
npm run dod:summary

# Check task completion status
npm run task:status
```

### Agent Coordination
```bash
# Show agent coordination matrix
npm run agent:matrix

# Execute testing column tasks
npm run agent:execute-testing

# Execute implementation column tasks
npm run agent:execute-implementation

# Execute full sprint (all tasks)
npm run agent:execute-sprint
```

### SPARC Methodology
```bash
# Initialize SPARC phases
npm run sparc:init

# Validate SPARC compliance
npm run sparc:validate

# Generate SPARC report
node sparc-integration.js report
```

### Workflow Automation
```bash
# Setup GitHub workflows
npm run workflow:setup

# Validate PR for automation
npm run workflow:validate-pr "P1-T001: Core Geometry Tests" file1.kt file2.kt

# Simulate PR merge automation
npm run task:pr-merge feature-branch file1.kt file2.kt
```

## 🔄 TDD Workflow

The system enforces a strict TDD cycle:

### 1. Testing Phase (測試)
- Write comprehensive test cases
- Implement Given-When-Then test structure
- Ensure >95% code coverage
- Include performance benchmarks

### 2. Implementation Phase (實作)
- Implement solution to make tests pass
- Follow Android development best practices
- Meet performance requirements
- Handle error cases gracefully

### 3. Refactoring Phase (重構)
- Improve code quality without changing functionality
- Optimize performance bottlenecks
- Enhance error handling and resilience
- Update documentation

### 4. Documentation Phase (文件)
- Create comprehensive API documentation
- Document architecture decisions
- Create performance guides
- Write troubleshooting documentation

## 🤖 Agent System

### Agent Categories
- **Core Development**: `mobile-dev`, `coder`, `ml-developer`
- **Testing & Validation**: `tester`, `tdd-london-swarm`, `production-validator`
- **Quality Assurance**: `reviewer`, `perf-analyzer`, `security-manager`
- **Coordination**: `sparc-coord`, `task-orchestrator`, `hierarchical-coordinator`

### Parallel Execution
```javascript
// Claude Code Task tool spawns agents concurrently
Task("Mobile Developer", "Implement CameraX integration...", "mobile-dev")
Task("ML Developer", "Integrate MediaPipe LIVE_STREAM...", "ml-developer")
Task("Tester", "Create comprehensive test suite...", "tester")
Task("Performance Analyzer", "Validate performance targets...", "perf-analyzer")
```

## 📊 Performance Monitoring

### Automated Validation
- **Inference Latency**: Real-time measurement during pose detection
- **Overlay Alignment**: Pixel-perfect accuracy validation
- **Memory Usage**: Leak detection and optimization
- **Frame Rate**: Sustained performance monitoring

### Benchmarking
```bash
# Run performance benchmarks
./gradlew :app:connectedBenchmarkAndroidTest

# Analyze benchmark results
node performance-analyzer.js --regression-threshold 0.1
```

## 🔒 Definition of Done (DoD)

### Code Quality
- [ ] Unit tests written and passing (>95% coverage)
- [ ] Integration tests passing
- [ ] Code review approved
- [ ] Static analysis passes
- [ ] Performance benchmarks meet targets
- [ ] Memory leaks resolved

### Performance Compliance
- [ ] Inference latency <30ms @720p verified
- [ ] Overlay alignment error <2px verified
- [ ] Frame rate >20fps maintained
- [ ] Memory usage <200MB verified
- [ ] Battery impact <5% increase

### Security & Privacy
- [ ] No sensitive data logged
- [ ] API keys properly secured
- [ ] Network communications encrypted
- [ ] Privacy settings respected
- [ ] Offline mode functional

### Documentation
- [ ] API documentation updated
- [ ] Architecture documentation current
- [ ] Performance characteristics documented
- [ ] Known limitations documented
- [ ] Setup guide complete

## 🔧 Configuration

### Task Board Configuration
Edit `.claude/tasks/sprint-P1.yaml` to modify:
- Task definitions and acceptance criteria
- Performance requirements
- Agent assignments
- Dependencies

### Performance Targets
```yaml
performance_targets:
  inference_latency: "<30ms @720p"
  overlay_alignment_error: "<2px"
  stable_pose_trigger: "1-2s intervals"
  frame_rate: ">20fps"
  memory_usage: "<200MB"
```

### Agent Matrix
```yaml
agent_matrix:
  core_development:
    - "mobile-dev"
    - "coder"
    - "ml-developer"
  testing_validation:
    - "tester"
    - "tdd-london-swarm"
    - "production-validator"
```

## 🔄 GitHub Integration

### Automated Workflows
- **CI Pipeline**: Static analysis, unit tests, integration tests
- **PR Automation**: Auto-task completion on merge
- **Performance Monitoring**: Daily performance benchmarks
- **DoD Validation**: Automated compliance checking

### PR Template
```
## Sprint P1 Task: P1-[TYPE][NUMBER]

### Task Description
Brief description of the implemented feature/fix

### Acceptance Criteria
- [ ] Given: [condition]
- [ ] When: [action]
- [ ] Then: [expected result]

### Performance Impact
- [ ] No performance regression detected
- [ ] Benchmarks pass performance targets
- [ ] Memory usage within limits

### DoD Compliance
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Code review completed
- [ ] Documentation updated
```

## 📈 Monitoring & Reporting

### Dashboard
The system provides real-time monitoring through:
- Task completion progress
- Performance metrics tracking
- DoD compliance status
- Agent coordination health

### Reports
- **Sprint Status**: Overall progress and remaining work
- **Performance Report**: Benchmark results and trends
- **DoD Summary**: Compliance status across all tasks
- **SPARC Report**: Methodology phase completion

## 🐛 Troubleshooting

### Common Issues

#### Task Automation Not Working
```bash
# Check task board syntax
node -c task-automation.js

# Validate YAML structure
npm run task:status
```

#### DoD Validation Failing
```bash
# Run individual DoD checks
npm run dod:validate P1-T001

# Check specific category
node dod-checklist.js validate P1-T001
```

#### Performance Tests Failing
```bash
# Run performance benchmarks manually
./gradlew :app:connectedBenchmarkAndroidTest

# Check performance requirements
grep -r "performance_requirements" .claude/tasks/
```

#### Agent Coordination Issues
```bash
# Reinitialize swarm coordination
npm run agent:init

# Check agent matrix
npm run agent:matrix
```

## 🤝 Contributing

### Adding New Tasks
1. Edit `sprint-P1.yaml`
2. Add Given-When-Then acceptance criteria
3. Define performance requirements
4. Assign appropriate agent
5. Update dependencies

### Modifying DoD Criteria
1. Update `definition_of_done` section in task board
2. Modify validation logic in `dod-checklist.js`
3. Update GitHub workflow validations

### Adding New Agents
1. Update `agent_matrix` in task board
2. Add agent capabilities in `agent-coordinator.js`
3. Update coordination protocols

## 📄 License

MIT License - see LICENSE file for details

## 🆘 Support

For issues and questions:
1. Check troubleshooting section above
2. Review task board YAML for configuration issues
3. Check GitHub workflow logs for CI/CD issues
4. Validate Claude Code agent system integration

---

**Remember**: Claude Flow coordinates, Claude Code creates!