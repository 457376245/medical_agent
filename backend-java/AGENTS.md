# Repository Guidelines

## Project Structure & Module Organization
This repository is a Java backend service (`backend-java`) built with Spring Boot 3 and Java 21.

- `src/main/java/com/medical/agent`: application code, split by layer (`api`, `application`, `config`, `infrastructure`).
- `src/main/resources`: runtime config and SQL migrations (`db/migration/V*_*.sql` via Flyway).
- `src/test/java/com/medical/agent`: integration tests.
- `src/test/resources`: test profile settings (`application-test.properties`).
- `target/`: Maven build output (generated; do not commit).

## Build, Test, and Development Commands
- `mvn spring-boot:run`: start the service locally on port `8080`.
- `mvn test`: run all tests (includes Spring Boot + JUnit 5 integration tests).
- `mvn -Dtest=ApiIntegrationTest test`: run a single test class.
- `mvn clean package`: build the JAR and run tests.

Example:
```bash
mvn clean package
```

## Coding Style & Naming Conventions
- Use Java 21 language features conservatively and keep code Spring Boot 3 compatible.
- Follow existing package layering: controllers in `api`, orchestration in `application`, external integrations in `infrastructure`, shared wiring in `config`.
- Keep class names descriptive and suffix by role: `*Controller`, `*Service`, `*Config`, `*Mapper`.
- Use `UpperCamelCase` for classes, `lowerCamelCase` for methods/fields, and `UPPER_SNAKE_CASE` for constants.
- Match existing formatting in this repo (2-space indentation, concise methods, explicit imports).

## Testing Guidelines
- Frameworks: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL).
- Prefer integration-style API tests similar to `ApiIntegrationTest`.
- Name test classes `*Test` and methods as behavior statements (for example, `parseJobCreationRequiresIdempotencyKey`).
- Testcontainers-based tests require Docker; if Docker is unavailable, use focused unit tests for changed logic.

## Commit & Pull Request Guidelines
- Recent history mostly follows Conventional Commits (`feat(...)`, `fix(...)`); continue this format.
- Use clear, scoped messages, for example: `feat(api): add delete record endpoint`.
- Avoid non-descriptive commits like `1` or `fix bug`.
- PRs should include:
  - Change summary and motivation
  - Linked issue/task ID
  - Test evidence (`mvn test` output or equivalent)
  - API request/response examples for endpoint changes

## Security & Configuration Tips
- Never commit real secrets. Use environment variables from `.env.example`.
- Review `application.properties` defaults before deploying; enforce secure values for `APP_SECURITY_ENABLED`, DB credentials, RabbitMQ, and OSS keys.
