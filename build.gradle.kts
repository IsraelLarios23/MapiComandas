// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Fuerza javapoet 1.13.0 en el classpath del buildscript (plugins de Hilt/Dagger).
// Evita el error "NoSuchMethod ClassName.canonicalName()" durante el sync.
buildscript {
    dependencies {
        classpath("com.squareup:javapoet:1.13.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}