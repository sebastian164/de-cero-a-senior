# Ruta Java

De los fundamentos de la orientación a objetos al diseño y la defensa de sistemas completos, con Java y Spring Boot.

## Niveles

| Nivel | Tema | Sesiones |
|---|---|---|
| 1 | [Fundamentos de programación y orientación a objetos](nivel-01-fundamentos-oo/) | 9 |
| 2 | [Principios SOLID](nivel-02-solid/) | 5 + taller integrador |
| 3 | [Patrones de diseño (GoF)](nivel-03-patrones/) | 23 + taller integrador |
| 4 | [Clean Code, refactorización y testing](nivel-04-clean-code-testing/) | 11 |
| 5 | [Arquitectura de software](nivel-05-arquitectura/) | 11 + taller integrador |
| 6 | [Java avanzado](nivel-06-java-avanzado/) | 6 |
| 7 | [Spring Boot avanzado](nivel-07-spring-boot-avanzado/) | 7 |
| 8 | [Bases de datos](nivel-08-bases-de-datos/) | 6 |
| 9 | [Sistemas distribuidos](nivel-09-sistemas-distribuidos/) | 6 |
| 10 | [Cloud](nivel-10-cloud/) | 5 |
| 11 | [System Design](nivel-11-system-design/) | 5 |
| 12 | [Liderazgo técnico](nivel-12-liderazgo-tecnico/) | 5 |

Y al final de cada bloque: [proyectos de aplicación](proyectos/).

## Orden

El orden de los niveles es obligatorio. Dentro del Nivel 3, sigue el orden de estudio indicado en su índice, no el alfabético.

## Qué código se puede ejecutar tal cual

| Niveles | Estado del código |
|---|---|
| 1, 2 y 3 | Java puro. Todos los ejemplos se compilaron y ejecutaron con JDK 21. |
| 4, lecciones 01 a 08 | Java puro, compilado y ejecutado. |
| 4, lecciones 09 a 11 | JUnit, Mockito y Spring: ilustrativo, sin ejecutar. |
| 5 a 12 | Ilustrativo: muestra estructura y decisiones. Montarlo y hacerlo funcionar es parte de cada taller. |

Ejecutar un ejemplo:

```bash
cd nivel-03-patrones/comportamiento/strategy/ejemplo
java Main.java
```

Los dos primeros módulos del curso (Clase 0 y SRP) tienen una clase por archivo: `javac *.java && java Main`.

## Requisitos

- JDK 21 o superior.
- Desde el Nivel 4: Maven.
- Desde el Nivel 7: Docker.
