import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("net.ltgt.errorprone")
    // ./gradlew dependencyUpdates lists newer versions for gradle.properties
    id("io.github.ben-manes.versions")
    // publishes to Maven Central (signed) and, via the publishing block, to GitHub Packages
    id("com.vanniktech.maven.publish")
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
    // check links, HTML and tags, but do not require a comment on every getter and setter
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

tasks.test {
    useJUnitPlatform()
    // resolved when the task runs, which the configuration cache requires
    val agent: FileCollection = mockitoAgent
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${agent.singleFile}") })
    systemProperty("consul.version", versionOf("consulVersion"))
}

// only suggest stable releases in ./gradlew dependencyUpdates
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    notCompatibleWithConfigurationCache("the versions plugin does not support the configuration cache")
    rejectVersionIf {
        Regex("(?i).*[.-](alpha|beta|rc|cr|m|milestone|preview|snapshot)[.\\d-]*").matches(candidate.version)
    }
}

// GitHub repository the package belongs to; GitHub Actions sets GITHUB_REPOSITORY
val githubRepository = System.getenv("GITHUB_REPOSITORY") ?: "EugenMayer/consul-api"

// Maven Central credentials and the signing key are read from the Gradle properties
// mavenCentralUsername, mavenCentralPassword, signingInMemoryKey and signingInMemoryKeyPassword
// (in CI: ORG_GRADLE_PROJECT_<name> environment variables).
mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources()))
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates(group.toString(), "consul-api", version.toString())
    pom {
        name = "consul-api"
        description = "Java client for the HashiCorp Consul HTTP API"
        url = "https://github.com/$githubRepository"
        inceptionYear = "2014"
        licenses {
            // LICENSE covers the original Ecwid code; files with an EPL-2.0 header are under EPL-2.0
            license {
                name = "Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
            license {
                name = "Eclipse Public License 2.0"
                url = "https://www.eclipse.org/legal/epl-2.0/"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "EugenMayer"
                name = "Eugen Mayer"
                url = "https://github.com/EugenMayer"
            }
        }
        contributors {
            contributor {
                name = "Vasily Vasilkov (Ecwid, original author)"
            }
            contributor {
                name = "Jon Huang"
                url = "https://github.com/jon5477"
            }
        }
        issueManagement {
            system = "GitHub"
            url = "https://github.com/$githubRepository/issues"
        }
        scm {
            connection = "scm:git:https://github.com/$githubRepository.git"
            developerConnection = "scm:git:ssh://git@github.com/$githubRepository.git"
            url = "https://github.com/$githubRepository"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/$githubRepository")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
