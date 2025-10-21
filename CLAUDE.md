# SimpleDataEntry DHIS2 Android App - Claude Context

**Last Updated**: 2025-10-13
**Status**: Production-ready for datasets and tracker enrollments. Event data entry functional but event table has broken navigation.

## Project Overview

**SimpleDataEntry** is an Android application for DHIS2 data collection with offline-first architecture. Supports aggregate datasets, tracker programs, and event programs using DHIS2 Android SDK v2.7.0+.

## Current Implementation Status

### ✅ WORKING FEATURES

**Dataset Data Entry** (Aggregate):
- Complete CRUD operations for dataset instances
- Offline data entry with sync
- Period and org unit selection
- CategoryOptionCombo support

**Tracker Program Support**:
- Enrollment listing in table/pivot view (`TrackerEnrollmentTableScreen`)
- Column customization and persistence
- Search and sort functionality
- 22 enrollments displaying correctly in test instance

**Event Data Capture**:
- Event data entry screen (`EventCaptureScreen`) fully functional
- ✅ **Option sets working**: Dropdowns, radio buttons, YES/NO buttons based on data element configuration
- ✅ **Text entry bug fixed**: Cursor position preserved (no more backwards typing)
- Data values save correctly

**Authentication**:
- Online/offline login with SHA-256 password validation
- Secure credential storage

### ⚠️ BROKEN/INCOMPLETE

**EventsTableScreen** (`presentation/tracker/EventsTableScreen.kt`):
- ❌ **Line 126**: FAB navigation route `"EventCapture/$programId/null/null/null"` DOES NOT EXIST
  - Should be: `"CreateEvent/$programId/$programName"`
- ❌ **Line 186-189**: Edit navigation route format doesn't match any actual route
  - Should be: `"EditStandaloneEvent/$programId/$programName/${row.id}"`

**EventsTableViewModel** (`presentation/tracker/EventsTableViewModel.kt`):
- ⚠️ **Line 113**: Redundant `.filterIsInstance<ProgramInstance.EventInstance>()` - data already correct type
- ⚠️ **Lines 164-172**: `blockingGet()` called in Flow/coroutine context - will block thread
- ⚠️ **Performance issue**: `buildColumns()` called on every search/sort, re-fetching data element names repeatedly

**Program Rules**:
- Evaluation code commented out in `EventCaptureViewModel.kt` (lines 324-395)
- TODO markers for future implementation with correct DHIS2 SDK APIs

## Architecture

### Core Models
- **`ProgramInstance`** (sealed class): Type-safe representation of datasets, tracker enrollments, and events
- **`TrackedEntityDataValue`**: Event data values (dataElement, value, event)
- **`TrackedEntityAttributeValue`**: Tracker enrollment attributes (id, displayName, value)

### Key Navigation Routes
From `AppNavigation.kt`:
```kotlin
// Datasets
"DatasetInstances/{datasetId}/{datasetName}"

// Tracker enrollments (table view)
"TrackerEnrollments/{programId}/{programName}"

// Events (BROKEN - needs fixing)
"EventsTable/{programId}/{programName}"

// Create/Edit Events
"CreateEvent/{programId}/{programName}"
"CreateEvent/{programId}/{programName}/{programStageId}"
"EditStandaloneEvent/{programId}/{programName}/{eventId}"
"EditEvent/{programId}/{programName}/{eventId}/{enrollmentId}"
```

### Repository Pattern
- `DatasetInstancesRepository`: Interface for all program instance operations
- `DatasetInstancesRepositoryImpl`: Implementation using DHIS2 SDK
- Returns `Flow<List<ProgramInstance>>` for reactive data loading

## Critical Development Rules

### NEVER Do These
1. ❌ Use `blockingGet()` in coroutine/Flow contexts - causes ANR
2. ❌ Call database operations repeatedly in loops (cache instead)
3. ❌ Navigate to routes without verifying they exist in `AppNavigation.kt`
4. ❌ Claim features work without building and testing
5. ❌ Break existing working functionality when adding features

### ALWAYS Do These
1. ✅ Verify navigation routes in `AppNavigation.kt` before using
2. ✅ Use suspending coroutines for database operations
3. ✅ Test builds with `./gradlew assembleDebug` before claiming completion
4. ✅ Use `.flowOn(Dispatchers.IO)` not `withContext` for Flow operations
5. ✅ Read existing working code patterns before implementing new features
6. use the bd tool instead of markdown for all new work and issue trackingclaude

## Recent Session Work (2025-10-13)

### Completed
1. ✅ Fixed text entry reversal bug in `EventCaptureScreen.kt` (cursor position preservation)
2. ✅ Implemented option sets for events (dropdowns, radio buttons, YES/NO buttons)
3. ✅ Commented out broken program rules code with TODO markers

### Created But Broken
1. ❌ `EventsTableScreen.kt` - UI exists but navigation routes are wrong
2. ❌ `EventsTableViewModel.kt` - Has blocking calls and performance issues

### Next Priority Fixes
1. Fix EventsTableScreen navigation (lines 126, 186-189)
2. Remove blocking calls from EventsTableViewModel (lines 164-172)
3. Optimize column building to cache data element names
4. Remove redundant `.filterIsInstance` call (line 113)

## Test Instance Status
- User: `adilanghciii` with offline access
- 1 tracker program: `QZkuUuLedjh` with 22 enrollments working correctly
- 0 event programs (EventsTable untested with real data)
- 0 datasets

## Build Status
✅ Code compiles successfully
⚠️ Runtime navigation will crash for EventsTable
⚠️ Performance issues with blocking calls