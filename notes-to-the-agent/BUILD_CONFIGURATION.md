# Build Configuration and Troubleshooting

## 🚨 Critical Build Requirements

### Working Configuration (DO NOT CHANGE without testing)
The following configuration was battle-tested and resolves all compatibility issues:

**Root `build.gradle.kts`:**
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}
```

**App `build.gradle.kts` - Key Settings:**
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")  // NOT kapt!
    id("com.google.dagger.hilt.android")  // Exact plugin name matters!
}

android {
    compileSdk = 34
    
    defaultConfig {
        minSdk = 26  // Modern baseline
        targetSdk = 34
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17  // Modern Java
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"  // Compatible with Kotlin 1.9.20
    }
    
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()  // JUnit 5 support
        }
    }
}

dependencies {
    // Compose BOM - controls all Compose versions
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    
    // Hilt with KSP (not KAPT!)
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    
    // Modern testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}
```

**`gradle/wrapper/gradle-wrapper.properties`:**
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
```

**`gradle.properties`:**
```properties
android.useAndroidX=true
android.enableJetifier=true

# Essential memory settings for modern dependencies
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
org.gradle.parallel=true
org.gradle.caching=true
```

**`local.properties`:**
```properties
sdk.dir=/home/daniel/Android/Sdk
```

## 🔍 Critical Lessons Learned

### 1. Java Version Compatibility Matrix
- **Java 17 target + Java 21 runtime = ✅ WORKS** (with AGP 8.2.2+)
- **Java 11 target + Java 21 runtime = ❌ FAILS** (jlink incompatibility)  
- **Java 8 target + Java 21 runtime = ✅ WORKS** (legacy mode bypass)

### 2. Android Gradle Plugin Evolution
- **AGP 8.1.4**: Limited Java 21 support, jlink issues with modern targets
- **AGP 8.2.2**: Improved Java 21 compatibility, handles jlink properly
- **Rule**: Use AGP 8.2+ for Java 21 runtime environments

### 3. KSP vs KAPT Decision
- **KSP**: Modern, faster, better Java compatibility ✅
- **KAPT**: Legacy, slower, Java 21 issues ❌
- **Plugin Name**: `com.google.dagger.hilt.android` (not `dagger.hilt.android.plugin`)

### 4. Compose Version Compatibility
- **Compose BOM 2024.06.00**: Stable, well-tested with Kotlin 1.9.20
- **Compose Compiler 1.5.4**: Compatible with Kotlin 1.9.20
- **Rule**: Always check Compose-Kotlin compatibility matrix

### 5. Memory Requirements
Modern Android dependencies require significant memory:
- **4GB heap minimum** for clean builds
- **Parallel builds** improve performance significantly
- **Build caching** essential for iterative development

## 🚨 Common Build Failures and Solutions

### JdkImageTransform Errors
```
Failed to transform core-for-system-modules.jar
Error while executing process .../jlink with arguments
```
**Solution**: Upgrade AGP to 8.2.2+ or revert to Java 8 target

### OutOfMemoryError During Build  
```
The project memory settings are likely not configured
```
**Solution**: Add memory settings to `gradle.properties`

### Hilt Plugin Not Found
```
Plugin [id: 'dagger.hilt.android.plugin'] was not found
```
**Solution**: Use correct plugin name `com.google.dagger.hilt.android`

### Compose Compilation Errors
```
This version of the Compose Compiler requires Kotlin version X.X.X
```
**Solution**: Check compatibility matrix, update versions together

## 🔄 Clean Build Process
When encountering build issues:
```bash
./gradlew clean
./gradlew assembleDebug
```

## 📊 Build Success Indicators
- **Clean compilation** with only cosmetic warnings
- **APK generated** at `app/build/outputs/apk/debug/app-debug.apk`
- **Size ~11MB** indicates all dependencies included
- **No JdkImageTransform errors** confirms Java compatibility

## 🚀 Release Process (CI-driven)

Releases are fully automated on GitHub Actions; `versionName`/`versionCode` in
`app/build.gradle.kts` are the single source of truth (F-Droid parses them too).

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, commit, push to `main` on the `github` remote.
2. `.github/workflows/auto-tag.yml` creates tag `v<versionName>` if it doesn't exist, then calls `build-release.yml`.
3. `build-release.yml` lints, tests, builds with the keystore from repo secrets
   (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), verifies the APK's
   versionName equals the tag and the signature, and publishes `release.apk` to the GitHub Release.
4. F-Droid (`fdroiddata/metadata/com.glosdalen.app.yml`: `Binaries: …/v%v/release.apk`,
   `AllowedAPKSigningKeys`) rebuilds from the tag and publishes our signed APK only if it's
   reproducible, i.e. identical except for the signature.

### Reproducibility invariants (breaking these blocks F-Droid updates)
- The build must see a clean git tree; a dirty tree appends `-DIRTY` and uses the wall-clock `BUILD_DATE`. Never commit build-generated files (e.g. `.gradle/`).
- `GIT_HASH` uses `git rev-parse --short`, whose length depends on repo size, so CI clones full history.
- `BUILD_DATE` = HEAD commit time; F-Droid supplies the same via `SOURCE_DATE_EPOCH`.
- The release asset must be named `release.apk`, signed with key SHA-256 `02907bd6…3836a`.

### Local builds
```bash
make build-release  # signed if keystore.properties exists; copies to apks/glosdalen-<version>.apk
make build-debug    # debug APK with versioned naming
make sync-apks      # sync apks/ to Nextcloud (if configured)
```
`../build_all_releases.py` rebuilds APKs for past tags into `../release-apks/`.

## ⚠️ Warning: Fragile Configuration
This build configuration required multiple iterations to achieve stability. Any version bumps should be tested carefully and documented here if successful.
