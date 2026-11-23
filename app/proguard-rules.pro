# GeckoView
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; *; }
# Anthropic SDK (Jackson)
-keep class com.anthropic.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.slf4j.**
# Tesseract (JNI)
-keep class com.googlecode.tesseract.android.** { *; }
