# Nivel 4 — Clean Code, refactorización y testing

Requisito: Niveles 1 a 3. Aquí se pasa del diseño de clases a la calidad del código en el día a día y a la red de seguridad que permite cambiarlo sin miedo.

| # | Lección |
|---|---|
| 01 | [Nombres significativos](01-nombres/) |
| 02 | [Responsabilidad de métodos y clases](02-metodos-y-clases/) |
| 03 | [Code smells](03-code-smells/) |
| 04 | [Refactoring](04-refactoring/) |
| 05 | [DRY, KISS y YAGNI](05-dry-kiss-yagni/) |
| 06 | [Inmutabilidad](06-inmutabilidad/) |
| 07 | [Manejo de excepciones](07-excepciones/) |
| 08 | [Código testeable](08-codigo-testeable/) |
| 09 | [JUnit 5](09-junit5/) |
| 10 | [Mockito y dobles de prueba](10-mockito/) |
| 11 | [TDD y pruebas de integración](11-tdd-e-integracion/) |

## Sobre el código de este nivel

- Lecciones 01 a 08: Java puro. Cada ejemplo se ejecuta con `java Main.java`.
- Lecciones 09 a 11: requieren Maven y bibliotecas (JUnit, AssertJ, Mockito, Spring Boot, Testcontainers). Los fragmentos se montaron en proyectos Maven y se ejecutaron (JDK 21, Maven 3.10, JUnit 5.11.4, AssertJ 3.27.3, Mockito 5.14.2, Spring Boot 4.1.1, Testcontainers 2.0.5, PostgreSQL 16). Excepción: el bloque "Código con problemas" de la lección 11 es un esquema y no compila tal cual. Parte del taller es montar el proyecto y hacerlo correr.
