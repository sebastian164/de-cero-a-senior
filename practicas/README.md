# Talleres de prácticas: TDD, Karate, SDD y ODD

Cuatro prácticas que responden a la misma pregunta desde sitios distintos: **¿cómo sé que esto hace lo que debe?**

| Parte | Qué es | Responde |
|---|---|---|
| [TDD](tdd/) | Desarrollo guiado por pruebas | ¿Esta función hace lo que debe, y puedo cambiarla sin miedo? |
| [Karate](karate/) | Pruebas de API con el marco Karate | ¿El servicio, visto desde fuera, cumple su contrato? |
| [SDD](sdd/) | Desarrollo guiado por especificaciones | ¿Lo que se construyó es lo que se pidió? |
| [ODD](odd/) | Desarrollo guiado por la observabilidad | ¿Funciona en producción, con usuarios reales? |

Son comunes a las tres rutas del curso. Los ejemplos con código usan Java, pero las técnicas no dependen del lenguaje.

## Orden

El recomendado es el de la tabla: cada parte usa algo de la anterior. Se pueden hacer por separado.

### [TDD](tdd/)

| # | Sesión |
|---|---|
| 01 | [Rojo, verde, refactor](tdd/01-rojo-verde-refactor/) |
| 02 | [De afuera hacia adentro, y los dobles de prueba](tdd/02-de-afuera-hacia-adentro/) |
| 03 | [TDD sobre código heredado](tdd/03-codigo-heredado/) |
| 04 | [Los límites de TDD y la calidad de las pruebas](tdd/04-limites-y-calidad-de-las-pruebas/) |

### [Karate](karate/)

| # | Sesión |
|---|---|
| 01 | [Primera prueba de API con Karate](karate/01-primera-prueba-de-api/) |
| 02 | [Aserciones, datos y reutilización](karate/02-aserciones-datos-y-reutilizacion/) |
| 03 | [Mocks, ejecución en paralelo e integración continua](karate/03-mocks-paralelo-y-ci/) |

### [SDD](sdd/)

| # | Sesión |
|---|---|
| 01 | [Especificar antes de construir](sdd/01-especificar-antes-de-construir/) |
| 02 | [De la especificación al plan y a las tareas](sdd/02-del-plan-a-las-tareas/) |
| 03 | [Mantener la especificación viva, y cuándo no vale la pena](sdd/03-mantener-la-especificacion-viva/) |

### [ODD](odd/)

| # | Sesión |
|---|---|
| 01 | [Preguntas antes que código](odd/01-preguntas-antes-que-codigo/) |
| 02 | [Trazas, métricas y objetivos de servicio](odd/02-trazas-metricas-y-objetivos/) |
| 03 | [Diagnosticar con telemetría y probar la observabilidad](odd/03-diagnosticar-con-telemetria/) |

## Cuándo hacerlos

| Parte | Después de |
|---|---|
| TDD | El [Nivel 4 de Java](../java/nivel-04-clean-code-testing/), o su equivalente en tu ruta de frontend. Profundiza con práctica lo que la sesión 11 de ese nivel presenta |
| Karate | TDD 01, y saber qué es una API HTTP |
| SDD | TDD y Karate ayudan: los criterios de aceptación se convierten en pruebas |
| ODD | Los [talleres de Docker](../docker-kubernetes/docker/), para levantar el entorno |

## El sistema bajo prueba

Karate, SDD y ODD trabajan sobre [Bitácora](../docker-kubernetes/app/), la aplicación de los talleres de Docker: una API en un solo archivo Java que arranca con `java Servidor.java`. Es pequeña a propósito, para que la atención esté en la práctica.

## Cómo es cada sesión

El ciclo de la [metodología](../METODOLOGIA.md): idea con analogía, lo esencial, un ejemplo que funciona pero está mal, su análisis, la versión corregida, errores comunes, taller con criterios de aceptación, cómo aparece en el trabajo real y preguntas senior.

## Cómo entregar

Una rama por taller (`taller/tdd-01`, `taller/karate-02`, `taller/sdd-03`, `taller/odd-01`…), tu trabajo en `taller/solucion/` dentro de la sesión, un `ANALISIS.md` y un pull request contra `main`. No hay soluciones de referencia publicadas.

## Estado del código

| Qué | Cómo se comprobó al preparar el curso |
|---|---|
| Ejemplos de TDD (cuatro programas) y el código heredado de inicio | Compilados con `-Xlint:all -Werror` y ejecutados con JDK 21 |
| Ejemplos de ODD 01 (dos programas) | Compilados y ejecutados con JDK 21 |
| API de Bitácora, contra la que corren Karate, SDD y ODD | Ejecutada; sus respuestas son las que esperan los archivos de Karate |
| Archivos `.feature` y configuración de Karate | Ejecutados con Karate 2.1.4 y JDK 21 contra la API real y contra el simulador de la sesión 03 |
| Compose del entorno de observabilidad | Levantado con Docker Compose y `grafana/otel-lgtm:0.40.0`; la telemetría se consultó desde Grafana |
| Órdenes de herramientas (PIT, OpenTelemetry) | Ejecutadas: PIT 1.30.2 con JaCoCo 0.8.14, y el agente de OpenTelemetry para Java 2.33.1 |
| Herramientas de SDD | No se ejecutaron: cada taller remite a la documentación de la herramienta |

Versiones vigentes al escribir: Karate 2.1 (requiere Java 21) y Angular, Kafka y Kubernetes como se indica en sus rutas. Las herramientas de SDD cambian cada pocos meses: por eso esa parte enseña la secuencia y las puertas de revisión, y no las órdenes de una herramienta concreta.
