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
rootProject.name = "VoidWars"
include(":app")
// The VOID family's shared look (themes, sky, emblem, controls), a sibling folder in Projects/.
include(":void-ui")
project(":void-ui").projectDir = file("../void-ui")
