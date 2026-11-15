# De cero a senior

Ingeniería de software desde los fundamentos hasta el criterio de un senior, con Java y Spring Boot.

Este repo no es una lista de definiciones para memorizar. Es un curso práctico en el que cada concepto se aprende viendo código que funciona pero está mal diseñado, entendiendo por qué duele, refactorizándolo y después resolviendo un taller sin la solución a la vista.

## Para quién es

- Para quien empieza y quiere bases sólidas.
- Para quien ya trabaja con Java, Spring Boot, Angular o React y sospecha que usa las herramientas sin dominar los conceptos que hay debajo.

Regla de la casa: usar una tecnología no cuenta como saber el concepto. Inyectar un `@Service` todos los días no demuestra que se entienda la inversión de dependencias.

## Cómo está organizado

| Nivel | Tema | Estado |
|---|---|---|
| 1 | [Fundamentos de programación y orientación a objetos](java/nivel-1-fundamentos-oo/) | En curso |
| 2 | [Principios SOLID](java/nivel-2-solid/) | En curso |
| 3 | Patrones de diseño (GoF) | Pendiente |
| 4 | Clean Code, refactorización y testing | Pendiente |
| 5 | Arquitectura de software | Pendiente |
| 6+ | Java avanzado, Spring Boot avanzado, bases de datos, sistemas distribuidos, Cloud, System Design y liderazgo técnico | Pendiente |

El detalle de cada nivel está en [ROADMAP.md](ROADMAP.md) y el avance real en [PROGRESO.md](PROGRESO.md).

## Por dónde empezar

1. Lee [METODOLOGIA.md](METODOLOGIA.md): son las reglas del curso.
2. [Clase 0 — ¿Qué es realmente la orientación a objetos?](java/nivel-1-fundamentos-oo/00-que-es-la-oo/)
3. [SRP — Principio de responsabilidad única](java/nivel-2-solid/01-srp/)

## Cómo se trabaja cada lección

Cada lección sigue el mismo ciclo de nueve pasos:

1. Explicación para principiantes, con una analogía.
2. Explicación técnica.
3. Código que funciona pero tiene problemas de diseño.
4. Análisis del problema y sus consecuencias.
5. Refactorización paso a paso.
6. Taller práctico, sin solución.
7. Revisión técnica de la entrega.
8. Aplicación profesional en Java y Spring Boot.
9. Evaluación senior: preguntas que exigen justificar decisiones.

## Cómo entregar un taller

1. Crea una rama: `git checkout -b taller/01-srp`.
2. Resuelve el taller dentro de la carpeta `taller/solucion/` de la lección. El código de `taller/inicio/` no se toca: es el punto de partida.
3. Añade un `ANALISIS.md` con tu razonamiento. Las decisiones pesan más que el código.
4. Abre un pull request contra `main`. La revisión técnica (paso 7) ocurre ahí.
5. La lección se marca como aprobada en [PROGRESO.md](PROGRESO.md) cuando la revisión no deja problemas de fondo abiertos.

Las soluciones de referencia no se publican antes de la entrega. Ver la respuesta primero convierte un taller en lectura.

## Requisitos

- JDK 21 o superior.
- Git.

Los ejemplos de los niveles 1 a 3 son Java puro, sin dependencias. Cada carpeta de ejemplo es independiente:

```bash
cd nivel-2-solid/01-srp/ejemplos/antes
javac *.java
java Main
```

A partir del nivel 4 (testing) el repo pasa a Maven, porque ahí sí hace falta.

## Autor

Juan Sebastián Charry Rodríguez — [LinkedIn](https://www.linkedin.com/in/juan-sebastian-charry-rodriguez-a6a74113a/)
