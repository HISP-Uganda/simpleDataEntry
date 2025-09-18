# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Common Development Commands

### Build & Run
```bash
# Build the project
./gradlew build

# Run unit tests (fast, JVM-based)
./gradlew test

# Run instrumentation tests (requires emulator/device)
./gradlew connectedAndroidTest

# Install debug APK on connected device/emulator
./gradlew installDebug

# Clean build artifacts
./gradlew clean

# Run a specific test class
./gradlew test --tests "*.DataEntryViewModelTest"

# Run single instrumentation test
./gradlew connectedAndroidTest --tests "*.LoginScreenTest"
```

### Code Quality
```bash
# Lint the code
./gradlew lint

# Format code (if ktfmt/ktlint is configured)
./gradlew ktfmtFormat

# Check dependencies for updates
./gradlew dependencyUpdates
```

## Architecture Overview

This is an **offline-first Android DHIS2 data entry application** built with modern Android architecture:

### Core Architecture Pattern
- **MVVM (Model-View-ViewModel)** with **Clean Architecture** layers
- **Jetpack Compose** for UI with state management via ViewModels
- **Room Database** as the primary data source for offline-first functionality
- **Hilt** for dependency injection
- **DHIS2 Android SDK** for server communication and metadata handling

### Layer Structure
```
app/src/main/java/com/ash/simpledataentry/
├── presentation/          # UI screens, ViewModels, Compose components
│   ├── login/            # Authentication screens
│   ├── datasets/         # Dataset listing screens
│   ├── datasetInstances/ # Dataset instances management
│   ├── dataEntry/        # Data entry form screens
│   └── settings/         # App settings and configuration
├── domain/               # Business logic, use cases, repository interfaces
├── data/                # Repository implementations, Room entities/DAOs
│   ├── local/           # Room database components
│   ├── repositoryImpl/  # Repository implementations
│   ├── cache/           # Metadata caching services
│   └── sync/            # Background sync and network handling
├── di/                  # Hilt dependency injection modules
└── util/               # Utility classes and helpers
```

### Key Architectural Principles
- **Room-first data access**: All screens load from Room database first for instant offline access
- **DHIS2 SDK hydration**: After login/sync, metadata is fetched from DHIS2 SDK and cached in Room
- **Draft-based editing**: All data entry creates local drafts before syncing to server
- **Dependency injection**: All repositories and use cases provided via Hilt modules in `AppModule.kt`

## Offline-First Data Flow

The app follows a strict offline-first approach:

1. **Primary Data Source**: Room database (instant access)
2. **Hydration Process**: DHIS2 SDK → Room cache (after login/sync)
3. **Fallback Logic**: Only fetch from DHIS2 SDK if Room is empty and device is online
4. **Draft Management**: All data entry saved as local drafts first, synced later

### Key Room Entities
- `DatasetEntity` - DHIS2 datasets metadata
- `DataElementEntity` - Form fields and validation rules
- `CategoryComboEntity` / `CategoryOptionComboEntity` - Category combinations
- `OrganisationUnitEntity` - Organization units
- `DataValueEntity` - Synced data values from server
- `DataValueDraftEntity` - Local drafts before sync

## Development Guidelines

### Testing Strategy
- **Unit Tests** (`app/src/test/`): Business logic, ViewModels, repositories
- **Instrumentation Tests** (`app/src/androidTest/`): UI components, database operations
- **Modern Testing Stack**: JUnit 4, Mockito-Kotlin, Turbine for StateFlow testing, Compose Testing
- **StateFlow Testing**: Use `MainDispatcherRule` and `runTest` for async testing
- **Database Testing**: In-memory Room database for reliable DAO testing

### Code Patterns to Follow
- **Repository Pattern**: All data access through repository interfaces in `domain/`
- **Use Case Pattern**: Business logic encapsulated in single-responsibility use cases
- **StateFlow**: ViewModels expose UI state via StateFlow for reactive updates
- **Compose State**: UI components use `collectAsState()` for ViewModel state observation
- **Hilt Injection**: Use `@HiltViewModel` for ViewModels, `@Inject` for dependencies

### DHIS2 SDK Integration
- **D2 Instance**: Initialized in `SimpleDataEntry` Application class via `SystemRepository`
- **Authentication**: Handled through `AuthRepository` and DHIS2 SDK
- **Metadata Sync**: Managed by `MetadataCacheService` and background sync components
- **Data Sync**: Queue-based sync via `SyncQueueManager` and `BackgroundSyncManager`

### Key Configuration Files
- `app/build.gradle.kts` - App-level Gradle configuration (compileSdk 35, minSdk 24)
- `app/src/main/AndroidManifest.xml` - App manifest and permissions
- `.env.example` - Environment variables template
- Database migrations defined in `AppModule.kt` (MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5)

## Task Management Integration

This project uses **Task Master** for development workflow management:

### MCP Server Integration
- Task Master is integrated via MCP server (preferred method)
- Available tools: `get_tasks`, `add_subtask`, `set_task_status`, etc.
- Configuration in `.taskmaster/config.json` and `.cursor/mcp.json`

### Development Workflow
- Start coding sessions with `get_tasks` to see current status
- Use `next_task` to determine next work item based on dependencies
- Break down complex tasks with `expand_task` using `--research` and `--force` flags
- Mark completed tasks with `set_task_status --status=done`
- Update task details with implementation notes using `update_subtask`

### Cursor Rules Integration
- Development workflow rules in `.cursor/rules/dev_workflow.mdc`
- Cursor-specific patterns in `.cursor/rules/cursor_rules.mdc`
- Task Master integration guidelines for proper MCP tool usage

## Important Implementation Details

### Data Entry Form Architecture
- **Accordion UI**: Sections and category groups for easy navigation
- **Immediate Persistence**: Each field saves immediately as local draft
- **Validation**: Built-in type validation (number, integer, percentage, date, etc.)
- **Field Types**: Text, numbers, integers, percentages, dates, coordinates, yes/no (boolean)

### Network & Sync Management
- **NetworkStateManager**: Monitors connectivity for sync decisions
- **SyncQueueManager**: Manages queue of items to sync when online
- **BackgroundSyncManager**: Handles periodic background synchronization
- **MetadataCacheService**: Manages caching of DHIS2 metadata in Room

### Security Features
- **Account Encryption**: Saved account passwords encrypted via `AccountEncryption`
- **Session Management**: User sessions managed through `SessionManager`
- **Secure Storage**: Sensitive data stored using Android security best practices

When working on this codebase, always consider the offline-first architecture and ensure that any new features work seamlessly both online and offline, with Room as the primary data source.