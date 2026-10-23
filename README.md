HashiCorp Consul API Java Client
================================
A Java client for the [HashiCorp Consul](https://developer.hashicorp.com/consul) HTTP API.
It covers the [API](https://developer.hashicorp.com/consul/api-docs) endpoints for ACL tokens, agent, catalog, coordinates, events, health, KV, prepared queries, sessions and status, including all consistency modes, blocking queries and filtering.

Status:
-------
- Version `2.0.0-SNAPSHOT` (not yet released)
- Requires **Java 25** or newer (compiled with `--release 25`)
- Tested against **Consul 2.0.4** (default) and Consul 1.18.1
- Known open issues (planned fixes):
  - Error responses that are not JSON (e.g. `text/plain` bodies such as `rpc error making call: EOF`) are dropped, so `OperationException.getStatusContent()` is `null` for them
  - Query parameter values are not URL-encoded, so `+`, `&` and `=` in values (e.g. in `filter` expressions) are not transmitted correctly
  - `Filter` values are not escaped (`"` and `\` break the expression)
  - `Request.Builder.addHeader` returns `null` instead of the builder

Dependencies:
-------------
- SLF4J (2.x)
- Jackson Databind (2.x, for JSON processing)
- Checker Framework qualifiers and Error Prone annotations (annotations only)

HTTP is handled by the JDK's built-in [HttpClient](https://openjdk.org/groups/net/httpclient/intro.html); there is no third-party HTTP library.

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

All library, plugin and test versions are kept in `gradle.properties`. To see which of them have newer releases:

```bash
./gradlew dependencyUpdates
```

Migrating from Ecwid consul-api:
--------------------------------
The API differs from the [Ecwid consul-api](https://github.com/Ecwid/consul-api) (`com.ecwid.consul:consul-api`). Review the following before migrating:
- Java Platform Module System
  - The library is modularized (`com.ecwid.consul.api`).
- Removed vulnerable dependencies (with open CVEs)
  - Apache HTTP Client 4.x, replaced with the JDK `HttpClient`
  - GSON, replaced with Jackson
- Blocking queries are interruptible with `Thread.interrupt()`
- Consul API deprecations and removals
  - Reworked ACL endpoints (the legacy ACL system was removed in Consul 1.11)
  - node-meta query specifier on [catalog](https://developer.hashicorp.com/consul/api-docs/catalog#node-meta) and [health](https://developer.hashicorp.com/consul/api-docs/health#node-meta-1) - deprecated since Consul 1.9
  - tag query specifier on [health](https://developer.hashicorp.com/consul/api-docs/health#tag) - deprecated since Consul 1.9
  - The token is sent in the `X-Consul-Token` header; the `token` query parameter is [deprecated](https://developer.hashicorp.com/consul/api-docs/v1.13.x/api-structure) since Consul 1.13
- Secure handling of sensitive data
  - Tokens and passwords are handled as `char[]`; `CharSequence` overloads exist for compatibility, but `char[]` is recommended
  - Pass your own `SSLContext` and `SSLParameters` for TLS. `TlsConfig` is deprecated and will be removed because it is insecure (it disables server certificate verification)

Credits:
--------
This project started from the [Ecwid consul-api](https://github.com/Ecwid/consul-api) by Vasily Vasilkov and the Ecwid team, which has not been maintained since 2021-2022.
It builds on the continuation by [Jon Huang (jon5477)](https://github.com/jon5477/consul-api), who modernized the codebase: JDK `HttpClient`, Jackson, the module system, the reworked ACL API and the secure token handling.
Many thanks to both.
