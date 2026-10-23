import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    `maven-publish`
    id("net.ltgt.errorprone")
    // ./gradlew dependencyUpdates lists newer versions for gradle.properties
    id("io.github.ben-manes.versions")
}

// all versions live in gradle.properties
fun versionOf(name: String): String = providers.gradleProperty(name).get()

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(versionOf("javaVersion"))
    }
    withJavadocJar()
    withSourcesJar()
}

// Mockito is loaded as a Java agent; dynamic agent loading is going away in newer JDKs
val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:${versionOf("jacksonVersion")}")
    api("org.checkerframework:checker-qual:${versionOf("checkerQualVersion")}")
    implementation("org.slf4j:slf4j-api:${versionOf("slf4jVersion")}")
    compileOnly("com.google.errorprone:error_prone_annotations:${versionOf("errorProneVersion")}")
    errorprone("com.google.errorprone:error_prone_core:${versionOf("errorProneVersion")}")

    testImplementation(platform("org.junit:junit-bom:${versionOf("junitVersion")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.hamcrest:hamcrest:${versionOf("hamcrestVersion")}")
    testImplementation("org.mockito:mockito-core:${versionOf("mockitoVersion")}")
    testImplementation("nl.jqno.equalsverifier:equalsverifier:${versionOf("equalsverifierVersion")}")
    testImplementation(platform("org.testcontainers:testcontainers-bom:${versionOf("testcontainersVersion")}"))
    testImplementation("org.testcontainers:testcontainers-consul")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.slf4j:slf4j-simple:${versionOf("slf4jVersion")}")
    mockitoAgent("org.mockito:mockito-core:${versionOf("mockitoVersion")}") { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = versionOf("javaVersion").toInt()
    options.errorprone.disableWarningsInGeneratedCode = true
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    systemProperty("consul.version", versionOf("consulVersion"))
}

// only suggest stable releases in ./gradlew dependencyUpdates
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
        Regex("(?i).*[.-](alpha|beta|rc|cr|m|milestone|preview|snapshot)[.\\d-]*").matches(candidate.version)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "consul-api"
                description = "Java client for the HashiCorp Consul HTTP API"
                url = "https://github.com/jon5477/consul-api"
                issueManagement {
                    url = "https://github.com/jon5477/consul-api/issues"
                }
                scm {
                    connection = "scm:git:git@github.com:jon5477/consul-api.git"
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jon5477/consul-api")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
