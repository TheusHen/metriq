plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.metriq.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

sqldelight {
    databases {
        create("MetriqDatabase") {
            packageName.set("com.metriq.data.db")
        }
    }
}

dependencies {
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(project(":core-domain"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation("app.cash.sqldelight:sqlite-driver:2.1.0")
    // Android provides org.json at runtime; JVM unit tests need the real implementation.
    testImplementation("org.json:json:20240303")
}

