# Testing Guide for Simple Data Entry

This document provides comprehensive information about the test suite for the Android DHIS2 data entry application.

## Test Architecture Overview

The test suite follows the project's clean architecture pattern with tests organized by layer:

### 📁 Test Structure
```
app/src/
├── test/java/com/ash/simpledataentry/           # Unit tests
│   ├── testutil/                               # Test utilities and builders
│   │   ├── TestDataBuilders.kt                # Test data factory
│   │   └── TestExtensions.kt                  # Test helpers and rules
│   ├── domain/                                # Domain layer tests
│   │   ├── useCase/                           # Use case tests
│   │   └── validation/                        # Validation service tests
│   ├── presentation/                          # ViewModel tests
│   │   ├── dataEntry/
│   │   └── login/
│   └── data/                                  # Data layer unit tests
│       └── repositoryImpl/
└── androidTest/java/com/ash/simpledataentry/   # Instrumentation tests
    ├── data/local/                            # DAO tests
    └── presentation/                          # UI component tests
```

## Test Categories

### 🔧 Unit Tests (JVM)
**Location**: `app/src/test/`

**Dependencies Added:**
- JUnit 4
- Mockito & Mockito-Kotlin
- Kotlinx Coroutines Test  
- AndroidX Arch Core Testing
- Turbine (Flow testing)
- Google Truth (assertions)
- Hilt Testing

**Coverage:**
- **Domain Use Cases**: Business logic validation
- **ViewModels**: StateFlow behavior and UI state management
- **Repositories**: Data layer logic with mocked dependencies
- **Validation Service**: DHIS2 validation rule processing

### 📱 Instrumentation Tests (Android)
**Location**: `app/src/androidTest/`

**Dependencies Added:**
- AndroidX Test Framework
- Compose UI Testing
- Room Testing
- Hilt Android Testing
- Mockito Android

**Coverage:**
- **DAO Tests**: Database operations with in-memory Room database
- **UI Component Tests**: Compose screens and interactions
- **Integration Tests**: End-to-end scenarios

## Key Test Features

### 🏗️ Test Data Builders
**File**: `TestDataBuilders.kt`

Provides factory methods for creating consistent test data:
```kotlin
// Create test dataset
val dataset = TestDataBuilders.createTestDataset(
    uid = "dataset123",
    displayName = "Test Dataset"
)

// Create test validation result
val validation = TestDataBuilders.createTestValidationResult(
    type = ValidationResultType.SUCCESS
)
```

### ⚡ Test Utilities
**File**: `TestExtensions.kt`

- **MainDispatcherRule**: Sets test dispatcher for coroutines
- **Constants**: Shared test data (dataset IDs, periods, etc.)
- **Helper Functions**: Common test operations

### 🌊 Flow Testing with Turbine
StateFlow and Flow testing made simple:
```kotlin
viewModel.state.test {
    val initialState = awaitItem()
    viewModel.login()
    val loadingState = awaitItem() 
    val successState = awaitItem()
    assertThat(successState.isLoggedIn).isTrue()
}
```

### 🎯 Mockito with Kotlin
Proper mocking with Kotlin-friendly syntax:
```kotlin
whenever(repository.login(any())).thenReturn(Result.success(Unit))
verify(repository).login(config)
```

## Test Scenarios Covered

### 🔐 Authentication Tests
- Login with valid/invalid credentials
- Account saving with encryption
- Multi-account management
- Session state management

### 📊 Data Entry Tests  
- Data value CRUD operations
- Draft saving and restoration
- Validation rule processing
- Offline-first data flow

### ✅ Validation Tests
- DHIS2 SDK validation integration
- Rule expression parsing
- Error/warning handling
- Completion state logic

### 🎨 UI Component Tests
- Login screen interactions
- Dataset list display
- Validation result dialogs
- Loading and error states

### 💾 Database Tests
- Room DAO operations
- Data persistence
- Query filtering
- Migration scenarios

## Running Tests

### Unit Tests
```bash
./gradlew test                    # Run all unit tests
./gradlew testDebugUnitTest      # Run debug unit tests only
```

### Instrumentation Tests  
```bash
./gradlew connectedAndroidTest   # Run on connected device/emulator
./gradlew connectedDebugAndroidTest  # Run debug instrumentation tests
```

### Specific Test Classes
```bash
./gradlew test --tests="*DataEntryViewModelTest*"
./gradlew connectedAndroidTest --tests="*LoginScreenTest*"
```

## Test Configuration

### Build Dependencies
The following testing dependencies have been added to `app/build.gradle`:

```gradle
// Unit testing
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.7.0'
testImplementation 'org.mockito.kotlin:mockito-kotlin:5.2.1'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
testImplementation 'androidx.arch.core:core-testing:2.2.0'
testImplementation 'app.cash.turbine:turbine:1.0.0'
testImplementation 'com.google.truth:truth:1.1.4'

// Hilt testing
testImplementation 'com.google.dagger:hilt-android-testing:2.56.2'
kspTest 'com.google.dagger:hilt-android-compiler:2.56.2'

// Instrumentation testing
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
androidTestImplementation 'androidx.test:core:1.5.0'
androidTestImplementation 'androidx.compose.ui:ui-test-junit4'

// Hilt instrumentation testing  
androidTestImplementation 'com.google.dagger:hilt-android-testing:2.56.2'
kspAndroidTest 'com.google.dagger:hilt-android-compiler:2.56.2'
```

## Test Best Practices

### ✅ What We Test Well
1. **Business Logic**: Use cases and validation rules
2. **State Management**: ViewModel StateFlow emissions  
3. **Data Persistence**: Room database operations
4. **UI Interactions**: Compose component behavior
5. **Error Handling**: Exception scenarios and recovery

### 🎯 Testing Principles Used
1. **AAA Pattern**: Arrange, Act, Assert structure
2. **Test Data Builders**: Consistent, readable test data creation
3. **Mocking**: Isolated unit testing with dependencies mocked
4. **Flow Testing**: Proper StateFlow/Flow testing with Turbine
5. **Truth Assertions**: Readable, fluent assertion syntax

### 📝 Test Naming Convention
- **Unit Tests**: `methodName_should_expectedBehavior_when_condition`
- **UI Tests**: `componentName_behavior_condition`
- **Integration Tests**: `feature_scenario_expectedOutcome`

Examples:
```kotlin
fun login_should_returnSuccess_when_credentialsValid()
fun loginScreen_displaysErrorMessage_whenAuthenticationFails()
fun dataEntry_savesAndSync_whenNetworkAvailable()
```

## Continuous Integration

The test suite is designed to run in CI/CD pipelines:

1. **Unit Tests**: Fast, run on every commit
2. **Instrumentation Tests**: Run on release branches
3. **Coverage Reports**: Generated for test coverage analysis
4. **Test Reports**: JUnit XML format for CI integration

## Coverage Goals

- **Unit Tests**: 80%+ coverage for business logic
- **Integration Tests**: Key user journeys covered
- **UI Tests**: Critical screens and interactions tested
- **Database Tests**: All DAO operations verified

## Troubleshooting

### Common Issues
1. **Coroutine Tests**: Use `MainDispatcherRule` and `runTest`
2. **Room Tests**: Use `inMemoryDatabaseBuilder()` 
3. **Compose Tests**: Use `createComposeRule()` and proper test tags
4. **Hilt Tests**: Annotate test classes with `@HiltAndroidTest`

### Performance Tips
1. Use `@Before`/`@After` for test setup/cleanup
2. Mock expensive operations (network, database)
3. Use test doubles instead of real implementations
4. Keep tests focused and fast

## Future Enhancements

- [ ] Property-based testing with Kotest
- [ ] Screenshot testing for UI regression detection
- [ ] Performance testing for large datasets
- [ ] End-to-end testing with real DHIS2 instances
- [ ] Accessibility testing integration

---

This comprehensive test suite ensures the reliability, maintainability, and quality of the Simple Data Entry Android application.