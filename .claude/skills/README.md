# SimpleDataEntry Claude Code Skills

Custom skills for DHIS2 Android development with adaptive learning and interactive fixes.

## Overview

This project has **7 specialized skills** that work together to:
1. **Prevent bugs** before they reach runtime
2. **Accelerate development** with code generation
3. **Learn your patterns** and adapt over time
4. **Provide interactive fixes** with clear explanations

---

## Skills Summary

### Safety & Debugging Skills (Auto-Activate)

#### 1. DHIS2 SDK Integration Helper
**Location:** `.claude/skills/dhis2-sdk-integration/`

**Purpose:** Detect unsafe DHIS2 SDK patterns and generate safe async wrappers.

**Auto-triggers:**
- Files with `import org.hisp.dhis.android.core`
- Code with `blockingGet()`, `blockingCount()`, etc.
- Inside `viewModelScope.launch` or `lifecycleScope.launch` blocks

**What it detects:**
- ❌ Blocking SDK calls in coroutine scopes (ANR risk)
- ❌ Blocking calls in loops (performance killer)
- ❌ withContext inside Flow builders (anti-pattern)
- ❌ Missing error handling in SDK calls

**Example fix:**
```kotlin
// Before (UNSAFE)
viewModelScope.launch {
    val data = d2.dataElementModule().blockingGet()  // ANR!
}

// After (SAFE)
viewModelScope.launch {
    val data = withContext(Dispatchers.IO) {
        d2.dataElementModule().blockingGet()
    }
}
```

---

#### 2. Android Coroutine Safety Auditor
**Location:** `.claude/skills/android-coroutine-safety/`

**Purpose:** Auto-scan for threading/coroutine issues and calculate ANR risk scores.

**Auto-triggers:**
- Files in `presentation/` directory saved
- Contains `viewModelScope`, `lifecycleScope`, `suspend fun`
- Build errors mentioning "thread", "coroutine", "dispatcher"

**What it detects:**
- Blocking operations in coroutine scopes
- withContext inside Flow (should use flowOn)
- Missing exception handling
- GlobalScope usage (never use!)
- Incorrect dispatcher for task type

**ANR Risk Scoring:**
```
risk_score = (
    blocking_call_severity * 40 +
    call_frequency * 30 +
    data_volume * 20 +
    missing_io_dispatcher * 40 +
    in_loop_penalty * 30
)

Severity: CRITICAL (≥80) | HIGH (≥50) | MEDIUM (<50)
```

---

#### 3. Navigation Route Validator
**Location:** `.claude/skills/navigation-route-validator/`

**Purpose:** Verify navigation routes exist before runtime and generate type-safe helpers.

**Auto-triggers:**
- `AppNavigation.kt` modified (rebuild route registry)
- Files with `navController.navigate()` saved

**What it validates:**
- ✓ Route pattern exists in AppNavigation.kt
- ✓ All required parameters provided
- ✓ URL encoding applied to string parameters

**Generates type-safe helpers:**
```kotlin
// Instead of manual strings:
navController.navigate("CreateEvent/$id/$name")

// Generate type-safe helpers:
navController.navigateToCreateEvent(programId, programName)
```

---

### Development Acceleration Skills (On-Demand)

#### 4. Clean Architecture Component Builder
**Location:** `.claude/skills/android-clean-architecture/`

**Usage:** `/generate-feature <FeatureName>`

**Generates:**
- Domain layer: Models + Repository interfaces
- Data layer: Entities + DAOs + Repository implementations
- Presentation layer: State + ViewModel + Screen
- DI: Hilt module updates
- Navigation: Route registration

**Example:**
```bash
/generate-feature TrackerDashboard
```

Generates 12 files following your established patterns (MVVM, Hilt DI, Room, Flow-based repositories).

---

#### 5. ViewModel Pattern Generator
**Location:** `.claude/skills/android-viewmodel-generator/`

**Usage:** `/generate-viewmodel <Name> [DataClass|SealedClass]`

**Generates:**
- State data class or sealed class (based on your preference)
- ViewModel with StateFlow
- Repository integration
- Sync progress observation
- Loading/error handling

**Example:**
```bash
/generate-viewmodel EventsTable DataClass
```

Follows your patterns (85% data class, 15% sealed class).

---

#### 6. Jetpack Compose Screen Builder
**Location:** `.claude/skills/android-compose-screen/`

**Usage:** `/generate-screen <ScreenName> <Type>`

**Types:** `List`, `Table`, `Form`, `Dashboard`

**Generates:**
- Complete Composable screen
- Scaffold with TopAppBar
- State collection from ViewModel
- Navigation integration
- Loading/error states

**Example:**
```bash
/generate-screen EventsList Table
```

Learns from your existing screens (EventsTableScreen, TrackerEnrollmentTableScreen, etc.).

---

#### 7. Compose UI Component Library
**Location:** `.claude/skills/android-compose-components/`

**Usage:** `/generate-component <ComponentType>`

**Types:** `DataTable`, `SearchBar`, `FilterDialog`, `LoadingIndicator`, `ErrorCard`, `SyncProgress`

**Generates:**
- Reusable Composable components
- Matches your design system
- Follows your modifier conventions
- Accessibility support

**Example:**
```bash
/generate-component DataTable
```

Based on your TrackerEnrollmentTableScreen pattern.

---

## Cross-Skill Synergy

When you generate code, all safety skills automatically verify it:

```
1. /generate-feature TrackerDashboard
   → Clean Architecture Builder generates files

2. DHIS2 SDK Helper auto-scans
   → ✓ Validates all SDK calls use proper async patterns

3. Coroutine Safety Auditor checks ViewModel
   → ✓ Validates viewModelScope usage

4. Navigation Validator updates registry
   → ✓ Adds new route to cache

5. All checks pass → Ready to use!
```

---

## Pattern Learning System

All skills share learned patterns:

**File:** `.claude/skills/dhis2-sdk-integration/patterns/learned-user-patterns.json`

```json
{
  "user_id": "sean",
  "project": "simpleDataEntry",
  "learned_patterns": {
    "blocking_sdk_calls": {
      "preferred_pattern": "repository_suspend_withContext",
      "confidence": 0.85,
      "examples": ["DatasetInstancesRepositoryImpl.kt:30-40"]
    },
    "repository_pattern": {
      "pattern": "Flow<List<T>> with Room DAO",
      "confidence": 0.95
    }
  }
}
```

**Confidence Scoring:**
- 0.0-0.3: Unknown (ask user)
- 0.3-0.6: Emerging (suggest with options)
- 0.6-0.9: Strong (auto-apply with confirmation)
- 0.9-1.0: Established (auto-apply silently)

**Reinforcement:**
- User accepts: +0.20
- User writes similar code: +0.30
- User rejects: -0.10
- Consistent usage (3+): +0.30

---

## How to Use

### Automatic (Safety Skills)

Just code normally! The skills activate automatically when you:
- Save files with DHIS2 SDK imports
- Modify presentation layer files
- Update AppNavigation.kt

### On-Demand (Generator Skills)

Use slash commands in Claude Code:

```bash
# Generate entire feature
/generate-feature TrackerDashboard

# Generate ViewModel only
/generate-viewmodel EventsTable DataClass

# Generate screen only
/generate-screen EventsList Table

# Generate component only
/generate-component DataTable
```

---

## Files Structure

```
.claude/skills/
├── README.md (this file)
├── dhis2-sdk-integration/
│   ├── skill.md
│   ├── patterns/
│   │   ├── learned-user-patterns.json
│   │   └── safe-wrapper-templates.kt
│   └── config/
│       └── auto-triggers.json
├── android-coroutine-safety/
│   ├── skill.md
│   └── rules/
├── navigation-route-validator/
│   ├── skill.md
│   ├── cache/
│   │   └── valid-routes.json
│   └── generators/
├── android-clean-architecture/
│   ├── skill.md
│   └── templates/
├── android-viewmodel-generator/
│   ├── skill.md
│   └── templates/
├── android-compose-screen/
│   ├── skill.md
│   └── templates/
└── android-compose-components/
    ├── skill.md
    └── templates/
```

---

## Success Criteria

**Detection Accuracy:**
- Blocking calls: 95%+ detection rate
- Invalid routes: 100% (parse-based)
- ANR correlation: 85%+ match with crashes

**Fix Quality:**
- Compile success: 98%+
- Runtime success: 95%+
- Performance improvement: Measurable in 80%+ cases

**Learning Efficiency:**
- Time to 0.9 confidence: < 5 examples
- False positive rate: < 5%
- User acceptance: 80%+ of suggestions

**Developer Velocity:**
- Bug-fix time: 30-50% reduction
- Issues caught before runtime: 70%+
- Boilerplate reduction: 60%+

---

## Quick Reference

### DHIS2 SDK Safe Usage
```kotlin
✅ DO: withContext(Dispatchers.IO) { d2.module().blockingGet() }
❌ DON'T: viewModelScope.launch { d2.module().blockingGet() }

✅ DO: flow { emit(data) }.flowOn(Dispatchers.IO)
❌ DON'T: flow { withContext(IO) { emit(data) } }

✅ DO: Batch fetch + cache
❌ DON'T: Loop with blocking calls
```

### Coroutine Safety
```kotlin
✅ DO: viewModelScope.launch
❌ DON'T: GlobalScope.launch

✅ DO: withContext(Dispatchers.IO) for database/network
❌ DON'T: withContext(Dispatchers.Main) for heavy work

✅ DO: try-catch in coroutines
❌ DON'T: Unhandled exceptions
```

### Navigation
```kotlin
✅ DO: Type-safe helpers
❌ DON'T: Manual string concatenation

✅ DO: URLEncoder.encode() for names
❌ DON'T: Raw strings with spaces
```

---

## Troubleshooting

**Skills not activating?**
- Check file patterns match (e.g., `*ViewModel.kt`)
- Verify imports are present (e.g., `org.hisp.dhis.android.core`)
- Check auto-trigger settings in `config/auto-triggers.json`

**Wrong patterns suggested?**
- Reject suggestion (lowers confidence)
- Write code manually in your preferred style
- Skills will learn and adapt (requires 3+ examples)

**Want to reset learning?**
- Delete `learned-user-patterns.json`
- Skills will re-learn from scratch

---

## Contributing Patterns

Found a new pattern? Add it to:
`.claude/skills/{skill-name}/patterns/sdk-call-examples.json`

Or let the skills learn automatically by:
1. Writing code in your preferred style
2. Accepting/rejecting suggestions
3. Skills update confidence scores automatically

---

## Next Steps

1. ✅ All 7 skills created and ready to use
2. Start coding - safety skills will auto-activate
3. Try generator commands (`/generate-feature`, etc.)
4. Watch skills learn your patterns over time
5. Enjoy faster, safer development!

**Happy Coding!** 🚀