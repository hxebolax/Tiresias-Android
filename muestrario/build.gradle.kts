// App de pruebas de Tiresias (no se publica): una pantalla con todos los tipos de
// control para comprobar qué dice el lector de cada uno (prueba LectorTest).
plugins {
	id("com.android.application")
}

android {
	namespace = "com.xebolax.tiresias.muestrario"
	compileSdk = 37

	defaultConfig {
		applicationId = "com.xebolax.tiresias.muestrario"
		minSdk = 24
		targetSdk = 37
		versionCode = 1
		versionName = "1.0"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

dependencies {
	implementation("androidx.appcompat:appcompat:1.8.0")
	implementation("androidx.recyclerview:recyclerview:1.4.0")
	implementation("com.google.android.material:material:1.14.0")
}
