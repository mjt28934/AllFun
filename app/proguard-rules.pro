# libtorrent4j is a SWIG/JNI bridge: Java classes and native method names must survive
# shrinking, and TorrentStream resolves its listener callbacks through the library.
-keep class org.libtorrent4j.** { *; }
-keep class com.github.se_bastiaan.torrentstream.** { *; }
-keepclasseswithmembernames class org.libtorrent4j.** { native <methods>; }
-dontwarn org.libtorrent4j.**

# Keep kotlinx.serialization serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Extension APKs link against the host ABI at runtime, so R8 must not strip or
# rename anything in these packages even when the app itself never calls it.
-keep class eu.kanade.tachiyomi.** { *; }
-keep class uy.kohesive.injekt.** { *; }
# Injekt resolves keys from generic superclass information, so keep signatures.
-keepattributes Signature
-keepclassmembers class * extends uy.kohesive.injekt.api.FullTypeReference {
    <init>();
}

# Dynamically loaded extensions are compiled against the full Kotlin stdlib,
# coroutines and the HTTP/HTML libraries Mihon ships, and resolve them by their
# original names. R8 only keeps what AllFun itself calls, so without these rules an
# extension crashes with "Failed resolution of: Lkotlin/collections/SetsKt;" (or a
# NoClassDefFoundError for a renamed jsoup/OkHttp class) in release builds even
# though the same APK works in debug.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
# OkHttp 5 decompresses zstd responses through a JNI bridge. The compressor class is
# resolved from native code, so R8 sees no Kotlin reference and strips it; a server that
# answers with Content-Encoding: zstd then aborts the process with ClassNotFoundException.
-keep class com.squareup.zstd.** { *; }
-keepclasseswithmembernames class com.squareup.zstd.** { native <methods>; }
-dontwarn com.squareup.zstd.**
-keep class org.jsoup.** { *; }
-keep class org.intellij.lang.annotations.** { *; }
-keep class org.jetbrains.annotations.** { *; }
# Extensions may ship a preference screen; it is inflated from the extension's own
# classes but resolved through the host's AndroidX.
-keep class androidx.preference.** { *; }
