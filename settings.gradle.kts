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
		// Tor para Android (Guardian Project), para la ayuda remota por datos móviles
		maven("https://raw.githubusercontent.com/guardianproject/gpmaven/master") {
			content { includeGroup("info.guardianproject") }
		}
	}
}

rootProject.name = "Tiresias"

// El lector de pantalla
include(":app")
// Lógica pura (frases, verbosidad, puntuación, gestos...), probada en el PC sin Android
include(":nucleo")
// Complemento aparte con Gemini Nano del sistema (ML Kit GenAI, propietario): fuera del APK principal
include(":nano")
// App de pruebas con todos los tipos de control (no se publica)
include(":muestrario")
