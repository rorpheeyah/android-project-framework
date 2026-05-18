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

rootProject.name = "BizplayDemo"

include(":app")
include(":aos-core")
include(":core")
include(":design-system")
include(":data")
include(":features")
include(":variants-kr")
