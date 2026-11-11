// Repository configuration
// ---------------------------------------------------------------------------
// dl.google.com is NOT reachable from this machine, so Google Maven (which
// hosts AndroidX, Compose and the Android Gradle Plugin) is consumed through
// mirrors instead.
//
// If you are on a network where dl.google.com works, delete the two
// maven { ... } blocks below and keep only google() + mavenCentral().
//
// NOTE: uri() is not resolvable in the settings script scope, so java.net.URI
// is used directly.
// ---------------------------------------------------------------------------

pluginManagement {
    repositories {
        maven {
            url = java.net.URI.create("https://maven.aliyun.com/repository/google")
        }
        maven {
            url = java.net.URI.create("https://mirrors.cloud.tencent.com/nexus/repository/maven-public")
        }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = java.net.URI.create("https://maven.aliyun.com/repository/google")
        }
        maven {
            url = java.net.URI.create("https://mirrors.cloud.tencent.com/nexus/repository/maven-public")
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "SrPass"
include(":app")
