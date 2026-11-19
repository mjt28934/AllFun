plugins {
    id("com.android.application") version "8.13.2" apply false
    // Current Keiyoushi extensions are built with Kotlin 2.4.x and link against
    // kotlinx-serialization 1.13.x, which is what Mihon ships today. Keeping the compiler
    // and stdlib one minor behind is what made generated serializers fail at runtime.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    // KSP 2.3.12+ requires AGP 8.12; both are on the same current line as Kotlin 2.4.
    id("com.google.devtools.ksp") version "2.3.14" apply false
}
