# Taller 12-02: decisiones y ADR (solución)

Proyecto: el servicio `pedidos` (el de los talleres 10-02, 10-03 y 10-05).

## 1. Tres ADR

### ADR-001: Memoria de la JVM como porcentaje del límite del contenedor

Estado: Aceptada (2026-11-16).

**Contexto.** Con `-Xmx1g` fijo y un límite de 300 Mi, el núcleo mata el contenedor (`OOMKilled`, código 137) sin dejar excepción de Java. Medido en el taller 10-03.
**Decisión.** Usar `-XX:MaxRAMPercentage` sobre el límite del contenedor y nunca `-Xmx`. Con el límite de 768 Mi, el 75 % da 576 MiB de montón (lo que se comprueba en el taller 10-03 con `memoriaMaximaMb`, entre 500 y 580).
**Alternativas.** `-Xmx` fijo (se desincroniza del límite al cambiarlo); 90 % (deja sin margen al metaspace, hilos y memoria directa); sin ajuste (la JVM toma un cuarto de la memoria y desperdicia el resto).
**Consecuencias.** (+) Cambiar el límite cambia el montón sin tocar la imagen; un exceso de uso da `OutOfMemoryError` visible en los registros. (-) Con límites pequeños (300 Mi) el 75 % no alcanza y hay que bajar el porcentaje (50 % en el taller). (-) El margen sin montón debe revisarse al añadir bibliotecas nativas.

### ADR-002: Apagado ordenado más `preStop` de 10 s

Estado: Aceptada.

**Contexto.** En un despliegue continuo, el Service sigue enrutando unos segundos a un pod que ya recibió `SIGTERM`.
**Decisión.** `server.shutdown=graceful`, `terminationGracePeriodSeconds: 45` y `preStop: sleep 10`.
**Alternativas.** No hacer nada, es decir, apagado inmediato (corta peticiones en vuelo: de 3 a 8 cortadas, además de conexiones rechazadas, en pruebas de 70 s con peticiones de 8 s del taller 10-03); solo apagado ordenado, que ya es el valor por defecto desde Spring Boot 3.4 pero no cubre la ventana de propagación del Service (no corta peticiones en vuelo, pero puede rechazar conexiones: de 0 a 7 según la ejecución; el total de fallos no lo distingue de forma fiable del apagado inmediato, el tipo de fallo sí); *blue/green* (duplica entorno).
**Consecuencias.** (+) 0 peticiones fallidas medidas (misma gracia de 45 s y misma carga en las tres variantes). (-) Cada pod tarda hasta 10 s más en terminar: los despliegues son más lentos. (-) El valor de 10 s es empírico y depende del clúster.

### ADR-003: Migraciones Flyway al arrancar la aplicación

Estado: Aceptada; revisable si el equipo crece a varios servicios sobre una misma base.

**Contexto.** Hay que aplicar el esquema en cada ambiente sin pasos manuales.
**Decisión.** Flyway embebido, migraciones versionadas e inmutables en `db/migration`.
**Alternativas.** Un `Job` de Kubernetes antes del despliegue (más control, más piezas); migrar a mano (error humano); Liquibase (sin ventaja para el equipo).
**Consecuencias.** (+) Un solo artefacto sabe crear su esquema; el pipeline lo prueba desde cero. (-) La reversa de imagen no revierte el esquema: migraciones compatibles hacia atrás y despliegue en dos pasos para cambios destructivos. (-) El usuario de la aplicación necesita permisos de DDL.

## 2. Diez decisiones: reversible o no

| # | Decisión | Tipo | Tiempo dedicado | Proporcional |
|---|---|---|---|---|
| 1 | Nombre de una clase del controlador | Reversible | Minutos | Sí |
| 2 | Tamaño del pool de conexiones | Reversible | Medición de una tarde | Sí |
| 3 | Caché Caffeine para `reporte-cliente` | Reversible | Una hora | Sí |
| 4 | Esquema de la tabla `pedidos` | **Irreversible** | Una conversación | **No: faltó análisis de crecimiento** |
| 5 | Formato del evento `pedidos-creados` en Kafka | **Irreversible** (contrato público) | Un día | Sí, con versión en el evento |
| 6 | Kubernetes como plataforma | **Irreversible en la práctica** | Sin documento | **No: sin ADR ni comparación con alternativas** |
| 7 | Java 21 | Reversible con costo moderado | Revisión del equipo | Sí |
| 8 | Sesión en Redis | Reversible | Una hora | Sí |
| 9 | PostgreSQL como almacén | **Irreversible** | Debate largo | Sí |
| 10 | Prefijo de la imagen del registro | Reversible | Minutos | Sí |

Conclusión: de cuatro puertas de un solo sentido, dos (4 y 6) recibieron menos tiempo del debido. Acción: ADR retroactivo para ambas.

## 3. Una decisión sin razón conocida

**Hallazgo:** la sonda de arranque (`startupProbe`) usa `failureThreshold: 30` y `periodSeconds: 2`.
**Investigación:** el historial del repositorio y una medición de arranque (2,3 s en los registros del pod `Started AplicacionPedidos in 2.302 seconds`) explican el valor: 30 × 2 = 60 s de margen, unas 25 veces el arranque medido, para un arranque frío con migraciones o una base lenta.
**Documentado como ADR-004:** el valor es un margen amplio sobre el arranque medido (2,3 s); si las migraciones crecen, hay que remedirlo.

## 4. Spike

- **Pregunta:** ¿puede el acortador (11-03) servir 5.000 redirecciones/s p95 < 50 ms con una sola instancia y caché en memoria?
- **Límite:** 4 horas; el código se descarta.
- **Criterio de decisión:** si p95 < 50 ms con 5.000 req/s en una instancia, no se añade caché distribuida; si no, se evalúa Redis. Resultado real en 11-03.

## 5. Propuesta de una página para no técnicos

**Asunto: dos semanas para que los despliegues no corten pedidos.**

Hoy, cada vez que publicamos una versión, un pequeño número de clientes ve un error. En nuestras pruebas, en una prueba de carga de 70 segundos, fallaron peticiones durante un despliegue (hasta 7 con la configuración por defecto de Spring y hasta 17 si se apaga de golpe; si se apaga de golpe, además, se cortan peticiones que ya estaban a medias). Proponemos dos semanas de trabajo para cambiar la forma en que apagamos cada instancia, de modo que termine lo que está haciendo antes de salir. Lo probamos y en la misma prueba no falló ninguna petición. **Costo:** dos semanas de una persona. **Riesgo:** bajo; se puede revertir en minutos. **Efecto visible:** los despliegues tardan unos 10 segundos más. **Qué ganamos:** podemos publicar en horario laboral sin avisar a nadie.
