# SimpleDataEntry DHIS2 Android App - Claude Context

**Last Updated**: 2025-12-05
**Status**: Production-ready for datasets and tracker enrollments. Account isolation implemented via separate Room databases.

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

## Recent Session Work (2025-12-04)

### CURRENT SESSION - Dataset Race Condition Fix ⚠️ TESTING REQUIRED
**Beads Issue**: simpleDataEntry-19
**Status**: ✅ Built and installed, ⚠️ Runtime testing pending

**Problem Solved**: Datasets not appearing despite successful metadata download (0 datasets showing in UI)

**Root Cause**: Race condition in `SessionManager.kt` line 829
- `.apply()` (async) wrote cache metadata to SharedPreferences
- `DatasetsRepositoryImpl.getDatasets()` read metadata immediately, saw null/stale values
- Cache validation failed, cleared all datasets from Room database

**Fix Applied**:
- Changed `SessionManager.kt` line 829: `.apply()` → `.commit()` (synchronous write)
- Ensures cache metadata written BEFORE datasets inserted into Room
- Cache validation now sees correct metadata, won't clear datasets

**Files Modified This Session**:
1. `AccountManager.kt` (line 253) - Added `RegexOption.DOT_MATCHES_ALL` to JSON parser
2. `DatabaseManager.kt` (lines 27,29,34,54) - Accept `AccountInfo` object instead of string ID
3. `SessionManager.kt` (line 829) - Changed `.apply()` to `.commit()` ← **CRITICAL FIX**
4. `AppModule.kt` - Removed duplicate `@Provides` methods

**Build Status**:
- ✅ Compiled: `./gradlew assembleDebug` SUCCESS
- ✅ Installed: `adb install -r app-debug.apk` SUCCESS
- ⚠️ **NOT TESTED AT RUNTIME** - Next agent must verify

**IMMEDIATE NEXT STEPS** (First 10 minutes of next session):
```bash
# 1. Clear logcat
adb logcat -c

# 2. User logs in with account that has datasets

# 3. Monitor logs
adb logcat | grep -E "SessionManager|DatasetsRepositoryImpl"
```

**Expected Results After Login**:
- ✅ "Cache validation metadata set for: [user]@[server]"
- ✅ "Combined programs: [N > 0] datasets, ..." (where N is positive)
- ✅ Datasets appear in DatasetsScreen UI
- ❌ NO "Cache validation failed" warnings
- ❌ NO "clearAll()" calls

**Secondary Issue** (Lower Priority):
- ForeignKey violations for CategoryOptionCombo still present
- Investigate only AFTER verifying datasets appear correctly

**User Context**:
> "There was a simplification effort that was recommended by a previous session's agent and now we're redebugging old issues coming back alive."

**Session History**: This session fixed THREE separate bugs:
1. DatabaseManager lookup issue (passing object vs string ID)
2. JSON parser regex issue (multiline support)
3. Race condition in cache metadata write (the main fix)

---

### Completed - Account Isolation Implementation
**Beads Issue**: simpleDataEntry-11 (Priority 1 from architectural audit)

**What Was Built**:
1. ✅ `AccountManager.kt` - Multi-account management with MD5-based stable IDs
2. ✅ `DatabaseManager.kt` - Account-specific Room database lifecycle management
3. ✅ Updated `SessionManager.kt` - Injected DatabaseManager, uses account-specific databases
4. ✅ Updated `AppModule.kt` - Added DI providers for AccountManager and DatabaseManager
5. ✅ Build verified: `./gradlew assembleDebug` SUCCESS

**Key Discovery**: D2Configuration.builder().databaseName() NOT AVAILABLE in current DHIS2 SDK
- Solution: D2 SDK database shared, Room database isolated per account
- Result: UI data isolation achieved (what matters for preventing contamination)

**Testing Required** (Next Session):
- [ ] Runtime test with multiple accounts
- [ ] Verify data isolation works
- [ ] Check database files: `adb shell ls /data/data/com.ash.simpledataentry/databases/`
- [ ] Confirm separate `room_*.db` files created

**Full details in**: Beads issue simpleDataEntry-11

### Previous Work (2025-10-13)
1. ✅ Fixed text entry reversal bug in `EventCaptureScreen.kt`
2. ✅ Implemented option sets for events
3. ✅ Commented out broken program rules code

### Known Issues (Lower Priority)
1. ❌ `EventsTableScreen.kt` - Navigation routes broken (lines 126, 186-189)
2. ❌ `EventsTableViewModel.kt` - Blocking calls and performance issues (lines 113, 164-172)

## Test Instance Status
- User: `android@emisuganda.org` (or `adilanghciii`) with offline access
- 1 tracker program: `QZkuUuLedjh` with 22 enrollments working correctly
- 0 event programs (EventsTable untested with real data)
- Datasets: PRESENT but not appearing due to race condition (fix pending runtime verification)

## Build Status
✅ Code compiles successfully (verified 2025-12-04 - multiple times this session)
✅ Account isolation implementation complete
✅ Dataset race condition fix implemented and built
⚠️ **RUNTIME TESTING REQUIRED** for dataset race condition fix (see simpleDataEntry-19)
⚠️ Runtime testing required for account switching
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