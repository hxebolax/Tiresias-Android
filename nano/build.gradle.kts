import java.util.Properties

// «Tiresias IA del móvil»: complemento aparte con Gemini Nano del sistema (ML Kit GenAI).
// Va en otro APK porque ML Kit es una biblioteca propietaria de Google: así Tiresias
// sigue siendo libre (GPL) y sin Play Services. Este complemento es Apache 2.0.
plugins {
	id("com.android.application")
}

val propiedadesFirma = Properties().apply {
	val archivo = rootProject.file("firma/firma.properties")
	if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
	namespace = "com.xebolax.tiresias.nano"
	compileSdk = 37

	defaultConfig {
		applicationId = "com.xebolax.tiresias.nano"
		// ML Kit GenAI necesita Android 8 o posterior
		minSdk = 26
		targetSdk = 37
		versionCode = 1
		versionName = "1.0.0"
	}

	androidResources {
		generateLocaleConfig = true
	}

	signingConfigs {
		if (propiedadesFirma.isNotEmpty()) {
			create("propia") {
				storeFile = rootProject.file(propiedadesFirma.getProperty("archivo"))
				storePassword = propiedadesFirma.getProperty("clave")
				keyAlias = propiedadesFirma.getProperty("alias")
				keyPassword = propiedadesFirma.getProperty("clave")
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			signingConfig = signingConfigs.findByName("propia") ?: signingConfigs.getByName("debug")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

dependencies {
	implementation("androidx.appcompat:appcompat:1.8.0")
	implementation("androidx.activity:activity-ktx:1.13.0")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
	implementation("com.google.android.material:material:1.14.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
	// Gemini Nano del sistema (AICore), en el propio móvil
	implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
}
