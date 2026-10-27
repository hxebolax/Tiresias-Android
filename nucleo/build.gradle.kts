// Lógica pura de Tiresias: no depende de Android, así que se prueba en el PC con JUnit.
// Lo que dice el lector (frases, verbosidad, puntuación, números, diccionarios),
// los gestos y las teclas y su asignación, los segmentos de texto y los filtros.
plugins {
	id("org.jetbrains.kotlin.jvm")
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
	}
}

dependencies {
	testImplementation("junit:junit:4.13.2")
}
