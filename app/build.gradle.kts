import java.util.Properties

plugins {
	id("com.android.application")
}

// Servidor de actualizaciones: ahí van version.json y Tiresias_v<versión>_<procesador>.apk (uno por procesador).
// La dirección sale de la carpeta de SynologyDrive\web: C:\Users\xebol\SynologyDrive\web\Tiresias-Android.
// Se puede cambiar en Ajustes; para compilar con otro: gradle assembleRelease -PurlActualizaciones=https://...
val urlActualizaciones = (project.findProperty("urlActualizaciones") as String?) ?: "https://xebolax.synology.me/Tiresias-Android"

// Repositorio de GitHub (respaldo del actualizador y de la tienda), publicado desde la 1.0.4:
// encendido. Para compilar sin él: -PgithubActivo=false.
val repoGithub = (project.findProperty("repoGithub") as String?) ?: "hxebolax/Tiresias-Android"
val githubActivo = (project.findProperty("githubActivo") as String?)?.toBoolean() ?: true

// Firma propia guardada en la carpeta "firma" (la crea compilar.bat).
val propiedadesFirma = Properties().apply {
	val archivo = rootProject.file("firma/firma.properties")
	if (archivo.exists()) archivo.inputStream().use { load(it) }
}

val procesadores = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
// Compilar para publicar (assembleRelease): un APK por procesador
val porProcesador = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

android {
	namespace = "com.xebolax.tiresias"
	compileSdk = 37

	defaultConfig {
		applicationId = "com.xebolax.tiresias"
		// Android 7.0: máxima compatibilidad (arranque directo, gestos simulados). Lo de cada
		// versión posterior (gestos de varios dedos, capturas, braille HID...) se comprueba al usarlo.
		minSdk = 24
		targetSdk = 37
		versionCode = 8
		versionName = "1.0.6.1"

		buildConfigField("String", "URL_ACTUALIZACIONES", "\"$urlActualizaciones\"")
		buildConfigField("String", "REPO_GITHUB", "\"$repoGithub\"")
		buildConfigField("boolean", "GITHUB_ACTIVO", githubActivo.toString())
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		// Todos los procesadores en un solo APK para las pruebas (al publicar, uno por procesador: splits)
		if (!porProcesador) {
			ndk {
				abiFilters += procesadores
			}
		}
		externalNativeBuild {
			cmake {
				arguments += listOf("-DANDROID_STL=c++_static", "-DINCLUIR_ESPEAK=ON")
			}
		}
	}

	// Al publicar, un APK por procesador: ONNX Runtime, LiteRT-LM y Vosk son grandes y cada
	// móvil descarga solo el suyo (unos 75 MB en vez de 250). El actualizador elige el que toca.
	splits {
		abi {
			isEnable = porProcesador
			reset()
			include(*procesadores.toTypedArray())
			isUniversalApk = false
		}
	}

	sourceSets {
		getByName("main") {
			assets.srcDirs("src/main/assets-espeak")
		}
		// Los complementos de la tienda, para probarlos con el motor de verdad (ComplementosTest)
		getByName("androidTest") {
			assets.srcDirs("../complementos")
		}
	}

	// NDK 29: páginas de 16 KB alineadas por defecto (Android 15 o posterior y Google Play)
	ndkVersion = "29.0.14206865"

	externalNativeBuild {
		cmake {
			path = file("src/main/cpp/CMakeLists.txt")
			version = "3.22.1"
		}
	}

	// Idiomas de la interfaz (Android 13+ los ofrece en Ajustes del sistema > Idioma de la app)
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
		debug {
			// Versión de pruebas: se instala junto a la normal y trae el registro de lo dicho y las órdenes de prueba
			applicationIdSuffix = ".pruebas"
			versionNameSuffix = "-pruebas"
			buildConfigField("boolean", "PRUEBAS", "true")
		}
		release {
			isMinifyEnabled = false
			buildConfigField("boolean", "PRUEBAS", "false")
			signingConfig = signingConfigs.findByName("propia") ?: signingConfigs.getByName("debug")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		buildConfig = true
	}

	packaging {
		resources.excludes += listOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/DEPENDENCIES")
		// Las bibliotecas nativas sin comprimir y alineadas: se cargan directamente del APK
		jniLibs.useLegacyPackaging = false
		// Eloquence va comprimida (no es una biblioteca hasta que Tiresias la descomprime): no se le quitan símbolos
		jniLibs.keepDebugSymbols += "**/libevvjni_gz.so"
	}

	testOptions {
		unitTests.isReturnDefaultValues = true
	}

	lint {
		abortOnError = false
		// Llamar a una API más nueva que minSdk sin comprobar la versión es un cierre en móviles viejos
		// (Backtalk se cerraba en Android 11 y 12 por esto): error, no aviso.
		error += listOf("NewApi", "InlinedApi")
		disable += listOf("MissingTranslation")
	}
}

// Las novedades (NOVEDADES.txt) dentro de la app, para enseñarlas al abrirla tras actualizar
val copiarNovedades = tasks.register<Copy>("copiarNovedades") {
	from(rootProject.file("NOVEDADES.txt"))
	into(layout.buildDirectory.dir("generated/novedades"))
	rename { "novedades.txt" }
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/novedades").get().asFile)
tasks.named("preBuild") { dependsOn(copiarNovedades) }

// Los temas de sonidos de Tiresias (temas/<id>/, los mismos de la tienda) dentro de la app, para elegirlos sin internet
val copiarTemas = tasks.register<Sync>("copiarTemas") {
	from(rootProject.file("temas"))
	into(layout.buildDirectory.dir("generated/temas/temas"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/temas").get().asFile)
tasks.named("preBuild") { dependsOn(copiarTemas) }

dependencies {
	implementation(project(":nucleo"))

	implementation("androidx.core:core-ktx:1.19.1")
	implementation("androidx.appcompat:appcompat:1.8.0")
	implementation("androidx.activity:activity-ktx:1.13.0")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
	implementation("androidx.lifecycle:lifecycle-service:2.11.0")
	implementation("androidx.recyclerview:recyclerview:1.4.0")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("androidx.drawerlayout:drawerlayout:1.2.0")
	implementation("com.google.android.material:material:1.14.0")
	// Los códigos QR y de barras (de la pantalla y de las imágenes compartidas), sin Google Play Services
	implementation("com.google.zxing:core:3.5.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

	// Reconocimiento de texto (PP-OCRv5) y voces neuronales (Piper) sin Play Services: ONNX Runtime (MIT)
	implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")
	// IA descargable dentro de Tiresias (Gemma 4), como en Lector Universal: LiteRT-LM (Apache 2.0), solo 64 bits
	implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
	// Órdenes por voz y dictado sin conexión: Vosk (Apache 2.0)
	implementation("com.alphacephei:vosk-android:0.3.75")

	testImplementation("junit:junit:4.13.2")
	testImplementation("org.json:json:20250517")
	androidTestImplementation("androidx.test:runner:1.7.0")
	androidTestImplementation("androidx.test:core:1.7.0")
	androidTestImplementation("androidx.test.ext:junit:1.3.0")
	androidTestImplementation("com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework:4.1.1")
	androidTestImplementation("com.google.guava:guava:33.7.2-android")
}
