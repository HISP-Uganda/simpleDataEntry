# SimpleDataEntry DHIS2 Android App - Claude Context

## Project Overview

**SimpleDataEntry** is a production-ready Android application built for efficient DHIS2 data collection with comprehensive offline capabilities. This app supports all DHIS2 program types (aggregate datasets, tracker programs, event programs) with a unified interface and robust data synchronization.

## Key Technical Achievements

### 1. Universal DHIS2 Program Support ✅
- **Aggregate Datasets**: Complete data entry functionality with offline capabilities
- **Tracker Programs**: Enrollment creation with FAB navigation to CreateEnrollment screens
- **Event Programs**: Event creation with FAB navigation to CreateEvent screens
- **Unified Interface**: Single screen supporting all program types with dynamic filtering

### 2. DHIS2 SDK Foreign Key Constraint Resolution ✅
- **Root Cause Identified**: CategoryOptionCombo foreign key violations preventing data storage
- **Comprehensive Solution**: Based on official DHIS2 SDK documentation and community solutions
- **Implementation**: Enhanced metadata synchronization in `SessionManager.kt` with:
  - Expanded metadata download scope including all CategoryOptionCombo dependencies
  - Real-time foreign key violation detection using `d2.maintenanceModule().foreignKeyViolations()`
  - Automatic dependency resolution through metadata re-synchronization
  - Multi-data type support (aggregate, tracker, event)

### 3. Architecture Excellence ✅
- **MVVM with Jetpack Compose**: Modern Android architecture
- **Offline-First**: Room database with DHIS2 SDK integration
- **Type-Safe Design**: Sealed class `ProgramInstance` model supporting all instance types
- **Flow Context Management**: Fixed Flow invariant violations in coroutine contexts

## Current Implementation Status

### ✅ PRODUCTION READY
- **Authentication & Session Management**: Enhanced with SHA-256 offline authentication
- **Dataset Operations**: Complete data entry, instance management, sync operations
- **Tracker Program Support**: Auto-detection, filtering, enrollment creation navigation
- **Event Program Support**: Auto-detection, filtering, event creation navigation
- **Foreign Key Violation Handling**: Comprehensive CategoryOptionCombo constraint resolution
- **Offline Capabilities**: Complete offline-first architecture with Room caching

### ✅ CRITICAL BUG FIXES IMPLEMENTED
- **Flow Context Violations**: Fixed `Flow invariant is violated` errors in data retrieval
- **Foreign Key Constraints**: Resolved CategoryOptionCombo constraint violations blocking data storage
- **Navigation Issues**: Fixed FAB navigation for tracker and event program types
- **Program Type Detection**: Enhanced auto-detection of TRACKER vs EVENT vs DATASET programs

### ❌ PHASE 3+ DEVELOPMENT NEEDED
- **Tracker Data Entry Screens**: Full enrollment and attribute value entry forms
- **Event Data Entry Screens**: Event-specific data capture interfaces
- **Advanced Tracker Features**: Program rules, relationships, advanced sync

## Technical Implementation Details

### File Structure
```
presentation/
├── datasets/DatasetsScreen.kt              # Unified program listing with filtering
├── datasetInstances/DatasetInstancesScreen.kt  # Universal instance management
├── datasetInstances/DatasetInstancesViewModel.kt  # Unified state management
└── dataEntry/EditEntryScreen.kt            # Dataset-specific data entry

domain/model/
├── ProgramInstance.kt                       # Sealed class for all instance types
├── Program.kt                              # Universal program model
└── TrackedEntity.kt                        # Tracker-specific models

data/
├── SessionManager.kt                       # Enhanced with FK violation handling
├── repositoryImpl/DatasetsRepositoryImpl.kt     # Extended with tracker/event methods
└── repositoryImpl/DatasetInstancesRepositoryImpl.kt  # Unified instance handling
```

### Key Code Locations

#### Foreign Key Violation Handling (`SessionManager.kt`)
- **Lines 620-660**: Comprehensive metadata dependency resolution
- **Lines 807-924**: Foreign key violation detection and handling functions
- **Lines 866-924**: Targeted resolution strategies for different violation types

#### Unified Program Interface (`DatasetInstancesScreen.kt`)
- **Lines 402-454**: Enhanced FAB navigation logic for all program types
- **Lines 570-587**: Dynamic navigation based on program type detection

#### Program Type Support (`DatasetInstancesViewModel.kt`)
- **Lines 140-188**: Enhanced program type detection and initialization
- **Lines 200-250**: Unified state management for all instance types

## Development Guidelines

### When Working on This Project
1. **Maintain Backward Compatibility**: All dataset functionality must remain intact
2. **Follow Sealed Class Architecture**: Use `ProgramInstance` for type-safe instance handling
3. **Respect Offline-First Design**: Always load from Room database first
4. **Handle Foreign Key Violations**: Use enhanced `SessionManager` metadata sync
5. **Test All Program Types**: Verify functionality works for datasets, tracker, and event programs

### Common Patterns
- **Repository Pattern**: Extended repositories support multiple program types
- **Flow-Based State Management**: Use `.flowOn(Dispatchers.IO)` instead of `withContext`
- **Type-Safe Navigation**: Dynamic navigation based on program type detection
- **Comprehensive Logging**: Extensive logging for debugging complex DHIS2 SDK interactions

### Build & Test Commands
```bash
# Build project
./gradlew assembleDebug

# Run tests
./gradlew test

# Check for compilation issues
./gradlew compileDebugKotlin
```

## Known Technical Challenges Resolved

### 1. CategoryOptionCombo Foreign Key Violations
**Problem**: DataValues referencing missing CategoryOptionCombos causing storage failures
**Solution**: Enhanced metadata synchronization with dependency resolution in `SessionManager.kt`
**Evidence-Based**: Implementation based on DHIS2 SDK documentation and ANDROSDK-1592 issue resolution

### 2. Flow Context Violations
**Problem**: `Flow invariant is violated` errors in coroutine contexts
**Solution**: Replaced `withContext(Dispatchers.IO)` with `.flowOn(Dispatchers.IO)` in repository implementations

### 3. Program Type Auto-Detection
**Problem**: Incorrect navigation for tracker vs event programs
**Solution**: Enhanced `initializeWithProgramId()` with proper DHIS2 SDK program type detection

## Integration with DHIS2 Ecosystem

### DHIS2 SDK Integration
- **Version**: 2.7.0+
- **Authentication**: Standard DHIS2 credentials with enhanced offline support
- **Metadata Sync**: Complete metadata download including dependencies
- **Data Sync**: Multi-strategy download for robust data synchronization

### Server Compatibility
- **DHIS2 Versions**: 2.35+ (tested with 2.40.4, 2.41.4)
- **API Usage**: Standard DHIS2 Web API through Android SDK
- **Permissions**: Requires standard data entry and read permissions

## Future Development Roadmap

### Phase 3: Tracker Data Entry Implementation
- Create tracker-specific data entry screens
- Implement attribute value forms with validation
- Add enrollment management capabilities

### Phase 4: Advanced Features
- Program rules implementation
- Tracker relationships support
- Advanced offline sync for tracker data
- Enhanced analytics and reporting

---

*This document provides comprehensive context for Claude AI when working on the SimpleDataEntry DHIS2 Android application. All technical implementation details, resolved issues, and architectural decisions are documented for efficient development continuation.*