plugins {
    alias(libs.plugins.android.application) apply false
    // No org.jetbrains.kotlin.android: AGP 9.0+ provides Kotlin support itself.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
