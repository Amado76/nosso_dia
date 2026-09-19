# Nosso Dia

- Write all project documentation, code, comments, configuration messages and API text in English. Keep Nosso Dia as the product name.

- Use Java 25 and Spring Boot. Build with the committed Maven Wrapper.
- Stack: Spring Web MVC, Spring Data JPA/Hibernate, PostgreSQL, Flyway,
  Jakarta Validation, Spring Security, OpenAPI/Swagger, JUnit, Mockito,
  Testcontainers and Docker Compose.
- Do not add dependencies without a clear reason. Prefer Java and Spring functionality.
- Let Spring Boot manage dependency versions where supported.
- Apply database changes through versioned Flyway migrations. Keep Hibernate ddl-auto=validate.
- Use PostgreSQL Testcontainers for database integration tests.
- Run ./mvnw verify for changes affecting application behavior. Docker must be available.
- Keep secrets out of version control; document environment variables in .env.example.
- The product domain and final authentication flow are not defined yet. Do not invent them.
