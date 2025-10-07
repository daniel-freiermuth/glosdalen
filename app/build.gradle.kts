import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneOffset
import java.time.OffsetDateTime

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("pl.allegro.tech.build.axion-release")
}

scmVersion {
    tag {
        prefix.set("v")
        versionSeparator.set("")
    }
    versionCreator { version, _ ->
        // Remove 'v' prefix if present for clean version names
        version.replace("^v".toRegex(), "")
    }
    
    // Configure semantic versioning - since we have feat: commits, use minor increment
    branchVersionIncrementer = mapOf(
        "main" to "incrementMinor"
    )
    repository {
        pushTagsOnly.set(false)
    }
    localOnly.set(true)  // Don't auto-push, create local release only
    checks {
        aheadOfRemote.set(false)
        uncommittedChanges.set(false)
    }

}

android {
    namespace = "com.swedishvocab.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.swedishvocab.app"
        minSdk = 26
        targetSdk = 34
        versionCode = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim().toIntOrNull() ?: 1
        versionName = scmVersion.version

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Build configuration fields (reproducible builds)
        val gitHash = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
        
        // Use SOURCE_DATE_EPOCH for reproducible builds, fallback to git commit date
        val buildDate = System.getenv("SOURCE_DATE_EPOCH")?.toLongOrNull()?.let { epoch ->
            Instant.ofEpochSecond(epoch)
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } ?: providers.exec {
            commandLine("git", "log", "-1", "--format=%cI")
        }.standardOutput.asText.get().trim().let { gitDate ->
            // Parse ISO 8601 git date and format consistently
            OffsetDateTime.parse(gitDate)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }
        
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // F-Droid will handle signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // AnkiDroid API
    implementation("com.github.ankidroid:Anki-Android:api-v1.1.0")
    
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    
    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.3")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
