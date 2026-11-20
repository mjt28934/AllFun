pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/google")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Fallback mirror for networks where dl.google.com is unreachable.
        // Harmless when google() works: Gradle only falls through on a miss.
        maven("https://maven.aliyun.com/repository/google")
        // TorrentStream-Android publishes to JitPack only. It supplies the libtorrent
        // session and sequential piece prioritisation used for torrent-only addons.
        maven("https://jitpack.io")
    }
}

rootProject.name = "AllFun"
include(":app")
