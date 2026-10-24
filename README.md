HashiCorp Consul API Java Client
================================
A Java client for the [HashiCorp Consul](https://developer.hashicorp.com/consul) HTTP API.
It covers the [API](https://developer.hashicorp.com/consul/api-docs) endpoints for ACL tokens, agent, catalog, coordinates, events, health, KV, prepared queries, sessions and status, including all consistency modes, blocking queries and filtering.

Status:
-------
- Version `2.0.0-SNAPSHOT` (not yet released)
- Requires **Java 25** or newer (compiled with `--release 25`)
- Tested against **Consul 2.0.4** (default) and Consul 1.18.1
- Known limitations (of Consul itself, not of this client):
  - KV keys containing a percent-escape sequence such as `%20` cannot be written through Consul's HTTP API, Consul decodes the key path twice; a plain `%` works
  - Filter values cannot contain both `"` and a backtick; `Filter` throws an `IllegalArgumentException` for those

Dependencies:
-------------
- SLF4J (2.x)
- Jackson Databind (2.x, for JSON processing)
- Checker Framework qualifiers and Error Prone annotations (annotations only)

HTTP is handled by the JDK's built-in [HttpClient](https://openjdk.org/groups/net/httpclient/intro.html); there is no third-party HTTP library.

Using the Library:
------------------
Releases are published to Maven Central, no extra repository or credentials needed.

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.github.eugenmayer:consul-api:2.0.0")
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.eugenmayer</groupId>
  <artifactId>consul-api</artifactId>
  <version>2.0.0</version>
</dependency>
```

Replace `2.0.0` with the latest release.

### GitHub Packages (releases and snapshots)

Releases and `-SNAPSHOT` builds are also published to [GitHub Packages](https://github.com/EugenMayer/consul-api/packages). GitHub Packages requires authentication for downloads, even for public packages: use a GitHub [personal access token (classic)](https://github.com/settings/tokens) with the `read:packages` scope (in GitHub Actions, the workflow's `GITHUB_TOKEN` works if it has `packages: read`).

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/EugenMayer/consul-api")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Put `gpr.user` and `gpr.key` into `~/.gradle/gradle.properties` rather than into the project. For Maven, add the repository to your `pom.xml` and the credentials to `~/.m2/settings.xml` under the same `id`:

```xml
<repositories>
  <repository>
    <id>github-consul-api</id>
    <url>https://maven.pkg.github.com/EugenMayer/consul-api</url>
  </repository>
</repositories>
```

```xml
<!-- ~/.m2/settings.xml -->
<servers>
  <server>
    <id>github-consul-api</id>
    <username>YOUR_GITHUB_USER</username>
    <password>YOUR_TOKEN</password>
  </server>
</servers>
```

### Releasing

Create a GitHub release with the plain version as tag, e.g. `2.0.1` (no `v` prefix). The publish workflow builds and tests that version, then publishes it signed to GitHub Packages and Maven Central. Maven Central releases are permanent: a published version can't be changed or deleted. Running the publish workflow manually publishes the version from `gradle.properties` (usually a `-SNAPSHOT`) to GitHub Packages only.

The workflow needs these repository secrets: `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (Central Portal user token), `SIGNING_KEY` (ASCII-armored private GPG key) and `SIGNING_KEY_PASSWORD`.

Building and Testing:
---------------------
The build uses the Gradle wrapper (Gradle 9.8.2) and a JDK 25. The integration tests start Consul via [Testcontainers](https://testcontainers.com/), so a running Docker daemon is required.

```bash
# all tests against the default Consul version (2.0.4)
./gradlew test

# against another Consul release (any hashicorp/consul image tag)
./gradlew test -PconsulVersion=1.18.1

# a single test class or method
./gradlew test --tests KeyValueConsulClientTest
./gradlew test --tests 'KeyValueConsulClientTest.testSetKVValue'

# full build: tests plus jar, sources jar and javadoc jar in build/libs
./gradlew build

# install into the local Maven repository (~/.m2)
./gradlew publishToMavenLocal
```

All library, plugin and test versions are kept in `gradle.properties`. [Renovate](https://docs.renovatebot.com/) opens update PRs every Monday evening via the `Renovate` workflow (`.github/workflows/renovate.yml`, configured in `renovate.json`; each version in `gradle.properties` carries a `# renovate:` comment for that). To check for newer releases locally:

```bash
./gradlew dependencyUpdates
```

The HTTPS integration test uses a test CA and a certificate for `localhost` in `src/test/resources/ssl` (valid until 2036). To regenerate them (requires `openssl` and `keytool`):

```bash
./scripts/generate-test-certs.sh
```

Migrating from Ecwid consul-api:
--------------------------------
The API differs from the [Ecwid consul-api](https://github.com/Ecwid/consul-api) (`com.ecwid.consul:consul-api`). Review the following before migrating:
- Java Platform Module System
  - The library is modularized (`com.ecwid.consul.api`).
- Removed vulnerable dependencies (with open CVEs)
  - Apache HTTP Client 4.x, replaced with the JDK `HttpClient`
  - GSON, replaced with Jackson
- Blocking queries are interruptible with `Thread.interrupt()` and use an HTTP timeout derived from the requested wait time
- Error responses carry Consul's error text: `OperationException.getMessage()` and `getStatusContent()` contain e.g. `rpc error making call: EOF`
- Query parameters and key paths are fully percent-encoded, so values with `+`, `&`, `=`, `?` or `#` work
- Consul API deprecations and removals
  - Reworked ACL endpoints (the legacy ACL system was removed in Consul 1.11)
  - node-meta query specifier on [catalog](https://developer.hashicorp.com/consul/api-docs/catalog#node-meta) and [health](https://developer.hashicorp.com/consul/api-docs/health#node-meta-1) - deprecated since Consul 1.9
  - tag query specifier on [health](https://developer.hashicorp.com/consul/api-docs/health#tag) - deprecated since Consul 1.9
  - The token is sent in the `X-Consul-Token` header; the `token` query parameter is [deprecated](https://developer.hashicorp.com/consul/api-docs/v1.13.x/api-structure) since Consul 1.13
- Secure handling of sensitive data
  - Tokens and passwords are handled as `char[]`; `CharSequence` overloads exist for compatibility, but `char[]` is recommended
  - Pass your own `SSLContext` and `SSLParameters` for TLS. The former `TlsConfig` was removed because it disabled server certificate verification

Credits:
--------
This project started from the [Ecwid consul-api](https://github.com/Ecwid/consul-api) by Vasily Vasilkov and the Ecwid team, which has not been maintained since 2021-2022.
It builds on the continuation by [Jon Huang (jon5477)](https://github.com/jon5477/consul-api), who modernized the codebase: JDK `HttpClient`, Jackson, the module system, the reworked ACL API and the secure token handling.
Many thanks to both.
