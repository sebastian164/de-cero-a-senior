# De cero a senior

Ingeniería de software desde los fundamentos hasta el criterio de un senior, en tres rutas: Java con Spring Boot, Angular, y React con Next.js.

Este repo no es una lista de definiciones para memorizar. Es un curso práctico en el que cada concepto se aprende viendo código que funciona pero está mal diseñado, entendiendo por qué duele, corrigiéndolo y después resolviendo un taller sin la solución a la vista.

## Para quién es

- Para quien empieza y quiere bases sólidas.
- Para quien ya trabaja con estas tecnologías y sospecha que usa las herramientas sin dominar los conceptos que hay debajo.

Regla de la casa: usar una tecnología no cuenta como saber el concepto. Inyectar un `@Service` todos los días no demuestra que se entienda la inversión de dependencias.

## Rutas

| Ruta | Contenido | Sesiones |
|---|---|---|
| [Java y Spring Boot](java/) | Orientación a objetos, SOLID, los 23 patrones GoF, clean code y testing, arquitectura, Java avanzado, Spring Boot avanzado, bases de datos, sistemas distribuidos, cloud, system design y liderazgo técnico | 102 |
| [Angular](angular/) | TypeScript, Angular 22 (señales, recursos, formularios con señales), SOLID y patrones en la interfaz, pruebas, arquitectura frontend, rendimiento, renderizado en servidor, seguridad y accesibilidad | 28 + 6 comunes |
| [React y Next.js](react-next/) | TypeScript, React, Next.js, SOLID y patrones en React, formularios y pruebas, arquitectura con componentes de servidor, rendimiento y seguridad | 23 + 6 comunes |

Material común a las dos rutas de frontend:

| Carpeta | Contenido | Sesiones |
|---|---|---|
| [Fundamentos de TypeScript](typescript/) | El lenguaje, orientación a objetos en TypeScript, funciones y asincronía | 3 |
| [Nivel senior de frontend](frontend-senior/) | Diseño de sistemas frontend, calidad y entrega, liderazgo | 3 |

Talleres prácticos comunes a las tres rutas:

| Carpeta | Contenido | Sesiones |
|---|---|---|
| [Arquitectura hexagonal con Java](hexagonal/) | Un banco construido sesión a sesión: dominio, puertos y casos de uso, transacciones (ACID, idempotencia, bandeja de salida), concurrencia y aislamiento, adaptadores HTTP y consola, persistencia con JPA y pruebas por capa. Es la base de los talleres de Kafka, sagas y HikariCP | 8 |
| [Docker y Kubernetes](docker-kubernetes/) | Contenedores, imágenes, Compose, publicación; clúster local, despliegues, configuración, sondas, Gateway API, almacenamiento, escalado, seguridad, diagnóstico y Kustomize | 6 + 10 |
| [Kafka y sagas](kafka-y-sagas/) | Registro de eventos, productores, consumidores, idempotencia, contratos, Spring Boot y outbox; sagas orquestadas y coreografiadas, fallos, aislamiento, la saga dentro de un servicio hexagonal y un taller integrador | 8 + 7 |
| [HikariCP](hikaricp/) | El pool de conexiones: qué es, cómo se dimensiona, fugas, agotamiento, métricas, un simulacro de diagnóstico y el pool dentro de un servicio hexagonal | 6 |
| [Prácticas: TDD, Karate, SDD y ODD](practicas/) | Desarrollo guiado por pruebas, pruebas de API con Karate, desarrollo guiado por especificaciones y desarrollo guiado por la observabilidad | 4 + 3 + 3 + 3 |

Cada ruta termina con sus **proyectos de aplicación**: [Java](java/proyectos/), [Angular](angular/proyectos/), [React y Next.js](react-next/proyectos/).

## Cómo se relacionan las rutas

Los conceptos de diseño no dependen del lenguaje. La explicación más completa de cada uno (analogía, definición, casos, excepciones) está en la ruta Java, que es la más extensa. Las rutas de frontend enlazan a esas lecciones y se concentran en cómo se aplica cada idea en su tecnología. Las tres se pueden seguir por separado.

## Por dónde empezar

1. Lee [METODOLOGIA.md](METODOLOGIA.md): son las reglas del curso.
2. Elige una ruta y entra por su índice.
3. Sigue el orden de los niveles: es obligatorio. El detalle está en [ROADMAP.md](ROADMAP.md) y tu avance en [PROGRESO.md](PROGRESO.md).

## Cómo se trabaja cada lección

1. Explicación para principiantes, con una analogía.
2. Explicación técnica.
3. Código que funciona pero tiene problemas de diseño.
4. Análisis del problema y sus consecuencias.
5. Código mejorado, paso a paso.
6. Taller práctico, con una solución de referencia para comparar después de intentarlo.
7. Revisión técnica de la entrega.
8. Aplicación profesional.
9. Evaluación senior: preguntas que exigen justificar decisiones.

Las lecciones de los primeros niveles de Java siguen este ciclo completo. Las de niveles avanzados y las de frontend lo condensan: idea, lo esencial, ejemplo, errores comunes, taller y preguntas.

## Cómo entregar un taller

1. Crea la rama que indica la lección, por ejemplo `git checkout -b taller/01-srp`.
2. Resuelve el taller en una carpeta `taller/solucion/` dentro de la lección. Si la lección trae código de inicio, no lo modifiques: es el punto de partida.
3. Añade un `ANALISIS.md` con tu razonamiento. Las decisiones pesan más que el código.
4. Abre un pull request contra `main`. La revisión técnica ocurre ahí.
5. La lección se marca como aprobada en [PROGRESO.md](PROGRESO.md) cuando la revisión no deja problemas de fondo abiertos.

### Soluciones de referencia

Cada taller trae su solución de referencia en `taller/solucion-de-referencia/`: el código completo, sus pruebas y un `ANALISIS.md` que explica las decisiones paso a paso. Todas se compilaron y se ejecutaron, y pasaron una revisión de arquitectura.

Úsalas para guiarte cuando te atasques y para comparar cuando termines, no como punto de partida: ver la respuesta primero convierte un taller en lectura. Tu propia solución sigue yendo en `taller/solucion/`.

Las soluciones se publican módulo a módulo a medida que se verifican. Hoy están en Java (niveles 1 a 9 y los seis proyectos de la ruta), en arquitectura hexagonal y en los talleres de prácticas (TDD, Karate, SDD y ODD).

## Qué código se puede ejecutar tal cual

| Contenido | Estado |
|---|---|
| Java, niveles 1 a 3 y lecciones 01 a 08 del nivel 4 | Java puro, compilado y ejecutado con JDK 21 |
| Java, resto (JUnit, Mockito, Spring, SQL, configuración) | Ilustrativo, sin ejecutar |
| TypeScript, Angular, React y Next.js | Ilustrativo, sin ejecutar |
| Arquitectura hexagonal | El proyecto del banco (38 archivos, 79 comprobaciones) se compiló con `-Xlint:all -Werror` y se ejecutó, incluidos la consola y el servidor HTTP. El proyecto con Spring Boot y JPA **no se compiló** contra las bibliotecas reales. El detalle está en [su índice](hexagonal/README.md#qué-código-se-ejecutó) |
| HikariCP | Los seis ejemplos en Java puro y el proyecto de la sesión 06 se compilaron y ejecutaron; el código con HikariCP y Spring es ilustrativo. El detalle está en [su índice](hikaricp/README.md#estado-del-código) |
| Prácticas (TDD, Karate, SDD, ODD) | Los ejemplos en Java puro de TDD y ODD se compilaron y ejecutaron; los archivos de Karate son ilustrativos. El detalle está en [su índice](practicas/README.md#estado-del-código) |
| Kafka y sagas | Los catorce ejemplos en Java puro y los dos proyectos sobre el banco hexagonal se compilaron y ejecutaron; el código con el cliente de Kafka y con Spring es ilustrativo. El detalle está en [su índice](kafka-y-sagas/README.md#estado-del-código) |
| Docker y Kubernetes | La aplicación de práctica se ejecutó; los manifiestos estándar y los archivos de Compose se validaron; los Dockerfile y las órdenes no se ejecutaron. El detalle está en [su índice](docker-kubernetes/README.md#estado-del-código) |

"Ilustrativo" significa que el código muestra la idea y la estructura, pero no se compiló al preparar el curso: puede requerir ajustes, y las versiones de los frameworks cambian. Hacerlo funcionar es parte de cada taller.

## Requisitos

- Ruta Java: JDK 21 o superior; Maven desde el nivel 4; Docker desde el nivel 7. El módulo de arquitectura hexagonal solo necesita el JDK hasta su sesión 07.
- Rutas de frontend: Node.js en una versión LTS vigente.
- Talleres de Docker y Kubernetes: Docker, `kubectl` y una herramienta de clúster local como kind.
- Git.

## Autor

Juan Sebastián Charry Rodríguez — [LinkedIn](https://www.linkedin.com/in/juan-sebastian-charry-rodriguez-a6a74113a/)
