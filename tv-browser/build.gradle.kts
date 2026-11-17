plugins {
    // AGP 9 has built-in Kotlin support; declaring KGP here (apply false) only pins the
    // Kotlin compiler version (GeckoView 160 pulls kotlin-stdlib 2.4).
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}
