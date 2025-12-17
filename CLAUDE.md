# SimpleDataEntry DHIS2 Android App - Claude Context

**Last Updated**: 2025-12-11
**Status**: Production-ready for datasets and tracker enrollments. Login flow resilient with retry logic.

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

**Authentication & Account Management**:
- Online/offline login with SHA-256 password validation
- Secure credential storage
- ✅ **Multiple account support**: Each account has separate Room database (simpleDataEntry-11)
- ✅ **Data isolation**: No cross-contamination between accounts via separate `room_{accountId}.db` files
- ✅ **Saved accounts shared**: All saved accounts visible across login/settings (uses shared database)
- ✅ **Resilient login flow**: Metadata download retries up to 3 times with user-friendly error messages

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

### Account Isolation (NEW - 2025-12-04)
- **`AccountManager`** (`data/AccountManager.kt`): Manages multiple DHIS2 accounts
  - Generates stable MD5-based account IDs from `username@serverUrl`
  - Stores account metadata in SharedPreferences (JSON)
  - Each account gets unique database names: `room_{accountId}.db`
- **`DatabaseManager`** (`data/DatabaseManager.kt`): Account-specific Room database lifecycle
  - Thread-safe database switching with Mutex
  - Closes old database, opens new one on account change
  - Only ONE Room database open at a time (memory efficient)
- **Critical**: D2 SDK database is SHARED (API limitation), Room database is ISOLATED per account

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
6. ✅ **Use Beads for ALL issue tracking and documentation** - NO new markdown files in project
7. ✅ Check Beads issues for current status and context before starting work

## Recent Session Work (2025-12-11)

### COMPLETED - Login Flow Resilience Fix ✅
**Plan File**: `/Users/sean/.claude/plans/parsed-cooking-taco.md`
**Status**: ✅ Implemented, built, installed, and verified working

**Problem Solved**: Login completes but metadata/data doesn't download on some DHIS2 servers
- Metadata download failed at 41% with "NullPointerException: Null attribute"
- UI showed nothing after login (empty dataset list)
- BackgroundSyncWorker failed to instantiate due to WorkManager/Hilt conflict

**Root Cause Analysis**:
- SDK metadata download is **incremental** (not transactional) - saves as it goes
- When error occurred at 41%, OrgUnits/Programs/Datasets hadn't been downloaded yet
- Official DHIS2 Capture app uses retry logic and `WAS_INITIAL_SYNC_DONE` flag pattern
- WorkManager's default initializer was running before Hilt could provide workers

**Fixes Applied**:

1. **`SessionManager.kt` - Retry Logic** (lines ~807-888):
   - Rewrote `downloadMetadataResilient()` to retry up to 3 times
   - 2-second delay between retry attempts
   - Checks for usable metadata (OrgUnits, Programs, or Datasets) after each attempt
   - Clear progress updates: "Retrying... (attempt 2)"

2. **`SessionManager.kt` - User-Friendly Errors** (lines 382-402):
   - "Null attribute" → "Server has invalid metadata configuration..."
   - Timeout → "Connection timed out. Please check your internet..."
   - Unable to resolve host → "Cannot reach server..."
   - 401/Unauthorized → "Authentication failed..."

3. **`AndroidManifest.xml` - WorkManager Fix** (lines 25-35):
   - Added provider block to disable default WorkManagerInitializer
   - Allows Hilt to provide `@HiltWorker`-annotated `BackgroundSyncWorker`
   - Fixes `NoSuchMethodException: BackgroundSyncWorker.<init>` error

**Build & Test Status**:
- ✅ `./gradlew assembleDebug` SUCCESS
- ✅ APK installed on device SUCCESS
- ✅ **RUNTIME VERIFIED WORKING** by user

---

### Previous Work (2025-12-04)

**Dataset Race Condition Fix** (simpleDataEntry-19):
- Changed `SessionManager.kt`: `.apply()` → `.commit()` for synchronous SharedPreferences write
- Fixed cache validation clearing datasets from Room database

**Account Isolation** (simpleDataEntry-11):
- `AccountManager.kt` - Multi-account management with MD5-based stable IDs
- `DatabaseManager.kt` - Account-specific Room database lifecycle
- D2 SDK database shared, Room database isolated per account

### Known Issues (Lower Priority)
1. ❌ `EventsTableScreen.kt` - Navigation routes broken (lines 126, 186-189)
2. ❌ `EventsTableViewModel.kt` - Blocking calls and performance issues (lines 113, 164-172)

## Test Instance Status
- User: `android@emisuganda.org` (or `adilanghciii`) with offline access
- 1 tracker program: `QZkuUuLedjh` with 22 enrollments working correctly
- Datasets: Working after race condition fix
- Login flow: Resilient across different DHIS2 server configurations

## Build Status
✅ Code compiles successfully (verified 2025-12-11)
✅ Login flow resilience implemented and verified working
✅ Account isolation implementation complete
✅ Dataset race condition fix verified
⚠️ EventsTable navigation still broken (lower priority)

## Documentation Policy
**CRITICAL FOR ALL SESSIONS**:
- ✅ Use Beads issues for ALL documentation and tracking
- ❌ DO NOT create new markdown files in the project
- ✅ Update Beads design/notes fields with implementation details
- ✅ Check existing Beads issues before starting work
- Reference: Architectural audit in `/Users/sean/.claude/plans/iridescent-rolling-feigenbaum.md`

## Next Priority Work
From architectural audit (see Beads issues simpleDataEntry-12 through simpleDataEntry-18):
1. **Priority 1 Remaining**:
   - simpleDataEntry-12: Implement SyncStatusController
   - simpleDataEntry-13: Global Top Bar Progress Indicator

2. **Priority 2**:
   - simpleDataEntry-14: Shimmer Placeholders
   - simpleDataEntry-15: Live Count Updates
   - simpleDataEntry-16: Per-Program Progress Display

3. **Priority 3**:
   - simpleDataEntry-17: WorkManager Migration
   - simpleDataEntry-18: WorkManager Progress Integration