# 📱 SimpleDataEntry

**A Modern Android DHIS2 Data Entry Application**

[![Android Studio](https://img.shields.io/badge/Android%20Studio-2024.1-brightgreen.svg)](https://developer.android.com/studio)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.06.00-green.svg)](https://developer.android.com/jetpack/compose)
[![DHIS2 SDK](https://img.shields.io/badge/DHIS2%20SDK-2.7.0-orange.svg)](https://docs.dhis2.org/en/develop/developing-with-the-android-sdk/about-this-guide.html)
[![Build Status](https://img.shields.io/badge/Build-Passing-success.svg)](#)

> **Streamlined offline-first DHIS2 data collection with modern Android architecture**

SimpleDataEntry is a production-ready Android application built for efficient DHIS2 data collection with comprehensive offline capabilities and universal DHIS2 program support. Designed for field workers, health professionals, and data collectors who need reliable, fast, and intuitive data entry tools that work seamlessly both online and offline. Supports aggregate datasets, tracker programs, and event programs with robust foreign key constraint violation handling.

---

## Features

### **Universal DHIS2 Program Support**
- **Aggregate Datasets:** Full support for traditional DHIS2 dataset data entry
- **Tracker Programs:** Support for individual-based programs with enrollment creation
- **Event Programs:** Support for standalone event programs with event creation
- **Unified Interface:** Single app interface supporting all DHIS2 program types

### **Offline-First Architecture**
- All metadata (datasets, data elements, category combos, option combos, org units) and data drafts are cached in a local Room database
- The app always loads from Room first for instant, robust offline use
- After login/sync, the app hydrates its Room cache from the DHIS2 SDK
- The app only fetches from the DHIS2 SDK/server if Room is empty and the device is online

### **DHIS2 SDK Foreign Key Constraint Resolution**
- **Comprehensive FK Violation Handling:** Automatic detection and resolution of CategoryOptionCombo foreign key violations
- **Expanded Metadata Scope:** Enhanced metadata synchronization including all dependencies
- **Multi-Data Type Support:** Works for aggregate datasets, tracker programs, and event programs
- **Real-Time Detection:** Inspection and automatic resolution of database constraint violations

### **Data Management**
- **Programs Listing:** View available datasets, tracker programs, and event programs for the logged-in user
- **Instance Management:** View and manage data entries, enrollments, and events with filtering and search
- **Create New Entries:** Guided workflow for creating new instances across all program types
- **Edit Existing Data:** Full editing capabilities for all data types with accordion UI navigation
- **Data Type Support:** Text, numbers, integers, percentages, dates, coordinates, and yes/no fields

### **Reliability & Performance**
- **Immediate Value Persistence:** Each field saves immediately as local draft with optimistic UI updates
- **Built-in Validation:** Value type validation and required field checking
- **Manual Refresh & Sync:** User-controlled sync with detailed progress tracking
- **Performance Optimizations:** Efficient Compose state management and Room caching
- **Error Handling:** Comprehensive error messages for saves, validation, and sync issues

---

## Screenshots

> 

---

## Architecture

This project follows a **modern Android architecture** using the **MVVM (Model-View-ViewModel)** pattern, **Jetpack Compose** for UI, **Room** for local caching, and **Hilt** for dependency injection.

- **Presentation Layer**: Composables and ViewModels (e.g., `DataEntryViewModel`, `LoginViewModel`, etc.) in `app/src/main/java/com/ash/simpledataentry/presentation/`.
- **Domain Layer**: Use cases and models in `app/src/main/java/com/ash/simpledataentry/domain/`.
- **Data Layer**: Repositories, Room DAOs/entities, and data sources in `app/src/main/java/com/ash/simpledataentry/data/`.
- **Dependency Injection**: Configured via Hilt in `di/AppModule.kt`.
- **App Initialization**: The `SimpleDataEntry` class (subclass of `Application`) initializes the DHIS2 SDK and other app-wide dependencies.

---

## Offline-First & Caching

- **Room Entities & DAOs:**
  - The app defines Room entities and DAOs for all key metadata: Datasets, Data Elements, Category Combos, Category Option Combos, Organisation Units, and Data Value Drafts.
- **Hydration from DHIS2 SDK:**
  - After login or sync, the app fetches all metadata from the DHIS2 SDK and persists it to Room.
- **Primary Data Source:**
  - All screens and repositories load from Room first for instant, offline access.
  - If Room is empty and the device is online, the app fetches from the DHIS2 SDK/server, updates Room, and then loads from Room.
- **Drafts:**
  - All data entry drafts are saved in Room and merged with metadata for offline editing and review.
- **Robust Offline Flow:**
  - Users can log in, sync, and then use the app in the field with no connectivity. All metadata and drafts are available, and the UI loads instantly.

---

## Getting Started

### Prerequisites

- **Android Studio** (latest recommended)
- **JDK 11**
- **Android SDK 24+**

### Build & Run

1. Clone the repository:
   ```sh
   git clone https://github.com/your-org/simpleDataEntry.git
   cd simpleDataEntry
   ```
2. Open the project in Android Studio.
3. Sync Gradle and build the project.
4. Run on an emulator or physical device (API 24+).

---

## Configuration

- **DHIS2 Server**: The app expects DHIS2 server configuration (URL, username, password) via the `Dhis2Config` data class.
- **AndroidManifest.xml**: Main activity is `com.ash.simpledataentry.presentation.MainActivity`. The app uses standard permissions and themes.
- **Dependency Injection**: All repositories and use cases are provided via Hilt modules in `di/AppModule.kt`.

---

## Usage

1. Login with your DHIS2 credentials (ensure you are online for the first login/sync).
2. The app will download and cache all required metadata and data drafts locally.
3. Select the dataset for which you want to enter data.
4. Select an existing entry you may want to edit or add to, or press the plus at the bottom right corner to create a new entry.
5. Navigate through the accordions and perform entry into the fields for which data is available.
6. You can now go offline and continue to use the app, enter data, and save drafts. All screens will load instantly from local cache.
7. When back online, sync to push drafts and fetch any new metadata or data.

---

## Development

- **Main App Module**: All code is under the `app/` directory.
- **Key files and directories**:
  - `app/src/main/java/com/ash/simpledataentry/` — Main package.
    - `presentation/` — UI screens and ViewModels.
    - `domain/` — Business logic, models, and use cases.
    - `data/` — Data sources, Room DAOs/entities, and repository implementations.
    - `di/` — Dependency injection setup.
    - `ui/theme/` — Compose theme definitions.
  - `app/build.gradle.kts` — App-level Gradle build file.
  - `app/src/main/AndroidManifest.xml` — App manifest.

- **Build System**: Uses Gradle with Kotlin DSL (`build.gradle.kts`).

---

## Testing

This project includes a comprehensive test suite covering all architectural layers with 80%+ coverage of critical functionality.

### 🧪 Test Structure

- **Unit Tests** (`app/src/test/`): JVM tests for business logic
  - Domain use cases and validation services
  - ViewModels with StateFlow testing using Turbine
  - Repository implementations with mocked dependencies
  - Test data builders and utilities for consistent test data

- **Instrumentation Tests** (`app/src/androidTest/`): Android device tests
  - Room database operations with in-memory testing
  - Compose UI components and user interactions
  - Integration tests with Android context

### 🚀 Running Tests

```bash
# Unit tests (fast, run locally)
./gradlew test

# UI/Integration tests (requires emulator/device)
./gradlew connectedAndroidTest

# All tests
./gradlew build
```

### 🎯 Testing Highlights

- **Modern Stack**: JUnit 4, Mockito-Kotlin, Turbine, Google Truth, Compose Testing
- **StateFlow Testing**: Proper async testing with `MainDispatcherRule` and `runTest`
- **Database Testing**: In-memory Room database for reliable DAO testing
- **UI Testing**: Compose test rule with semantic tree assertions
- **Test Coverage**: Business logic, state management, data persistence, UI interactions

See [TESTING.md](TESTING.md) for comprehensive testing documentation.

### 📋 Manual Testing

Test the offline-first flow by:
1. Logging in and syncing while online
2. Going offline (disable WiFi/data)
3. Navigating all screens and entering data - should load instantly from Room cache
4. Going back online and syncing to push drafts and fetch updates

---

## Changelog

> 

---

## Known Issues & Limitations

1. **Tracker Data Entry:** Full tracker data entry screens are in Phase 3 development
2. **Event Data Entry:** Event-specific data entry forms are in Phase 3 development
3. **Advanced Features:** Program rules, relationships, and tracker-specific sync features are planned for future releases
4. **UI Polish:** Some data entry UX elements need additional refinement

---

## Support

- [Open an issue](https://github.com/your-org/simpleDataEntry/issues) for bug reports or feature requests.
- [DHIS2 Community](https://community.dhis2.org/) for general DHIS2 questions.
- HISP Uganda Software Development Team - email info@hispuganda.org for specific inquiries.

---

## License

This project is licensed under the MIT License.

Copyright (c) 2025 HISP Uganda

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## Acknowledgements

- [DHIS2 Android SDK](https://github.com/dhis2/dhis2-android-sdk)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Persistence Library](https://developer.android.com/jetpack/androidx/releases/room)
- [DHIS2 Community](https://community.dhis2.org/)
- [HISP Uganda](https://hispuganda.org)
