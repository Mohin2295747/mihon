# TachiyomiAT - Project Documentation

## Project Overview

TachiyomiAT is a fork of Tachiyomi (Mihon), an open-source Android manga reader, with enhanced translation capabilities. This fork adds comprehensive translation features including AI-powered translation using Gemini AI, Google Cloud Translation API, OpenRouter, and on-device translation with MLKit.

**Application ID**: `app.kanade.tachiyomi.at`
**Current Version**: 0.17.2 (versionCode 12)
**Build System**: Gradle with Kotlin DSL
**Minimum SDK**: 28 (Android 9.0)
**Target SDK**: 35 (Android 15)

---

## Project Architecture

### Module Structure

```
TachiyomiAT/
├── app/                          # Main application module
│   ├── src/main/java/
│   │   ├── eu/kanade/tachiyomi/  # Core app code
│   │   └── eu/kanade/translation/ # Translation system (see translation/CLAUDE.md)
│   └── build.gradle.kts
├── core/                         # Core utilities and common code
├── domain/                       # Business logic and domain models
├── presentation/                 # UI components and Compose screens
├── source-api/                   # Source/extension API
├── data/                         # Data layer (repositories, database)
└── i18n/                         # Internationalization resources
```

### Key Technologies

- **Language**: Kotlin 1.9.x
- **UI**: Jetpack Compose + Material 3
- **Async**: Kotlin Coroutines & Flow
- **DI**: Injekt (lightweight dependency injection)
- **Database**: SQLDelight
- **Networking**: OkHttp 5.0
- **Image Loading**: Coil 3
- **Translation**: MLKit, Google Cloud Translation, Gemini AI, OpenRouter

---

## Build System

### Gradle Version Catalogs

Dependencies are managed using Gradle Version Catalogs:
- `gradle/libs.versions.toml` - Main dependencies
- `gradle/androidx.versions.toml` - AndroidX libraries
- `gradle/compose.versions.toml` - Compose dependencies
- `gradle/kotlinx.versions.toml` - Kotlin extensions

### Build Variants

#### Flavors
1. **standard** (Production)
   - Includes Firebase Analytics & Crashlytics
   - Full feature set with updater
   - Used for public releases

2. **dev** (Development)
   - No Firebase dependencies
   - Lighter and faster to build
   - Includes all features except analytics

#### Build Types
- **debug** - Debug symbols, logging enabled
- **release** - Minified, optimized, ProGuard enabled
- **preview** - Release-based with debug signing
- **benchmark** - For performance testing

### Building APKs

#### Build Dev Release (Lightweight, No Firebase)
```bash
./gradlew assembleDevRelease
```
Output: `app/build/outputs/apk/dev/release/`

#### Build Standard Release (Full Features)
```bash
./gradlew assembleStandardRelease
```
Output: `app/build/outputs/apk/standard/release/`

#### APK Variants
The build generates:
- Universal APK (all ABIs)
- Split APKs: arm64-v8a, armeabi-v7a, x86, x86_64

---

## Development Setup

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17 or newer
- Android SDK with API 35
- Gradle 8.12 (wrapper included)

### Initial Setup
```bash
# Clone repository
git clone <repository-url>
cd TachiyomiAT

# Sync Gradle
./gradlew --refresh-dependencies

# Build debug APK
./gradlew assembleDevDebug

# Run on device/emulator
./gradlew installDevDebug
```

### Project Structure Commands
```bash
# Clean build artifacts
./gradlew clean

# Run tests
./gradlew test

# Check code formatting
./gradlew spotlessCheck

# Apply code formatting
./gradlew spotlessApply

# Analyze code
./gradlew lintDevDebug
```

---

## Key Features

### Translation System
See `app/src/main/java/eu/kanade/translation/CLAUDE.md` for detailed documentation.

**Translation Engines**:
1. **MLKit** - On-device translation (no API key required)
2. **Google Cloud Translation** - Official Google Cloud Translation API (BYOK)
3. **Gemini AI** - Gemini 2.0 Flash with context-aware manga translation (BYOK)
4. **OpenRouter** - Access to multiple AI models (BYOK)

**Features**:
- Automatic text recognition from manga images
- Context-aware translation for comics/manga
- Watermark detection and removal
- Multiple language support
- On-device and cloud-based options

### Manga Reading
- Extensive source support via extensions
- Local manga library management
- Chapter downloads
- Reading progress tracking
- Customizable reader settings

### Library Management
- Categories and filters
- Automatic updates
- Batch operations
- Backup & restore

---

## Code Style & Conventions

### Kotlin Conventions
- Use trailing commas in multi-line declarations
- Prefer `val` over `var` when possible
- Use explicit types for public APIs
- Single expression functions: `fun foo() = bar`

### Compose Guidelines
- Use `@Composable` functions for UI
- Prefer `remember` for state that survives recomposition
- Use `derivedStateOf` for computed state
- Keep composables small and focused

### File Organization
```kotlin
// 1. Package declaration
package eu.kanade.tachiyomi.feature

// 2. Imports (alphabetically sorted)
import android.*
import androidx.*
import other.libraries.*
import eu.kanade.*

// 3. Class/Interface declaration
class MyClass {
    // Companion object first
    companion object { }

    // Properties
    private val foo: String

    // Init blocks
    init { }

    // Methods
    fun doSomething() { }
}
```

### Logging
Use `logcat` library for logging:
```kotlin
import logcat.LogPriority
import logcat.logcat

// Info log
logcat { "This is a log message" }

// Error log
logcat(LogPriority.ERROR) { "Error occurred: $error" }

// With stack trace
logcat(LogPriority.ERROR) {
    "Error: ${e.message}\n${e.stackTraceToString()}"
}
```

---

## Git Workflow

### Commit Message Format
```
<type>: <subject>

<body>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

**Types**: feat, fix, refactor, docs, style, test, chore, perf

### Branch Strategy
- `main` - Stable releases
- `develop` - Development branch
- `feature/*` - Feature branches
- `fix/*` - Bug fix branches

### Pre-commit Checks
Before committing:
1. Run `./gradlew spotlessApply` to format code
2. Run `./gradlew lintDevDebug` to check for issues
3. Ensure all tests pass: `./gradlew test`
4. Build successfully: `./gradlew assembleDevDebug`

---

## Troubleshooting

### Build Issues

**Problem**: "Failed to resolve dependency"
```bash
# Solution: Refresh dependencies
./gradlew clean --refresh-dependencies
```

**Problem**: "Out of memory" during build
```bash
# Solution: Increase Gradle heap size
# Add to gradle.properties:
org.gradle.jvmargs=-Xmx4g
```

**Problem**: "Duplicate class" error
```bash
# Solution: Check for conflicting dependencies
./gradlew :app:dependencies
```

### Translation Issues

**Problem**: Cloud Translation not working
- Check API key is valid
- Ensure Translation API is enabled in Google Cloud Console
- Verify billing is enabled on Google Cloud account
- Check logcat for detailed error messages

**Problem**: Gemini translation fails
- Verify API key is correct
- Check if Gemini API is available in your region
- Review logcat for rate limiting or quota errors
- Ensure model name is correct (gemini-2.0-flash-exp)

---

## Testing

### Running Tests
```bash
# All tests
./gradlew test

# Specific module
./gradlew :app:test

# Unit tests only
./gradlew testDevDebugUnitTest

# With coverage
./gradlew jacocoTestReport
```

### Writing Tests
```kotlin
import org.junit.Test
import io.kotest.assertions.assertThat
import io.kotest.matchers.shouldBe

class MyTest {
    @Test
    fun `test something`() {
        val result = doSomething()
        result shouldBe expectedValue
    }
}
```

---

## Performance Optimization

### ProGuard/R8 Rules
Custom rules are in `app/proguard-rules.pro`:
- Keep translation models
- Preserve SQLDelight generated code
- Keep OkHttp internals
- Preserve serialization classes

### Build Performance
- Use Gradle configuration cache: `./gradlew --configuration-cache`
- Enable parallel builds in `gradle.properties`
- Use build cache for faster incremental builds
- Consider using composite builds for multi-module projects

---

## Resources

### Documentation
- [Tachiyomi Docs](https://tachiyomi.org/docs/)
- [Mihon GitHub](https://github.com/mihonapp/mihon)
- [Compose Documentation](https://developer.android.com/jetpack/compose)
- [Translation Module Docs](app/src/main/java/eu/kanade/translation/CLAUDE.md)

### APIs
- [Gemini API Docs](https://ai.google.dev/docs)
- [Google Cloud Translation](https://cloud.google.com/translate/docs)
- [OpenRouter API](https://openrouter.ai/docs)
- [MLKit Translation](https://developers.google.com/ml-kit/language/translation)

---

## Contributing

When contributing to this project:
1. Follow the code style and conventions outlined above
2. Add comprehensive error logging (see translation module examples)
3. Update relevant CLAUDE.md files with your changes
4. Write tests for new features
5. Ensure all builds pass before submitting PR
6. Update version numbers appropriately

For translation-related contributions, see `app/src/main/java/eu/kanade/translation/CLAUDE.md`.
