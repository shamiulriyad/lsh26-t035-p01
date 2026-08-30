# Third-party licenses

Every framework, library, starter, template, UI kit, font, icon, and asset used in this project, and its license.

## Backend

| Name | License | Use |
|---|---|---|
| [Spring Boot](https://spring.io/projects/spring-boot) (`spring-boot-starter-parent` 3.3.4) | Apache License 2.0 | Application framework |
| [Spring Web](https://spring.io/projects/spring-framework) (`spring-boot-starter-web`) | Apache License 2.0 | REST API / embedded Tomcat |
| [Spring Boot Test](https://spring.io/projects/spring-boot) (`spring-boot-starter-test`, test scope) | Apache License 2.0 | Test framework integration (includes JUnit 5, AssertJ, Mockito) |
| [JUnit 5](https://junit.org/junit5/) | Eclipse Public License 2.0 | Test runner (transitive via `spring-boot-starter-test`) |
| [AssertJ](https://assertj.github.io/doc/) | Apache License 2.0 | Test assertions (transitive via `spring-boot-starter-test`) |

## Frontend

| Name | License | Use |
|---|---|---|
| [Inter](https://fonts.google.com/specimen/Inter) (via Google Fonts) | SIL Open Font License 1.1 | UI body font |
| [JetBrains Mono](https://fonts.google.com/specimen/JetBrains+Mono) (via Google Fonts) | Apache License 2.0 | Monospace font (stat values, timeline) |

No JS framework, CSS framework, icon library, or UI kit is used — the frontend is hand-written HTML/CSS/vanilla JS with no build step. The favicon is an inline Unicode emoji (⚡) rendered as SVG, not a third-party icon asset.

## Build / CI tooling

| Name | License | Use |
|---|---|---|
| [Apache Maven](https://maven.apache.org/) | Apache License 2.0 | Build tool |
| [Eclipse Temurin](https://adoptium.net/) (Docker base images) | GPLv2 with Classpath Exception | JDK/JRE runtime in `Dockerfile` |
| [docker/build-push-action](https://github.com/docker/build-push-action), [docker/login-action](https://github.com/docker/login-action), [docker/metadata-action](https://github.com/docker/metadata-action), [docker/setup-buildx-action](https://github.com/docker/setup-buildx-action) | Apache License 2.0 | GitHub Actions steps in `.github/workflows/docker-publish.yml` |
| [actions/checkout](https://github.com/actions/checkout), [actions/setup-java](https://github.com/actions/setup-java) | MIT License | GitHub Actions steps |

`[FILL IN: add anything else here that isn't listed above — any starter template, boilerplate, or pre-event material you started from.]`
