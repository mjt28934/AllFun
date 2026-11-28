import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Release signing material is local only: keystore.properties and keystore/ are git-ignored.
// Without them the release variant still configures and builds, just unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// The GitHub release workflow passes the tag's version in, so the APK's versionName always
// matches the release it is published under. Local builds use the defaults below.
val appVersionName = (project.findProperty("appVersionName") as String?)
    ?.removePrefix("v")
    ?: "0.12.2"
val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
    ?: 1202

android {
    namespace = "app.allfun"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.allfun"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Name the artifact after the app and the version (AllFun-0.1-release.apk) instead of
    // app-release.apk, so a folder of builds stays self-describing. This uses the legacy
    // variant API because AGP 8.7 still has no public way to rename an APK output; if the
    // project moves to AGP 9, replace it with a task that copies the assemble output.
    applicationVariants.all {
        val variantName = name
        val variantVersion = versionName ?: "dev"
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName =
                "AllFun-$variantVersion-$variantName.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            // Both okhttp-brotli and jspecify ship this multi-release manifest entry.
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            "META-INF/versions/9/module-info.class"
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Mihon extension APKs are compiled against interface default methods
        // (`DefaultImpls` plus real Java defaults). Keep the same shape so an
        // extension's super calls resolve against this app's host API.
        // `enable` is the modern spelling of -Xjvm-default=all-compatibility: real JVM
        // default methods plus DefaultImpls bridges, so extensions compiled either way
        // resolve host interface methods.
        freeCompilerArgs.add("-jvm-default=enable")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Modern extensions are compiled against kotlinx-serialization 1.13.x. In 1.8.0
    // GeneratedSerializer.typeParametersSerializers gained a real JVM default method;
    // the 1.7.3 interface declared it abstract, so every serializer generated by a
    // newer compiler threw AbstractMethodError when the host built its descriptor.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    // Extensions call Json.encodeToStream/decodeFromStream (OkioStreamsKt).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.13.0")

    // OkHttp 5 is what modern Keiyoushi extensions compile against. 5.2.3 is the
    // newest patch line that still builds against this project's compileSdk 35;
    // 5.5+ requires compiling against Android API 37.
    implementation("com.squareup.okhttp3:okhttp:5.2.3")
    implementation("com.squareup.okhttp3:okhttp-brotli:5.2.3")
    implementation("com.squareup.okhttp3:okhttp-zstd:5.2.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Mihon/Komikku extension host API. The published extensions-lib artifact is
    // a stub ABI only; the real implementations live in this app under
    // eu.kanade.tachiyomi.* and are compiled with the same default-method shape.
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Extensions resolve host singletons (Application, Json, ...) through Injekt.
    // This is the same DI library Mihon itself ships, so the extension bytecode's
    // uy.kohesive.injekt.* references resolve against a compatible ABI.
    implementation("uy.kohesive.injekt:injekt-core:1.16.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Torrent-only Stremio streams: libtorrent session with sequential piece
    // prioritisation. The Android native libraries ship as JNI jars, so the APK grows
    // by the selected ABIs; this is the cost of playing infoHash streams in-app.
    implementation("com.github.TorrentStream:TorrentStream-Android:3.0.0")

    testImplementation("junit:junit:4.13.2")
}
