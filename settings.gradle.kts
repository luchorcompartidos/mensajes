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
// Núcleo compartido (alarmas, notificaciones, posponer, contactos, tema…).
// Etapa actual: módulo dentro de este repo. Más adelante pasa a su propio repo
// (luchorcompartidos/nucleo-android) como submódulo, y lo usa también Recordatorios.
include(":nucleo")
