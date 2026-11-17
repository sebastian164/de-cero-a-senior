# 05 — Operación en producción y taller integrador

Los nombres de las métricas y los cuatro mensajes de registro de esta sesión se comprobaron con una aplicación Spring Boot 4.1.1, Actuator, HikariCP 7.1.0 y PostgreSQL 17: las ocho métricas existen con esos nombres y la etiqueta `pool`, y cada mensaje salió tal como se cita. Las dos alertas de la sesión se cargaron en un Prometheus real y el panel en un Grafana real (en Prometheus las métricas se llaman `hikaricp_connections_pending`, `hikaricp_connections_timeout_total`, etc.; para el percentil 95 hay que activar `management.metrics.distribution.percentiles-histogram.hikaricp.connections=true` y exponer `prometheus`). Los nombres pueden cambiar con otras versiones: confírmalos en tu aplicación antes de construir alertas sobre ellos.

## Idea

El tablero de un carro otra vez, pero para una sola pieza. No hace falta saber de mecánica para entender tres agujas: cuánta gasolina queda, a qué temperatura va el motor y a cuántas revoluciones. El pool tiene las suyas, y con tres o cuatro se sabe si está sano, si se está quedando corto o si alguien no está devolviendo lo que toma.

## Lo esencial

### Las métricas del pool

Con Spring Boot y Actuator aparecen solas, etiquetadas con el nombre del pool:

| Métrica | Qué dice | Qué mirar |
|---|---|---|
| `hikaricp.connections.active` | Cuántas están prestadas ahora | Pegada al máximo durante mucho tiempo: retención o fuga |
| `hikaricp.connections.idle` | Cuántas están libres | Cero de forma sostenida: no queda margen |
| `hikaricp.connections.pending` | Cuántos hilos esperan una conexión | **Cualquier valor sostenido por encima de cero es una señal** |
| `hikaricp.connections.max` | El máximo configurado | Para comparar |
| `hikaricp.connections.acquire` | Cuánto se tarda en conseguir una conexión | Debería ser casi cero. Si crece, hay cola |
| `hikaricp.connections.usage` | Cuánto tiempo se retiene cada conexión | Compáralo con el tiempo de tus consultas |
| `hikaricp.connections.creation` | Cuánto tarda en abrirse una nueva | Si crece, la base o la red van mal |
| `hikaricp.connections.timeout` | Cuántas peticiones se quedaron sin conexión | Debería ser siempre cero. Es la alerta |

### Leer el pool de un vistazo

| Activas | Pendientes | Uso | Diagnóstico probable |
|---|---|---|---|
| Bajas | Cero | Corto | Sano |
| En el máximo durante los picos | Sube y baja | Corto | Falta capacidad de verdad, o la base va lenta |
| En el máximo | Alto | **Largo** | Retención: algo se queda con la conexión sin usarla (sesión 04) |
| Sube en escalera y no baja nunca | Acaba subiendo | — | Fuga (sesión 03) |
| Normales | Cero | Corto, pero `creation` alto y errores | La base o la red: mira del otro lado |

### Mensajes del registro que conviene reconocer

| Mensaje | Qué significa |
|---|---|
| `Connection is not available, request timed out after …ms` | Nadie devolvió una conexión a tiempo. Suele venir con el estado del pool: total, activas, libres y en espera |
| `Connection leak detection triggered for … on thread …` | Una conexión lleva prestada más que el umbral. Trae la traza de quien la tomó |
| `Failed to validate connection … Possibly consider using a shorter maxLifetime value` | Algo de fuera (la base, un proxy, un cortafuegos) cerró la conexión antes que el pool |
| `Thread starvation or clock leap detected` | El proceso estuvo parado un rato: pausas largas del recolector de basura, o un contenedor sin CPU |

### Varias instancias, una base

Cada instancia tiene su pool. Con autoescalado, el total de conexiones crece con el número de instancias hasta chocar con el límite de la base, y entonces **escalar empeora las cosas**: más instancias compitiendo por las mismas conexiones.

| Opción | Cuándo |
|---|---|
| Pools pequeños por instancia, calculados con el máximo de instancias | Siempre, como base |
| Un proxy de conexiones delante de la base (PgBouncer y similares) | Cuando hay muchas instancias o funciones sin servidor. Ojo: en modo de transacción, algunas características de sesión dejan de funcionar |
| Réplicas de lectura con su propio pool | Cuando las lecturas dominan |
| Subir el límite de la base | Solo si la base tiene memoria y procesadores para atenderlo |

### Cuando la base se cae y vuelve

- El pool descarta las conexiones rotas y abre otras. Mientras tanto, las peticiones fallan tras `connectionTimeout`: por eso debe ser corto.
- La comprobación de salud de la aplicación debería reflejar si puede conseguir una conexión, para que el tráfico no llegue a una instancia que no puede atender.
- Al arrancar, HikariCP por defecto falla rápido si no puede conectar, pero **solo si algo le pide la conexión al arrancar**. Con Spring Boot y solo `spring-boot-starter-jdbc`, el pool se crea en el primer uso: la aplicación arranca sin base y es la comprobación de disponibilidad (503) la que la deja fuera de rotación, sin reinicios. Con JPA (Hibernate toma una conexión al arrancar), con `spring.sql.init`, con migraciones o si creas el `HikariDataSource` a mano, el proceso termina con error, y en un orquestador eso se traduce en reinicios hasta que la base esté disponible. Ambos casos se comprobaron con Spring Boot 4.1.1 y HikariCP 7.1.0.
- Al apagar, el pool se cierra y las conexiones se liberan. Un proceso que muere de golpe las deja abiertas en la base hasta que esta las detecta.

## Ejemplo

### Antes

Tablero del servicio: CPU, memoria, peticiones por segundo. Alerta: "tasa de errores mayor al 5 %". Una noche la alerta suena. El registro está lleno de `Connection is not available`. Se sube el pool de 10 a 50 y se despliega. Mejora veinte minutos. Vuelve a sonar. Se sube a 100. La base llega a su límite de conexiones y empiezan a fallar otros tres servicios.

### Análisis

| Problema | Consecuencia |
|---|---|
| Ninguna métrica del pool en el tablero | No se sabía si era falta de capacidad, retención o fuga |
| La alerta estaba sobre el síntoma final | Sonó cuando los usuarios ya llevaban rato afectados |
| Se cambió el tamaño sin diagnóstico | Con una fuga, un pool más grande solo tarda más en vaciarse |
| No se hizo la cuenta contra el límite de la base | El problema de un servicio se exportó a los demás |

### Después

Lo que se tiene **antes** del incidente:

| Elemento | Definición |
|---|---|
| Panel | Activas, libres y pendientes en una gráfica; percentil 95 de `acquire` y de `usage` en otra; `timeout` acumulado |
| Alerta temprana | Hilos pendientes por encima de cero durante más de un minuto |
| Alerta grave | Cualquier incremento de `timeout` |
| Detección de fugas | Activada, con un umbral mayor que la operación legítima más larga |
| Cuenta de conexiones | Escrita junto a la configuración: instancias máximas × tamaño, frente al límite de la base |
| Guía de guardia | La tabla "leer el pool de un vistazo", con el primer paso para cada fila |

Con eso, la misma noche: la alerta temprana suena antes de que haya errores; el panel muestra las activas subiendo en escalera sin bajar; en el registro hay un aviso de fuga con una traza que apunta a un extremo desplegado esa tarde. Se revierte ese despliegue. Nadie toca el tamaño del pool.

## Errores comunes

- No tener ninguna métrica del pool.
- Alertar solo cuando ya hay errores.
- Cambiar el tamaño del pool en mitad de un incidente, sin diagnóstico.
- Olvidar el autoescalado en la cuenta de conexiones.
- Poner un proxy de conexiones sin revisar qué características de sesión usa la aplicación.
- Una comprobación de salud que no mira la base, o que la mira con una consulta pesada.
- Todos los servicios con la misma configuración copiada, sin su propia cuenta.

## Taller integrador

**Objetivo:** dejar un servicio con su pool dimensionado, vigilado y probado contra los fallos que ya conoces, y demostrarlo con un simulacro.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../hexagonal/), haz el simulacro sobre tu servicio de pedidos e incluye en el diagnóstico qué caso de uso retenía las conexiones y qué hacía mientras tanto.

Con el [entorno](../entorno/), una aplicación Spring Boot con tres extremos (una lectura rápida, una escritura y una operación que llama a un servicio externo simulado) y una herramienta de carga.

### Parte 1 — Dimensionar

1. Haz la cuenta de conexiones para tres instancias, con margen para un despliegue gradual y una reserva de administración, contra el límite de 30 del entorno. Escríbela junto a la configuración.
2. Confirma el tamaño con una prueba de carga, como en la sesión 02.

### Parte 2 — Ver

3. Expón las métricas del pool y construye el panel de la tabla.
4. Define las dos alertas y la detección de fugas.
5. Haz que la comprobación de disponibilidad de la aplicación falle cuando no se puede conseguir una conexión en un tiempo corto. ¿Por qué la de vida **no** debería depender de la base?

### Parte 3 — Simulacro

Con carga continua, provoca cada situación sin avisar a quien diagnostica (o sortea el orden si trabajas solo). Quien diagnostica solo puede mirar el panel y el registro.

| Situación | Cómo provocarla | Qué debe concluir el diagnóstico |
|---|---|---|
| Fuga | Un extremo que no devuelve la conexión en un camino raro | Fuga, y en qué línea |
| Retención | El servicio externo pasa a tardar tres segundos | Retención por llamada externa |
| Consulta lenta | Quitar un índice | La base es lenta, no el pool |
| Falta de capacidad real | Triplicar el tráfico | Hace falta más capacidad, y dónde |
| Caída de la base | Detener PostgreSQL treinta segundos | Se recupera sola; cuánto tardó |
| Conexiones cortadas desde fuera | Terminar las sesiones desde PostgreSQL | El pool las repone; qué vio el usuario |
| Demasiadas instancias | Arrancar seis en lugar de tres | Se alcanzó el límite de la base |

6. Para cada una: tiempo hasta el diagnóstico, qué métrica dio la pista y qué faltó.
7. Corrige lo que faltó y repite las dos que más te costaron.

### Parte 4 — Decidir

8. Pon PgBouncer delante de la base siguiendo su documentación y arranca seis instancias. ¿Qué cambia? ¿Qué característica dejó de funcionar o tuviste que ajustar?
9. Escribe la guía de guardia del pool: para cada alerta, qué significa y el primer paso. Incluye, en mayúsculas si hace falta, lo que **no** se hace.
10. Escribe la plantilla de configuración que propondrías para los servicios de tu equipo, con la cuenta que cada uno debe rellenar.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Las siete situaciones se diagnostican solo con panel y registro | La tabla del simulacro |
| Ninguna se "resolvió" subiendo el tamaño del pool sin justificación | `ANALISIS.md` |
| La cuenta de conexiones cubre despliegue y autoescalado | Parte 1 |
| La guía de guardia dice qué no hacer | Paso 9 |

**Entrega:** rama `taller/hikari-05`, con el código, el panel exportado, los resultados y los documentos en `taller/solucion/`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

El pool de conexiones es de esas piezas que nadie mira hasta que tumba un sistema, y cuando lo tumba, el instinto de subir el número casi siempre empeora las cosas. Quien sabe leer cuatro métricas y hacer una multiplicación resuelve en minutos un tipo de incidente que a muchos equipos les cuesta una noche.

## Preguntas senior

1. ¿Qué métricas del pool vigilas y qué alerta pones primero?
2. ¿Cómo distingues, con métricas, una fuga, una retención y una falta real de capacidad?
3. ¿Por qué escalar las instancias puede empeorar un problema de conexiones?
4. ¿Cuándo pondrías un proxy de conexiones y qué riesgos tiene?
5. Es de madrugada y el registro dice `Connection is not available`. ¿Qué haces, en orden, y qué no haces?
