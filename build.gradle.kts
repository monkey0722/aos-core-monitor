// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Applied, not just declared: without it ktlintCheck covered app/ alone, so this file and
    // settings.gradle.kts were the two Kotlin files in the repository nothing checked.
    alias(libs.plugins.ktlint)
}
