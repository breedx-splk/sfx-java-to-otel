plugins {
    application
}

repositories {
    mavenCentral()
}

val otelVersion = "1.31"

application {
    mainClass.set("com.splunk.Example")
}

dependencies {
    implementation("io.opentelemetry:opentelemetry-api:${otelVersion}")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:${otelVersion}")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:${otelVersion}")
}
