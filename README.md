# Simple Data Entry App for DHIS2

A modern Android application for streamlined data entry into DHIS2, designed for field users and data managers. Built with Jetpack Compose, Room, and the DHIS2 Android SDK.

---

## Table of Contents
- [About](#about)
- [Features](#features)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [Offline-First & Caching](#offline-first--caching)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Usage](#usage)
- [Development](#development)
- [Testing](#testing)
- [Changelog](#changelog)
- [Known Issues & Limitations](#known-issues--limitations)
- [Support](#support)
- [License](#license)
- [Acknowledgements](#acknowledgements)

---

## About

A simple, robust Android app for entering, validating, and managing DHIS2 data values in the field or at the facility. Designed for ease of use, **true offline support**, and seamless integration with DHIS2 instances specifically for a data entrant at a facility.

---

## Features

- **Offline-First Architecture:**
  - All metadata (datasets, data elements, category combos, option combos, org units) and data drafts are cached in a local Room database.
  - The app always loads from Room first for instant, robust offline use.
  - After login/sync, the app hydrates its Room cache from the DHIS2 SDK.
  - The app only fetches from the DHIS2 SDK/server if Room is empty and the device is online.
- **Datasets Listing:**
  - View available datasets for the logged-in user and organization unit.
- **Data Entries Listing:**
  - View available data entries for the logged-in user and organization unit.
  - See last updated dates and attribute option combos for each instance.
- **Create New Data Entry:**
  - Guided workflow to create a new data entry for a dataset, period, org unit, and attribute option combo.
- **Edit Existing Data Entry:**
  - Edit all data values for a dataset instance, grouped by section and category.
  - Accordion UI for easy navigation between sections and category groups.
  - Data fields support text, numbers, integers, percentages, dates, coordinates, and yes/no (boolean) types.
- **Immediate Value Persistence:**
  - Each field saves immediately as a local draft, with optimistic UI updates and error handling.
- **Validation:**
  - Built-in validation for value types (number, integer, percentage, etc.) and required fields.
- **Manual Refresh & Sync:**
  - Users can manually refresh dataset instances and data values to ensure up-to-date information.
- **Performance Optimizations:**
  - Efficient Compose state management and Room caching for instant UI and minimal lag.
- **Error Handling:**
  - User-friendly error messages for failed saves, validation errors, and sync issues.
- **DHIS2 Integration:**
  - Uses DHIS2 Android SDK for authentication, data value management, and metadata sync.

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

1. Data entries UX needs more polish
2. Some advanced DHIS2 features (e.g., tracker, events) are not yet supported

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
