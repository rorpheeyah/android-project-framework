pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "compass-demo"

include(":aos-core")
include(":core")
include(":design-system")
include(":data")
include(":features")
include(":variants-kh")
include(":variants-vn")
include(":app")
