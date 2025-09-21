#!/usr/bin/env node

/**
 * SPARC Methodology Integration for Sprint P1
 * Integrates Specification, Pseudocode, Architecture, Refinement, Completion phases
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const { execSync } = require('child_process');

class SPARCIntegration {
    constructor() {
        this.taskBoardPath = path.join(__dirname, '..', 'tasks', 'sprint-P1.yaml');
        this.taskBoard = this.loadTaskBoard();
        this.sparcConfig = this.taskBoard.sparc_integration;
        this.sparcPhasesDir = path.join(__dirname, '..', 'sparc-phases');
        this.sessionId = `sparc-p1-${Date.now()}`;
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

    // Initialize SPARC methodology for Sprint P1
    async initializeSPARC() {
        console.log('🔄 Initializing SPARC Methodology for Sprint P1...');
        console.log(`📋 Session ID: ${this.sessionId}`);

        try {
            await this.createSPARCStructure();
            await this.initializeSpecificationPhase();
            await this.setupPseudocodePhase();
            await this.initializeArchitecturePhase();
            await this.setupRefinementPhase();
            await this.prepareCompletionPhase();

            console.log('✅ SPARC methodology initialized successfully');
            return this.sessionId;
        } catch (error) {
            console.error('❌ SPARC initialization failed:', error.message);
            throw error;
        }
    }

    async createSPARCStructure() {
        console.log('📁 Creating SPARC directory structure...');

        const directories = [
            'specification',
            'pseudocode',
            'architecture',
            'refinement',
            'completion',
            'traceability'
        ];

        if (!fs.existsSync(this.sparcPhasesDir)) {
            fs.mkdirSync(this.sparcPhasesDir, { recursive: true });
        }

        for (const dir of directories) {
            const fullPath = path.join(this.sparcPhasesDir, dir);
            if (!fs.existsSync(fullPath)) {
                fs.mkdirSync(fullPath, { recursive: true });
            }
            console.log(`   📂 ${dir}/`);
        }
    }

    async initializeSpecificationPhase() {
        console.log('📋 Initializing Specification Phase...');

        const specDir = path.join(this.sparcPhasesDir, 'specification');

        // Create specification documents for each task
        for (const task of this.taskBoard.tasks) {
            const specDoc = this.generateSpecificationDocument(task);
            const specPath = path.join(specDir, `${task.id}-specification.md`);
            fs.writeFileSync(specPath, specDoc);
        }

        // Create traceability matrix
        const traceabilityMatrix = this.generateTraceabilityMatrix();
        const matrixPath = path.join(this.sparcPhasesDir, 'traceability', 'specification-matrix.yaml');
        fs.writeFileSync(matrixPath, yaml.dump(traceabilityMatrix));

        console.log(`   ✅ ${this.taskBoard.tasks.length} specification documents created`);
    }

    generateSpecificationDocument(task) {
        return `# Specification Document: ${task.name}

## Task Information
- **ID**: ${task.id}
- **Module**: ${task.module}
- **Column**: ${task.column}
- **Priority**: ${task.priority}
- **Assignee**: ${task.assignee}
- **Estimated Hours**: ${task.estimated_hours}

## Requirements Specification

### Functional Requirements

#### Given-When-Then Acceptance Criteria
- **Given**: ${task.acceptance_criteria.given}
- **When**: ${task.acceptance_criteria.when}
- **Then**:
${task.acceptance_criteria.then.map(item => `  - ${item}`).join('\n')}

### Non-Functional Requirements

#### Performance Requirements
${task.performance_requirements ? task.performance_requirements.map(req => `- ${req}`).join('\n') : '- No specific performance requirements'}

#### Quality Attributes
- **Maintainability**: Code must be well-structured and documented
- **Testability**: All functionality must be unit testable
- **Reliability**: Error handling for all failure scenarios
- **Usability**: User-friendly interfaces where applicable

### Dependencies
${task.dependencies.length > 0 ?
  `This task depends on:\n${task.dependencies.map(dep => `- ${dep}`).join('\n')}` :
  'No dependencies identified'
}

### Constraints
- Must follow Android development best practices
- Must integrate with existing architecture
- Must pass all DoD criteria
- Must maintain performance targets

## Implementation Tasks
${task.implementation_tasks ?
  task.implementation_tasks.map(t => `- ${t}`).join('\n') :
  '- Implementation tasks to be defined in pseudocode phase'
}

## Test Cases
${task.test_cases ?
  task.test_cases.map(tc => `- ${tc}`).join('\n') :
  '- Test cases to be defined in specification refinement'
}

## Validation Criteria
The specification is considered complete when:
- All acceptance criteria are clearly defined
- Performance requirements are measurable
- Dependencies are identified and documented
- Implementation approach is feasible
- Test strategy is defined

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    generateTraceabilityMatrix() {
        const matrix = {
            sprint: this.taskBoard.sprint,
            goal: this.taskBoard.goal,
            session_id: this.sessionId,
            created_at: new Date().toISOString(),
            traceability: {}
        };

        // Map each task to its specifications and requirements
        for (const task of this.taskBoard.tasks) {
            matrix.traceability[task.id] = {
                name: task.name,
                specification_doc: `specification/${task.id}-specification.md`,
                pseudocode_doc: `pseudocode/${task.id}-algorithm.md`,
                architecture_doc: `architecture/${task.id}-design.md`,
                implementation_files: this.getImplementationFiles(task),
                test_files: this.getTestFiles(task),
                requirements: task.acceptance_criteria.then,
                performance_targets: task.performance_requirements || [],
                validation_status: 'pending'
            };
        }

        return matrix;
    }

    getImplementationFiles(task) {
        // Predict implementation files based on task module and type
        const basePackage = 'com.posecoach';
        const moduleMapping = {
            'core-geom': [`${basePackage}.coregeom`],
            'core-pose': [`${basePackage}.corepose`],
            'app': [`${basePackage}.app`],
            'suggestions-api': [`${basePackage}.suggestions`]
        };

        const modulePkg = moduleMapping[task.module] || [`${basePackage}.${task.module}`];

        // Generate likely file names based on task name
        const className = task.name.replace(/[^a-zA-Z0-9]/g, '');
        return modulePkg.map(pkg => `${pkg}.${className}.kt`);
    }

    getTestFiles(task) {
        const implementationFiles = this.getImplementationFiles(task);
        return implementationFiles.map(file => file.replace('.kt', 'Test.kt'));
    }

    async setupPseudocodePhase() {
        console.log('🧠 Setting up Pseudocode Phase...');

        const pseudocodeDir = path.join(this.sparcPhasesDir, 'pseudocode');

        // Generate algorithm documents for implementation tasks
        const implementationTasks = this.taskBoard.tasks.filter(t => t.column === 'implementation');

        for (const task of implementationTasks) {
            const algorithmDoc = this.generateAlgorithmDocument(task);
            const algorithmPath = path.join(pseudocodeDir, `${task.id}-algorithm.md`);
            fs.writeFileSync(algorithmPath, algorithmDoc);
        }

        console.log(`   ✅ ${implementationTasks.length} algorithm documents created`);
    }

    generateAlgorithmDocument(task) {
        return `# Algorithm Design: ${task.name}

## Overview
This document defines the algorithmic approach for implementing ${task.name}.

## Input/Output Specification

### Inputs
${this.getTaskInputs(task)}

### Outputs
${this.getTaskOutputs(task)}

### Data Structures
${this.getRequiredDataStructures(task)}

## Algorithm Pseudocode

### Main Algorithm
\`\`\`
ALGORITHM ${task.name.replace(/[^a-zA-Z0-9]/g, '')}
INPUT: ${this.getInputParameters(task)}
OUTPUT: ${this.getOutputParameters(task)}

BEGIN
    ${this.generatePseudocode(task)}
END
\`\`\`

### Helper Functions
${this.generateHelperFunctions(task)}

## Complexity Analysis
- **Time Complexity**: ${this.getTimeComplexity(task)}
- **Space Complexity**: ${this.getSpaceComplexity(task)}

## Performance Considerations
${task.performance_requirements ?
  task.performance_requirements.map(req => `- ${req}`).join('\n') :
  '- Follow general performance guidelines'
}

## Error Handling
${this.getErrorHandlingStrategy(task)}

## Integration Points
${this.getIntegrationPoints(task)}

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    getTaskInputs(task) {
        const inputMapping = {
            'core-geom': '- Raw pose landmarks (List<PoseLandmark>)\n- Filter parameters (OneEuroFilterParams)',
            'core-pose': '- Camera image frames (ImageProxy)\n- Detection configuration (PoseDetectorOptions)',
            'app': '- User interactions (Touch events)\n- Camera preview data (ImageAnalysis.Analyzer)',
            'suggestions-api': '- Pose analysis request (PoseAnalysisRequest)\n- API configuration (ApiKeyManager)'
        };
        return inputMapping[task.module] || '- To be defined based on task requirements';
    }

    getTaskOutputs(task) {
        const outputMapping = {
            'core-geom': '- Smoothed pose landmarks (List<PoseLandmark>)\n- Calculated angles (Map<String, Double>)',
            'core-pose': '- Pose detection results (PoseLandmarkResult)\n- Stability indicators (StabilityResult)',
            'app': '- UI updates (ViewState changes)\n- Overlay graphics (Canvas drawings)',
            'suggestions-api': '- Structured suggestions (PoseSuggestion)\n- API response status (ResponseResult)'
        };
        return outputMapping[task.module] || '- To be defined based on task requirements';
    }

    getRequiredDataStructures(task) {
        const dataStructures = {
            'core-geom': '- OneEuroFilter (smoothing state)\n- AngleCalculator (joint relationships)',
            'core-pose': '- PoseRepository (detection interface)\n- StablePoseGate (stability tracking)',
            'app': '- OverlayRenderer (graphics state)\n- CoordinateMapper (transformation matrices)',
            'suggestions-api': '- GeminiClient (API interface)\n- SuggestionCache (response caching)'
        };
        return dataStructures[task.module] || '- Standard collections and domain objects';
    }

    getInputParameters(task) {
        return task.module === 'core-geom' ? 'landmarks, params' :
               task.module === 'core-pose' ? 'imageFrame, config' :
               task.module === 'app' ? 'userInput, cameraData' :
               'request, config';
    }

    getOutputParameters(task) {
        return task.module === 'core-geom' ? 'smoothedLandmarks, angles' :
               task.module === 'core-pose' ? 'poseResults, stability' :
               task.module === 'app' ? 'uiState, overlayGraphics' :
               'suggestions, responseStatus';
    }

    generatePseudocode(task) {
        const pseudocodeTemplates = {
            'core-geom': `    // Initialize smoothing filters
    filters = initializeOneEuroFilters(params)

    // Process each landmark
    FOR each landmark in landmarks
        smoothedLandmark = filters[landmark.type].filter(landmark.position)
        smoothedLandmarks.add(smoothedLandmark)
    END FOR

    // Calculate joint angles
    angles = calculateJointAngles(smoothedLandmarks)

    RETURN smoothedLandmarks, angles`,

            'core-pose': `    // Initialize pose detector
    detector = initializeMediaPipeDetector(config)

    // Process image frame
    TRY
        poseResults = detector.detectAsync(imageFrame)
        stability = stablePoseGate.evaluate(poseResults)

        // Update repository state
        repository.updateResults(poseResults, stability)

    CATCH DetectionException e
        // Handle detection failure
        poseResults = handleDetectionFailure(e)
    END TRY

    RETURN poseResults, stability`,

            'app': `    // Setup camera preview
    cameraPreview = initializeCameraX(config)
    overlayView = initializeOverlayView()

    // Handle user input
    IF userInput.type == TOUCH
        selectedPerson = multiPersonManager.selectByTouch(userInput.coordinates)
        overlayView.highlightPerson(selectedPerson)
    END IF

    // Process camera frame
    FOR each frame in cameraData
        poseResults = poseRepository.detect(frame)
        overlayGraphics = renderPoseOverlay(poseResults)
        overlayView.draw(overlayGraphics)
    END FOR

    RETURN uiState, overlayGraphics`,

            'suggestions-api': `    // Prepare API request
    requestPayload = formatPoseData(request.landmarks)

    // Call Gemini API with retry logic
    attempts = 0
    WHILE attempts < MAX_RETRIES
        TRY
            response = geminiClient.analyzePose(requestPayload)
            suggestions = parseStructuredResponse(response)
            BREAK
        CATCH NetworkException e
            attempts++
            wait(exponentialBackoff(attempts))
        END TRY
    END WHILE

    // Cache successful response
    IF suggestions != null
        cache.store(request.hash(), suggestions)
    END IF

    RETURN suggestions, responseStatus`
        };

        return pseudocodeTemplates[task.module] || '    // Algorithm to be defined based on task requirements\n    // Follow TDD approach: Red -> Green -> Refactor';
    }

    generateHelperFunctions(task) {
        const helperFunctions = {
            'core-geom': `### initializeOneEuroFilters(params)
- Create filter instances for each landmark type
- Configure frequency cutoff and beta parameters

### calculateJointAngles(landmarks)
- Calculate angles between joint connections
- Handle edge cases for missing landmarks`,

            'core-pose': `### handleDetectionFailure(exception)
- Log detection error
- Return previous valid result if available
- Trigger error recovery mechanism

### updatePerformanceMetrics(detectionTime)
- Record inference latency
- Update performance statistics`,

            'app': `### renderPoseOverlay(poseResults)
- Transform pose coordinates to screen space
- Draw skeleton connections and landmarks
- Handle multiple person visualization

### initializeCameraX(config)
- Setup camera preview and analysis
- Configure rotation handling`,

            'suggestions-api': `### formatPoseData(landmarks)
- Convert landmarks to API format
- Apply privacy filtering
- Validate data completeness

### parseStructuredResponse(response)
- Validate JSON schema
- Extract suggestion data
- Handle partial responses`
        };

        return helperFunctions[task.module] || '### helperFunction()\n- Helper functions to be defined based on implementation needs';
    }

    getTimeComplexity(task) {
        const complexities = {
            'core-geom': 'O(n) where n is number of landmarks',
            'core-pose': 'O(1) per frame (MediaPipe handles complexity)',
            'app': 'O(p) where p is number of poses to render',
            'suggestions-api': 'O(1) for API call + network latency'
        };
        return complexities[task.module] || 'O(n) - to be analyzed based on algorithm';
    }

    getSpaceComplexity(task) {
        const complexities = {
            'core-geom': 'O(n) for filter state storage',
            'core-pose': 'O(1) constant space for detection',
            'app': 'O(p) for pose rendering buffers',
            'suggestions-api': 'O(1) for request/response data'
        };
        return complexities[task.module] || 'O(1) - constant space expected';
    }

    getErrorHandlingStrategy(task) {
        return `- Validate all inputs before processing
- Use try-catch blocks for external dependencies
- Implement graceful degradation for failures
- Log errors for debugging and monitoring
- Return meaningful error codes/messages`;
    }

    getIntegrationPoints(task) {
        const integrations = {
            'core-geom': '- Integrates with MediaPipe pose detection output\n- Provides smoothed data to overlay rendering',
            'core-pose': '- Receives camera frames from CameraX\n- Provides pose results to app layer',
            'app': '- Integrates camera, pose detection, and overlay systems\n- Coordinates with suggestions API',
            'suggestions-api': '- Receives pose data from core modules\n- Provides suggestions to app UI'
        };
        return integrations[task.module] || '- Integration points to be defined';
    }

    async initializeArchitecturePhase() {
        console.log('🏗️ Initializing Architecture Phase...');

        const archDir = path.join(this.sparcPhasesDir, 'architecture');

        // Create architecture documents
        const systemArchDoc = this.generateSystemArchitectureDocument();
        fs.writeFileSync(path.join(archDir, 'system-architecture.md'), systemArchDoc);

        // Create module-specific architecture docs
        const modules = ['core-geom', 'core-pose', 'app', 'suggestions-api'];
        for (const module of modules) {
            const moduleArchDoc = this.generateModuleArchitectureDocument(module);
            fs.writeFileSync(path.join(archDir, `${module}-architecture.md`), moduleArchDoc);
        }

        // Create task-specific design docs
        for (const task of this.taskBoard.tasks) {
            if (task.column === 'implementation') {
                const designDoc = this.generateTaskDesignDocument(task);
                fs.writeFileSync(path.join(archDir, `${task.id}-design.md`), designDoc);
            }
        }

        console.log('   ✅ Architecture documentation created');
    }

    generateSystemArchitectureDocument() {
        return `# Sprint P1 System Architecture

## Overview
This document defines the system architecture for Sprint P1 implementation of the Pose Coach Android application.

## Architecture Goals
- **Performance**: Meet <30ms inference latency @720p
- **Accuracy**: Achieve <2px overlay alignment error
- **Stability**: Provide 1-2s stable pose triggers
- **Maintainability**: Clean, testable, modular design
- **Scalability**: Support future feature additions

## High-Level Architecture

### Layer Architecture
\`\`\`
┌─────────────────────────────────────────┐
│                UI Layer                 │
│  (Activities, Views, Overlays)          │
├─────────────────────────────────────────┤
│            Application Layer            │
│  (Use Cases, Coordinators, Managers)    │
├─────────────────────────────────────────┤
│             Domain Layer                │
│  (Models, Repositories, Business Logic) │
├─────────────────────────────────────────┤
│         Infrastructure Layer            │
│  (MediaPipe, CameraX, Network, Storage) │
└─────────────────────────────────────────┘
\`\`\`

### Module Dependencies
\`\`\`
app
├── core-pose
│   └── core-geom
└── suggestions-api
\`\`\`

## Core Components

### CameraX Integration
- **Purpose**: Camera preview and image analysis pipeline
- **Key Classes**: CameraActivity, PoseImageAnalyzer
- **Performance Target**: >20fps frame processing

### MediaPipe Pose Detection
- **Purpose**: Real-time pose landmark detection
- **Key Classes**: MediaPipePoseRepository, PoseLandmarkResult
- **Performance Target**: <30ms inference latency

### Overlay System
- **Purpose**: Real-time pose skeleton rendering
- **Key Classes**: PoseOverlayView, PoseOverlayEffect, CoordinateMapper
- **Performance Target**: <2px alignment accuracy

### Geometry & Smoothing
- **Purpose**: Pose data smoothing and angle calculation
- **Key Classes**: OneEuroFilter, AngleUtils
- **Performance Target**: <2ms processing time

### Stability Detection
- **Purpose**: Detect stable poses for analysis triggers
- **Key Classes**: EnhancedStablePoseGate
- **Performance Target**: 1-2s trigger intervals

### Suggestions API
- **Purpose**: AI-powered pose analysis and suggestions
- **Key Classes**: GeminiPoseSuggestionClient
- **Performance Target**: <2s API response time

## Data Flow

### Real-Time Processing Pipeline
1. **Camera Frame** → CameraX ImageAnalysis
2. **Image Analysis** → MediaPipe Pose Detection
3. **Pose Results** → Geometry Smoothing
4. **Smoothed Poses** → Stability Detection
5. **Stable Poses** → Overlay Rendering
6. **Periodic Analysis** → Suggestions API

### Threading Model
- **Main Thread**: UI updates, user interactions
- **Camera Thread**: Image capture and preview
- **ML Thread**: Pose detection processing
- **Network Thread**: API calls and responses

## Performance Architecture

### Critical Performance Paths
1. **Camera → Pose Detection**: Must complete in <30ms
2. **Pose Results → Overlay**: Must complete in <16ms
3. **Touch → UI Response**: Must complete in <100ms

### Memory Management
- **Object Pooling**: Reuse pose result objects
- **Image Buffer Management**: Efficient camera frame handling
- **Cache Strategy**: LRU cache for API responses

### Battery Optimization
- **Adaptive Processing**: Reduce processing based on device state
- **Background Throttling**: Minimize background processing
- **Power-Aware Algorithms**: Use efficient implementations

## Security Architecture

### Data Protection
- **Local Processing**: Core pose detection runs locally
- **API Security**: Encrypted HTTPS communications
- **Privacy Controls**: User-configurable data sharing

### Error Handling
- **Graceful Degradation**: Continue operation when components fail
- **Circuit Breaker**: Prevent cascade failures
- **Monitoring**: Performance and error tracking

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    generateModuleArchitectureDocument(module) {
        const moduleInfo = {
            'core-geom': {
                purpose: 'Geometric calculations and pose data smoothing',
                keyClasses: ['OneEuroFilter', 'AngleUtils', 'CoordinateTransform'],
                responsibilities: [
                    'Smooth noisy pose landmark data',
                    'Calculate joint angles and geometric relationships',
                    'Provide coordinate transformation utilities'
                ]
            },
            'core-pose': {
                purpose: 'Pose detection and stability analysis',
                keyClasses: ['MediaPipePoseRepository', 'EnhancedStablePoseGate', 'PoseLandmarkResult'],
                responsibilities: [
                    'Interface with MediaPipe for pose detection',
                    'Detect stable poses for analysis triggers',
                    'Manage pose detection lifecycle and threading'
                ]
            },
            'app': {
                purpose: 'User interface and system coordination',
                keyClasses: ['CameraActivity', 'PoseOverlayView', 'CoordinateMapper'],
                responsibilities: [
                    'Coordinate camera preview and pose detection',
                    'Render pose overlays with pixel-perfect alignment',
                    'Handle user interactions and device orientation'
                ]
            },
            'suggestions-api': {
                purpose: 'AI-powered pose analysis and suggestions',
                keyClasses: ['GeminiPoseSuggestionClient', 'PoseSuggestion', 'ApiKeyManager'],
                responsibilities: [
                    'Analyze pose data using Gemini 2.5 Flash API',
                    'Provide structured pose improvement suggestions',
                    'Manage API rate limiting and error handling'
                ]
            }
        };

        const info = moduleInfo[module];

        return `# ${module} Module Architecture

## Purpose
${info.purpose}

## Key Classes
${info.keyClasses.map(cls => `- **${cls}**: Core functionality for ${module}`).join('\n')}

## Responsibilities
${info.responsibilities.map(resp => `- ${resp}`).join('\n')}

## Interface Design

### Public API
\`\`\`kotlin
// Main interface for ${module} module
interface ${module.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('')}Api {
    // Core functionality methods
}
\`\`\`

### Data Models
Key data structures used by this module:

\`\`\`kotlin
// Primary data model for ${module}
data class ${module.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('')}Data(
    // Model properties
)
\`\`\`

## Implementation Strategy

### Test-Driven Development
1. **Red Phase**: Write failing tests for each function
2. **Green Phase**: Implement minimal code to pass tests
3. **Refactor Phase**: Optimize and clean up implementation

### Performance Considerations
- Minimize object allocations in hot paths
- Use efficient algorithms and data structures
- Profile and optimize critical performance paths

### Error Handling
- Validate all inputs at module boundaries
- Use Result types for operations that can fail
- Implement graceful degradation strategies

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    generateTaskDesignDocument(task) {
        return `# Design Document: ${task.name}

## Task Overview
- **ID**: ${task.id}
- **Module**: ${task.module}
- **Priority**: ${task.priority}
- **Estimated Hours**: ${task.estimated_hours}

## Design Goals
${task.acceptance_criteria.then.map(goal => `- ${goal}`).join('\n')}

## Component Design

### Class Structure
\`\`\`kotlin
// Primary implementation class
class ${this.getClassName(task)} {
    // Implementation details
}
\`\`\`

### Interface Definition
\`\`\`kotlin
// Public interface
interface ${this.getInterfaceName(task)} {
    // Public methods
}
\`\`\`

## Implementation Approach

### Algorithm Design
Follows pseudocode specification in \`pseudocode/${task.id}-algorithm.md\`

### Performance Optimization
${task.performance_requirements ?
  task.performance_requirements.map(req => `- ${req}`).join('\n') :
  '- Follow general performance guidelines'
}

### Testing Strategy
${task.test_cases ?
  task.test_cases.map(tc => `- ${tc}`).join('\n') :
  '- Unit tests for all public methods\n- Integration tests for external dependencies'
}

## Dependencies
${task.dependencies.length > 0 ?
  task.dependencies.map(dep => `- ${dep}: Provides required functionality`).join('\n') :
  'No external dependencies'
}

## Risk Mitigation
- **Performance Risk**: Profile implementation and optimize hot paths
- **Integration Risk**: Use dependency injection for testability
- **Maintenance Risk**: Document complex logic and design decisions

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    getClassName(task) {
        return task.name.replace(/[^a-zA-Z0-9]/g, '').replace(/^./, str => str.toUpperCase());
    }

    getInterfaceName(task) {
        return `I${this.getClassName(task)}`;
    }

    async setupRefinementPhase() {
        console.log('⚙️ Setting up Refinement Phase...');

        const refinementDir = path.join(this.sparcPhasesDir, 'refinement');

        // Create TDD workflow documentation
        const tddWorkflow = this.generateTDDWorkflowDocument();
        fs.writeFileSync(path.join(refinementDir, 'tdd-workflow.md'), tddWorkflow);

        // Create refactoring guidelines
        const refactoringGuide = this.generateRefactoringGuide();
        fs.writeFileSync(path.join(refinementDir, 'refactoring-guidelines.md'), refactoringGuide);

        console.log('   ✅ Refinement phase documentation created');
    }

    generateTDDWorkflowDocument() {
        return `# Test-Driven Development Workflow for Sprint P1

## TDD Cycle Overview

### Red-Green-Refactor Cycle
1. **Red**: Write a failing test
2. **Green**: Write minimal code to make the test pass
3. **Refactor**: Improve code quality without changing functionality

## Implementation Workflow

### Phase 1: Testing Column (Red Phase)
For each task in the testing column:

1. **Write Failing Tests**
   \`\`\`bash
   # Create test file
   touch \${module}/src/test/kotlin/\${package}/\${ClassName}Test.kt

   # Write test that fails
   @Test
   fun \`should achieve acceptance criteria\`() {
       // Arrange: Set up test data
       // Act: Call the method under test
       // Assert: Verify expected behavior
       fail("Not implemented yet")
   }
   \`\`\`

2. **Verify Test Fails**
   \`\`\`bash
   ./gradlew :\${module}:test
   # Should see failing tests
   \`\`\`

3. **Create Minimal Interface**
   \`\`\`kotlin
   // Create interface/class structure that compiles
   class \${ClassName} {
       fun methodUnderTest(): ReturnType {
           TODO("Not implemented")
       }
   }
   \`\`\`

### Phase 2: Implementation Column (Green Phase)
For each task in the implementation column:

1. **Implement to Pass Tests**
   \`\`\`kotlin
   // Implement actual functionality
   class \${ClassName} {
       fun methodUnderTest(): ReturnType {
           // Actual implementation here
           return actualResult
       }
   }
   \`\`\`

2. **Verify All Tests Pass**
   \`\`\`bash
   ./gradlew :\${module}:test
   # All tests should now pass
   \`\`\`

3. **Check Performance Requirements**
   \`\`\`kotlin
   @Test
   fun \`should meet performance requirements\`() {
       val startTime = System.currentTimeMillis()
       // Execute operation
       val endTime = System.currentTimeMillis()
       assertThat(endTime - startTime).isLessThan(performanceTarget)
   }
   \`\`\`

### Phase 3: Refactoring Column (Refactor Phase)
For each task in the refactoring column:

1. **Ensure Tests Still Pass**
   \`\`\`bash
   ./gradlew :\${module}:test
   # Baseline: all tests pass before refactoring
   \`\`\`

2. **Refactor for Quality**
   - Extract methods for better readability
   - Optimize performance bottlenecks
   - Improve error handling
   - Reduce code duplication

3. **Verify Tests Still Pass**
   \`\`\`bash
   ./gradlew :\${module}:test
   # All tests must still pass after refactoring
   \`\`\`

## Testing Guidelines

### Unit Test Structure
\`\`\`kotlin
class \${ClassName}Test {

    @Test
    fun \`given \${condition} when \${action} then \${expected_result}\`() {
        // Given
        val testInput = createTestInput()
        val expectedOutput = createExpectedOutput()

        // When
        val actualOutput = classUnderTest.methodUnderTest(testInput)

        // Then
        assertThat(actualOutput).isEqualTo(expectedOutput)
    }
}
\`\`\`

### Performance Testing
\`\`\`kotlin
@Test
fun \`should complete within performance target\`() {
    val iterations = 1000
    val startTime = System.nanoTime()

    repeat(iterations) {
        classUnderTest.performanceOperation()
    }

    val endTime = System.nanoTime()
    val averageTime = (endTime - startTime) / iterations / 1_000_000.0 // ms

    assertThat(averageTime).isLessThan(performanceTargetMs)
}
\`\`\`

### Integration Testing
\`\`\`kotlin
@Test
fun \`should integrate correctly with dependencies\`() {
    // Test real integration with external components
    val realDependency = createRealDependency()
    val classUnderTest = ClassName(realDependency)

    val result = classUnderTest.integratedOperation()

    assertThat(result).satisfiesIntegrationRequirements()
}
\`\`\`

## Continuous Integration

### Pre-commit Hooks
\`\`\`bash
#!/bin/bash
# Run tests before commit
./gradlew test
if [ $? -ne 0 ]; then
    echo "Tests failed! Commit aborted."
    exit 1
fi
\`\`\`

### Build Pipeline
1. **Static Analysis**: Run lint and detekt
2. **Unit Tests**: Execute all unit tests
3. **Integration Tests**: Run integration test suite
4. **Performance Tests**: Validate performance requirements
5. **Coverage Report**: Ensure >95% coverage

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    generateRefactoringGuide() {
        return `# Refactoring Guidelines for Sprint P1

## Refactoring Principles

### Code Quality Goals
- **Readability**: Code should be self-documenting
- **Maintainability**: Easy to modify and extend
- **Performance**: Meet all performance requirements
- **Testability**: Easy to unit test in isolation

## Common Refactoring Patterns

### 1. Extract Method
**Before:**
\`\`\`kotlin
fun complexOperation() {
    // Setup code
    val config = createConfiguration()
    config.parameter1 = value1
    config.parameter2 = value2

    // Core logic
    val result = processWithConfig(config)

    // Cleanup code
    releaseResources(config)
    cleanupTemporaryData()
}
\`\`\`

**After:**
\`\`\`kotlin
fun complexOperation() {
    val config = setupConfiguration()
    val result = processWithConfig(config)
    cleanup(config)
}

private fun setupConfiguration() = createConfiguration().apply {
    parameter1 = value1
    parameter2 = value2
}

private fun cleanup(config: Configuration) {
    releaseResources(config)
    cleanupTemporaryData()
}
\`\`\`

### 2. Replace Conditional with Polymorphism
**Before:**
\`\`\`kotlin
fun processBasedOnType(type: String, data: Data) {
    when (type) {
        "TYPE_A" -> processTypeA(data)
        "TYPE_B" -> processTypeB(data)
        "TYPE_C" -> processTypeC(data)
    }
}
\`\`\`

**After:**
\`\`\`kotlin
interface DataProcessor {
    fun process(data: Data)
}

class TypeAProcessor : DataProcessor {
    override fun process(data: Data) = processTypeA(data)
}

// Use factory to create appropriate processor
fun processBasedOnType(type: String, data: Data) {
    val processor = processorFactory.create(type)
    processor.process(data)
}
\`\`\`

### 3. Introduce Parameter Object
**Before:**
\`\`\`kotlin
fun createPoseDetector(
    modelPath: String,
    confidenceThreshold: Float,
    maxResults: Int,
    enableSegmentation: Boolean,
    outputFormat: String
) {
    // Implementation
}
\`\`\`

**After:**
\`\`\`kotlin
data class PoseDetectorConfig(
    val modelPath: String,
    val confidenceThreshold: Float,
    val maxResults: Int,
    val enableSegmentation: Boolean,
    val outputFormat: String
)

fun createPoseDetector(config: PoseDetectorConfig) {
    // Implementation
}
\`\`\`

## Performance Refactoring

### 1. Object Pool Pattern
**Before:**
\`\`\`kotlin
fun processFrame(image: ImageProxy) {
    val result = PoseResult() // New allocation each frame
    // Process and populate result
    return result
}
\`\`\`

**After:**
\`\`\`kotlin
class PoseProcessor {
    private val resultPool = ObjectPool { PoseResult() }

    fun processFrame(image: ImageProxy): PoseResult {
        val result = resultPool.acquire()
        result.reset()
        // Process and populate result
        return result
    }

    fun releaseResult(result: PoseResult) {
        resultPool.release(result)
    }
}
\`\`\`

### 2. Lazy Initialization
**Before:**
\`\`\`kotlin
class PoseDetector {
    private val expensiveResource = createExpensiveResource()
}
\`\`\`

**After:**
\`\`\`kotlin
class PoseDetector {
    private val expensiveResource by lazy { createExpensiveResource() }
}
\`\`\`

### 3. Caching Results
**Before:**
\`\`\`kotlin
fun calculateAngle(landmark1: PoseLandmark, landmark2: PoseLandmark): Double {
    return computeComplexAngle(landmark1, landmark2)
}
\`\`\`

**After:**
\`\`\`kotlin
class AngleCalculator {
    private val angleCache = LruCache<Pair<PoseLandmark, PoseLandmark>, Double>(100)

    fun calculateAngle(landmark1: PoseLandmark, landmark2: PoseLandmark): Double {
        val key = landmark1 to landmark2
        return angleCache.get(key) ?: run {
            val angle = computeComplexAngle(landmark1, landmark2)
            angleCache.put(key, angle)
            angle
        }
    }
}
\`\`\`

## Error Handling Refactoring

### 1. Replace Exception with Result Type
**Before:**
\`\`\`kotlin
fun detectPose(image: ImageProxy): PoseResult {
    if (image.isInvalid()) {
        throw InvalidImageException("Invalid image")
    }
    return performDetection(image)
}
\`\`\`

**After:**
\`\`\`kotlin
sealed class DetectionResult {
    data class Success(val pose: PoseResult) : DetectionResult()
    data class Failure(val error: String) : DetectionResult()
}

fun detectPose(image: ImageProxy): DetectionResult {
    return if (image.isInvalid()) {
        DetectionResult.Failure("Invalid image")
    } else {
        DetectionResult.Success(performDetection(image))
    }
}
\`\`\`

## Refactoring Checklist

### Before Refactoring
- [ ] All tests are passing
- [ ] Performance benchmarks recorded
- [ ] Code coverage measured
- [ ] Commit current working state

### During Refactoring
- [ ] Make small, incremental changes
- [ ] Run tests after each change
- [ ] Maintain performance requirements
- [ ] Preserve public API contracts

### After Refactoring
- [ ] All tests still pass
- [ ] Performance requirements still met
- [ ] Code coverage maintained or improved
- [ ] Documentation updated if needed

### Quality Metrics
- [ ] Cyclomatic complexity reduced
- [ ] Code duplication eliminated
- [ ] Method length appropriate (<20 lines)
- [ ] Class size manageable (<500 lines)

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    async prepareCompletionPhase() {
        console.log('🎯 Preparing Completion Phase...');

        const completionDir = path.join(this.sparcPhasesDir, 'completion');

        // Create integration testing guide
        const integrationGuide = this.generateIntegrationTestingGuide();
        fs.writeFileSync(path.join(completionDir, 'integration-testing.md'), integrationGuide);

        // Create performance validation guide
        const performanceGuide = this.generatePerformanceValidationGuide();
        fs.writeFileSync(path.join(completionDir, 'performance-validation.md'), performanceGuide);

        // Create completion checklist
        const completionChecklist = this.generateCompletionChecklist();
        fs.writeFileSync(path.join(completionDir, 'completion-checklist.md'), completionChecklist);

        console.log('   ✅ Completion phase documentation created');
    }

    generateIntegrationTestingGuide() {
        return `# Integration Testing Guide for Sprint P1

## Integration Testing Strategy

### End-to-End Flow Testing
Test the complete pipeline from camera input to pose suggestions:

\`\`\`kotlin
@Test
fun \`complete pose analysis pipeline should work end to end\`() {
    // Given: Mock camera input
    val mockImageFrame = createMockCameraFrame()

    // When: Process through entire pipeline
    val poseResults = poseRepository.detect(mockImageFrame)
    val smoothedPose = geometryProcessor.smooth(poseResults)
    val stability = stablePoseGate.evaluate(smoothedPose)
    val suggestions = suggestionsApi.analyze(smoothedPose)

    // Then: Verify each stage
    assertThat(poseResults).isNotNull()
    assertThat(smoothedPose.jitter).isLessThan(0.05)
    assertThat(stability.isStable).isTrue()
    assertThat(suggestions).isNotEmpty()
}
\`\`\`

### Component Integration Tests

#### CameraX + Pose Detection
\`\`\`kotlin
@Test
fun \`camera frames should be processed by pose detector\`() {
    val cameraFrames = generateTestFrames()
    val detectedPoses = mutableListOf<PoseResult>()

    cameraFrames.forEach { frame ->
        val result = poseImageAnalyzer.analyze(frame)
        if (result != null) detectedPoses.add(result)
    }

    assertThat(detectedPoses).hasSize(cameraFrames.size)
}
\`\`\`

#### Pose Detection + Overlay Rendering
\`\`\`kotlin
@Test
fun \`pose results should render correctly on overlay\`() {
    val poseResult = createTestPoseResult()
    val overlayGraphics = overlayRenderer.render(poseResult)

    assertThat(overlayGraphics.landmarks).hasSize(33) // MediaPipe pose landmarks
    assertThat(overlayGraphics.connections).hasSize(32) // Skeleton connections
}
\`\`\`

#### Geometry + Stability Detection
\`\`\`kotlin
@Test
fun \`smoothed poses should trigger stability detection\`() {
    val noisyPoses = generateNoisyPoseSequence()
    val smoothedPoses = noisyPoses.map { geometryProcessor.smooth(it) }

    val stableFrames = smoothedPoses.count { stablePoseGate.evaluate(it).isStable }

    assertThat(stableFrames).isGreaterThan(0)
    assertThat(stableFrames.toDouble() / smoothedPoses.size).isLessThan(0.5) // Not every frame is stable
}
\`\`\`

### Performance Integration Tests

#### End-to-End Latency
\`\`\`kotlin
@Test
fun \`complete pipeline should meet latency requirements\`() {
    val testFrames = generateRealisticFrameSequence(100)
    val latencies = mutableListOf<Long>()

    testFrames.forEach { frame ->
        val startTime = System.nanoTime()

        val poseResult = poseRepository.detect(frame)
        val smoothedPose = geometryProcessor.smooth(poseResult)
        val overlayGraphics = overlayRenderer.render(smoothedPose)

        val endTime = System.nanoTime()
        latencies.add((endTime - startTime) / 1_000_000) // Convert to ms
    }

    val averageLatency = latencies.average()
    val p95Latency = latencies.sorted()[95] // 95th percentile

    assertThat(averageLatency).isLessThan(30.0) // <30ms average
    assertThat(p95Latency).isLessThan(50.0) // <50ms p95
}
\`\`\`

#### Memory Pressure Testing
\`\`\`kotlin
@Test
fun \`system should handle memory pressure gracefully\`() {
    val initialMemory = Runtime.getRuntime().freeMemory()
    val frames = generateLargeFrameSequence(1000)

    frames.forEach { frame ->
        poseRepository.detect(frame)
        // Verify no memory leaks
    }

    System.gc()
    val finalMemory = Runtime.getRuntime().freeMemory()
    val memoryDifference = initialMemory - finalMemory

    assertThat(memoryDifference).isLessThan(50 * 1024 * 1024) // <50MB growth
}
\`\`\`

### Error Scenario Testing

#### Network Failure Handling
\`\`\`kotlin
@Test
fun \`suggestions api should handle network failures gracefully\`() {
    val poseData = createValidPoseData()

    // Simulate network failure
    mockServer.shutdown()

    val result = suggestionsApi.analyze(poseData)

    assertThat(result.isFailure).isTrue()
    assertThat(result.error).contains("network")

    // Core functionality should continue working
    val overlayGraphics = overlayRenderer.render(poseData)
    assertThat(overlayGraphics).isNotNull()
}
\`\`\`

#### Camera Permission Denied
\`\`\`kotlin
@Test
fun \`app should handle camera permission denial\`() {
    mockCameraPermission(granted = false)

    val cameraActivity = launchCameraActivity()

    assertThat(cameraActivity.isShowingPermissionDialog()).isTrue()
    assertThat(cameraActivity.isPoseDetectionActive()).isFalse()
}
\`\`\`

### Device-Specific Testing

#### Rotation Handling
\`\`\`kotlin
@Test
fun \`overlay should maintain alignment during rotation\`() {
    val portraitPose = detectPoseInOrientation(PORTRAIT)
    val landscapePose = detectPoseInOrientation(LANDSCAPE)

    val portraitOverlay = overlayRenderer.render(portraitPose)
    val landscapeOverlay = overlayRenderer.render(landscapePose)

    // Verify coordinate transformation maintains accuracy
    assertThat(portraitOverlay.alignmentError).isLessThan(2.0)
    assertThat(landscapeOverlay.alignmentError).isLessThan(2.0)
}
\`\`\`

#### Low-End Device Performance
\`\`\`kotlin
@Test
fun \`performance should degrade gracefully on low end devices\`() {
    simulateLowEndDevice()

    val frames = generateTestFrames(100)
    val processedFrames = mutableListOf<PoseResult>()

    frames.forEach { frame ->
        val result = poseRepository.detect(frame)
        if (result != null) processedFrames.add(result)
    }

    // May drop frames but should maintain minimum performance
    assertThat(processedFrames.size.toDouble() / frames.size).isGreaterThan(0.7) // >70% frames processed
}
\`\`\`

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    generatePerformanceValidationGuide() {
        return `# Performance Validation Guide for Sprint P1

## Performance Targets
- **Inference Latency**: <30ms @720p resolution
- **Overlay Alignment Error**: <2px maximum error
- **Frame Rate**: >20fps sustained processing
- **Memory Usage**: <200MB total application memory
- **Battery Impact**: <5% increase over baseline

## Validation Methodology

### 1. Inference Latency Testing
\`\`\`kotlin
class InferenceLatencyValidator {

    @Test
    fun \`pose detection should complete within 30ms at 720p\`() {
        val testImages = loadTestImages(resolution = "720p", count = 100)
        val latencies = mutableListOf<Double>()

        testImages.forEach { image ->
            val startTime = System.nanoTime()
            val result = poseDetector.detect(image)
            val endTime = System.nanoTime()

            latencies.add((endTime - startTime) / 1_000_000.0) // Convert to ms
        }

        val averageLatency = latencies.average()
        val p95Latency = latencies.sorted()[94] // 95th percentile
        val maxLatency = latencies.maxOrNull() ?: 0.0

        assertThat(averageLatency).isLessThan(30.0)
        assertThat(p95Latency).isLessThan(40.0)
        assertThat(maxLatency).isLessThan(50.0)

        logPerformanceResults("Inference Latency", latencies)
    }
}
\`\`\`

### 2. Overlay Alignment Accuracy Testing
\`\`\`kotlin
class OverlayAlignmentValidator {

    @Test
    fun \`overlay landmarks should align within 2px of actual body parts\`() {
        val calibrationImages = loadCalibratedImages() // Known ground truth positions
        val alignmentErrors = mutableListOf<Double>()

        calibrationImages.forEach { (image, groundTruth) ->
            val detectedPose = poseDetector.detect(image)
            val overlayCoordinates = coordinateMapper.mapToScreen(detectedPose)

            groundTruth.landmarks.forEachIndexed { index, trueLandmark ->
                val detectedLandmark = overlayCoordinates.landmarks[index]
                val error = calculatePixelDistance(trueLandmark, detectedLandmark)
                alignmentErrors.add(error)
            }
        }

        val averageError = alignmentErrors.average()
        val maxError = alignmentErrors.maxOrNull() ?: 0.0

        assertThat(averageError).isLessThan(1.0) // <1px average
        assertThat(maxError).isLessThan(2.0) // <2px maximum

        logPerformanceResults("Overlay Alignment", alignmentErrors)
    }
}
\`\`\`

### 3. Frame Rate Validation
\`\`\`kotlin
class FrameRateValidator {

    @Test
    fun \`system should maintain above 20fps under normal load\`() {
        val testDuration = 10_000L // 10 seconds
        val frameTimestamps = mutableListOf<Long>()

        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < testDuration) {
            val frameStart = System.currentTimeMillis()

            // Simulate frame processing
            val mockFrame = generateMockFrame()
            val poseResult = poseRepository.detect(mockFrame)
            val smoothedPose = geometryProcessor.smooth(poseResult)
            val overlayGraphics = overlayRenderer.render(smoothedPose)

            frameTimestamps.add(System.currentTimeMillis() - frameStart)
        }

        val totalFrames = frameTimestamps.size
        val actualFps = totalFrames.toDouble() / (testDuration / 1000.0)
        val averageFrameTime = frameTimestamps.average()

        assertThat(actualFps).isGreaterThan(20.0)
        assertThat(averageFrameTime).isLessThan(50.0) // <50ms per frame

        logPerformanceResults("Frame Rate", listOf(actualFps))
    }
}
\`\`\`

### 4. Memory Usage Validation
\`\`\`kotlin
class MemoryUsageValidator {

    @Test
    fun \`application should use less than 200MB total memory\`() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        // Run typical application workload
        repeat(1000) {
            val frame = generateTestFrame()
            val poseResult = poseRepository.detect(frame)
            val smoothedPose = geometryProcessor.smooth(poseResult)
            overlayRenderer.render(smoothedPose)
        }

        // Force garbage collection
        System.gc()
        Thread.sleep(100)
        System.gc()

        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsageMB = (finalMemory - initialMemory) / (1024 * 1024)

        assertThat(memoryUsageMB).isLessThan(200)

        logPerformanceResults("Memory Usage", listOf(memoryUsageMB.toDouble()))
    }
}
\`\`\`

### 5. Battery Impact Testing
\`\`\`kotlin
class BatteryImpactValidator {

    @Test
    fun \`pose detection should have minimal battery impact\`() {
        val batteryManager = getBatteryManager()
        val initialBatteryLevel = batteryManager.currentLevel

        val testDuration = 60_000L // 1 minute test
        val startTime = System.currentTimeMillis()

        // Run continuous pose detection
        while (System.currentTimeMillis() - startTime < testDuration) {
            val frame = captureRealFrame()
            poseRepository.detect(frame)
            Thread.sleep(33) // ~30fps
        }

        val finalBatteryLevel = batteryManager.currentLevel
        val batteryDrop = initialBatteryLevel - finalBatteryLevel
        val projectedHourlyDrop = batteryDrop * (3600_000 / testDuration)

        assertThat(projectedHourlyDrop).isLessThan(5.0) // <5% per hour

        logPerformanceResults("Battery Impact", listOf(projectedHourlyDrop))
    }
}
\`\`\`

## Device-Specific Performance Testing

### Performance Tiers
\`\`\`kotlin
enum class DevicePerformanceTier {
    HIGH_END,    // Flagship devices (Pixel 6+, Galaxy S21+)
    MID_RANGE,   // Mid-range devices (Pixel 4a, Galaxy A52)
    LOW_END      // Budget devices (Android Go, <3GB RAM)
}

class DeviceSpecificValidator {

    @Test
    fun \`performance should scale appropriately by device tier\`() {
        val deviceTier = detectDevicePerformanceTier()
        val performanceTargets = getPerformanceTargets(deviceTier)

        val actualPerformance = measureCurrentPerformance()

        assertThat(actualPerformance.inferenceLatency)
            .isLessThan(performanceTargets.maxInferenceLatency)
        assertThat(actualPerformance.frameRate)
            .isGreaterThan(performanceTargets.minFrameRate)
        assertThat(actualPerformance.memoryUsage)
            .isLessThan(performanceTargets.maxMemoryUsage)
    }

    private fun getPerformanceTargets(tier: DevicePerformanceTier) = when (tier) {
        HIGH_END -> PerformanceTargets(
            maxInferenceLatency = 25.0,
            minFrameRate = 25.0,
            maxMemoryUsage = 150.0
        )
        MID_RANGE -> PerformanceTargets(
            maxInferenceLatency = 30.0,
            minFrameRate = 20.0,
            maxMemoryUsage = 200.0
        )
        LOW_END -> PerformanceTargets(
            maxInferenceLatency = 40.0,
            minFrameRate = 15.0,
            maxMemoryUsage = 250.0
        )
    }
}
\`\`\`

## Continuous Performance Monitoring

### Automated Performance Regression Detection
\`\`\`kotlin
class PerformanceRegressionDetector {

    @Test
    fun \`performance should not regress from baseline\`() {
        val currentMetrics = measurePerformanceMetrics()
        val baselineMetrics = loadBaselineMetrics()

        val regressionThreshold = 0.1 // 10% regression threshold

        assertThat(currentMetrics.inferenceLatency)
            .isLessThan(baselineMetrics.inferenceLatency * (1 + regressionThreshold))

        assertThat(currentMetrics.frameRate)
            .isGreaterThan(baselineMetrics.frameRate * (1 - regressionThreshold))

        assertThat(currentMetrics.memoryUsage)
            .isLessThan(baselineMetrics.memoryUsage * (1 + regressionThreshold))

        // Update baseline if performance improved
        if (currentMetrics.isBetterThan(baselineMetrics)) {
            saveNewBaseline(currentMetrics)
        }
    }
}
\`\`\`

### Performance Reporting
\`\`\`kotlin
fun logPerformanceResults(testName: String, measurements: List<Double>) {
    val stats = PerformanceStats(
        testName = testName,
        sampleCount = measurements.size,
        average = measurements.average(),
        median = measurements.sorted()[measurements.size / 2],
        p95 = measurements.sorted()[(measurements.size * 0.95).toInt()],
        min = measurements.minOrNull() ?: 0.0,
        max = measurements.maxOrNull() ?: 0.0,
        standardDeviation = calculateStandardDeviation(measurements)
    )

    // Log to console
    println("Performance Test Results: \$testName")
    println("Average: \${stats.average}ms")
    println("P95: \${stats.p95}ms")
    println("Range: \${stats.min}ms - \${stats.max}ms")

    // Save to file for trend analysis
    savePerformanceReport(stats)
}
\`\`\`

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    generateCompletionChecklist() {
        return `# Sprint P1 Completion Checklist

## Definition of Done Verification

### ✅ Code Quality
- [ ] All unit tests passing (>95% coverage)
- [ ] Integration tests passing
- [ ] Code review approved by senior developer
- [ ] Static analysis passes with no critical issues
- [ ] Performance benchmarks meet targets
- [ ] Memory leaks tested and resolved

### ✅ Performance Compliance
- [ ] Inference latency <30ms @720p verified
- [ ] Overlay alignment error <2px verified
- [ ] Frame rate >20fps maintained
- [ ] Memory usage <200MB verified
- [ ] Battery impact acceptable (<5% increase)

### ✅ Security & Privacy
- [ ] No sensitive data logged
- [ ] API keys properly secured
- [ ] Network communications encrypted
- [ ] Privacy settings respected
- [ ] Offline mode functional

### ✅ Documentation
- [ ] API documentation updated
- [ ] Architecture documentation current
- [ ] Performance characteristics documented
- [ ] Known limitations documented
- [ ] Setup and configuration guide complete

## SPARC Phase Completion

### 📋 Specification Phase
- [ ] All tasks have detailed Given-When-Then acceptance criteria
- [ ] Requirements are testable and measurable
- [ ] Dependencies are clearly identified
- [ ] Performance targets are specific and achievable
- [ ] Traceability matrix is complete and up-to-date

### 🧠 Pseudocode Phase
- [ ] Algorithm designs completed for all implementation tasks
- [ ] Time and space complexity analyzed
- [ ] Error handling strategies defined
- [ ] Integration points identified
- [ ] Performance considerations documented

### 🏗️ Architecture Phase
- [ ] System architecture documented
- [ ] Module interfaces defined
- [ ] Component designs completed
- [ ] Performance architecture optimized
- [ ] Security architecture validated

### ⚙️ Refinement Phase
- [ ] TDD cycle followed for all implementation
- [ ] All tests pass (Red-Green-Refactor)
- [ ] Code refactored for quality and performance
- [ ] Continuous integration pipeline passes
- [ ] Performance requirements met

### 🎯 Completion Phase
- [ ] End-to-end integration testing completed
- [ ] Performance validation passed
- [ ] All acceptance criteria verified
- [ ] System deployed and functional
- [ ] Documentation finalized

## Task Completion Status

### Testing Column (測試)
- [ ] P1-T001: Core Geometry & Smoothing Tests
- [ ] P1-T002: CameraX Pipeline & Rotation Tests
- [ ] P1-T003: MediaPipe LIVE_STREAM Integration Tests
- [ ] P1-T004: Overlay System Alignment Tests
- [ ] P1-T005: Gemini 2.5 Structured Output Tests
- [ ] P1-T006: StablePoseGate Logic Tests

### Implementation Column (實作)
- [ ] P1-I001: Core Geometry Module Implementation
- [ ] P1-I002: CameraX Integration Implementation
- [ ] P1-I003: MediaPipe Pose Repository Implementation
- [ ] P1-I004: Overlay System Implementation
- [ ] P1-I005: Gemini API Client Implementation
- [ ] P1-I006: Enhanced StablePoseGate Implementation

### Refactoring Column (重構)
- [ ] P1-R001: Performance Optimization Refactoring
- [ ] P1-R002: Error Handling & Resilience Refactoring
- [ ] P1-R003: Code Quality & Architecture Refactoring

### Documentation Column (文件)
- [ ] P1-D001: API Documentation & Integration Guide
- [ ] P1-D002: Performance Benchmarking Documentation
- [ ] P1-D003: Architecture & Design Documentation

## Integration Validation

### End-to-End Workflows
- [ ] Camera → Pose Detection → Overlay pipeline working
- [ ] Pose Detection → Geometry Processing → Stability pipeline working
- [ ] Stable Pose → Suggestions API → UI feedback pipeline working
- [ ] Error scenarios handled gracefully
- [ ] Performance targets met in integrated system

### Cross-Module Integration
- [ ] core-geom integrates correctly with core-pose
- [ ] core-pose integrates correctly with app
- [ ] app integrates correctly with suggestions-api
- [ ] All module boundaries well-defined and tested

### Device Compatibility
- [ ] Works on high-end devices (optimal performance)
- [ ] Works on mid-range devices (acceptable performance)
- [ ] Graceful degradation on low-end devices
- [ ] Handles orientation changes correctly
- [ ] Adapts to different screen sizes

## Performance Validation Results

### Benchmark Results
- [ ] Inference latency: ___ms (target: <30ms)
- [ ] Overlay alignment error: ___px (target: <2px)
- [ ] Frame rate: ___fps (target: >20fps)
- [ ] Memory usage: ___MB (target: <200MB)
- [ ] Battery impact: __% increase (target: <5%)

### Performance Regression Tests
- [ ] No performance regressions detected
- [ ] Performance baselines updated if improved
- [ ] Performance monitoring system active
- [ ] Automated regression detection configured

## Release Readiness

### Pre-Release Validation
- [ ] All P0 tasks completed and validated
- [ ] All P1 tasks completed or have mitigation plan
- [ ] P2 tasks evaluated for next sprint
- [ ] Known issues documented with workarounds
- [ ] Release notes prepared

### Deployment Checklist
- [ ] Build configuration optimized for release
- [ ] ProGuard/R8 configuration tested
- [ ] App signing configured
- [ ] Store listing materials prepared
- [ ] Beta testing plan executed

### Post-Release Monitoring
- [ ] Crash reporting configured
- [ ] Performance monitoring active
- [ ] User feedback collection ready
- [ ] Hotfix deployment process tested

## Sprint Retrospective

### What Went Well
- [ ] Successful implementation of core features
- [ ] Performance targets achieved
- [ ] Team collaboration effective
- [ ] TDD methodology followed successfully

### What Could Be Improved
- [ ] Areas for process improvement identified
- [ ] Technical debt items documented
- [ ] Performance optimization opportunities noted
- [ ] Team skill development needs identified

### Next Sprint Planning
- [ ] Sprint P2 goals defined
- [ ] Technical debt prioritized
- [ ] Team capacity planned
- [ ] Risk mitigation strategies prepared

---
**Sprint P1 Completion Criteria**: All P0 tasks completed, performance targets met, DoD checklist verified, integration testing passed.

**Sign-off Required From**:
- [ ] Technical Lead
- [ ] Product Owner
- [ ] QA Engineer
- [ ] DevOps Engineer

**Completion Date**: _______________

---
*Generated by SPARC Integration System*
*Session: ${this.sessionId}*
*Timestamp: ${new Date().toISOString()}*
`;
    }

    // CLI Commands for SPARC integration
    static async initializeSPARC() {
        const sparc = new SPARCIntegration();
        return await sparc.initializeSPARC();
    }

    static async validateSPARCCompliance() {
        const sparc = new SPARCIntegration();
        console.log('🔍 Validating SPARC Compliance...');

        // Check that all SPARC phases are properly documented
        const phases = ['specification', 'pseudocode', 'architecture', 'refinement', 'completion'];
        const results = {};

        for (const phase of phases) {
            const phaseDir = path.join(sparc.sparcPhasesDir, phase);
            const files = fs.existsSync(phaseDir) ? fs.readdirSync(phaseDir) : [];

            results[phase] = {
                directory_exists: fs.existsSync(phaseDir),
                file_count: files.length,
                files: files
            };

            console.log(`${phase.charAt(0).toUpperCase() + phase.slice(1)}: ${files.length} files`);
        }

        // Validate traceability
        const traceabilityPath = path.join(sparc.sparcPhasesDir, 'traceability', 'specification-matrix.yaml');
        const traceabilityExists = fs.existsSync(traceabilityPath);

        console.log(`Traceability Matrix: ${traceabilityExists ? 'Present' : 'Missing'}`);

        results.traceability = {
            exists: traceabilityExists,
            file: traceabilityExists ? yaml.load(fs.readFileSync(traceabilityPath, 'utf8')) : null
        };

        return results;
    }

    static generateSPARCReport() {
        const sparc = new SPARCIntegration();

        console.log('📊 SPARC Methodology Status Report');
        console.log('==================================');

        const phases = [
            { name: 'Specification', agent: sparc.sparcConfig.specification?.agent, status: 'Complete' },
            { name: 'Pseudocode', agent: sparc.sparcConfig.pseudocode?.agent, status: 'Complete' },
            { name: 'Architecture', agent: sparc.sparcConfig.architecture?.agent, status: 'Complete' },
            { name: 'Refinement', agent: sparc.sparcConfig.refinement?.agent, status: 'In Progress' },
            { name: 'Completion', agent: sparc.sparcConfig.completion?.agent, status: 'Pending' }
        ];

        phases.forEach(phase => {
            console.log(`📋 ${phase.name}:`);
            console.log(`   Agent: ${phase.agent}`);
            console.log(`   Status: ${phase.status}`);
            console.log('');
        });

        return phases;
    }
}

// CLI Interface
if (require.main === module) {
    const command = process.argv[2];

    switch (command) {
        case 'init':
            SPARCIntegration.initializeSPARC()
                .then(sessionId => {
                    console.log(`✅ SPARC methodology initialized: ${sessionId}`);
                    process.exit(0);
                })
                .catch(error => {
                    console.error('❌ SPARC initialization failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'validate':
            SPARCIntegration.validateSPARCCompliance()
                .then(results => {
                    console.log('\n📊 SPARC Compliance Results:');
                    console.log(JSON.stringify(results, null, 2));
                    process.exit(0);
                })
                .catch(error => {
                    console.error('❌ SPARC validation failed:', error.message);
                    process.exit(1);
                });
            break;

        case 'report':
            const report = SPARCIntegration.generateSPARCReport();
            process.exit(0);
            break;

        default:
            console.log('SPARC Methodology Integration for Sprint P1');
            console.log('Usage:');
            console.log('  node sparc-integration.js init       # Initialize SPARC methodology');
            console.log('  node sparc-integration.js validate   # Validate SPARC compliance');
            console.log('  node sparc-integration.js report     # Generate SPARC status report');
            break;
    }
}

module.exports = SPARCIntegration;