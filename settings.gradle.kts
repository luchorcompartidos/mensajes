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

rootProject.name = "ProgramadorMensajes"
include(":app")
// Núcleo compartido (alarmas, notificaciones, posponer, versión, tema…).
// Es el submódulo nucleo/ → repo luchorcompartidos/nucleo-android (más adelante
// lo usa también Recordatorios). Clonar con: git clone --recurse-submodules …
include(":nucleo")
