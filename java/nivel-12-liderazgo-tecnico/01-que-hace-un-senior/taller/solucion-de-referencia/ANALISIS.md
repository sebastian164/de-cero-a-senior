# Taller 12-01: qué hace un senior (solución)

Caso: una persona que construyó el servicio `pedidos` de la ruta (Spring Boot, Flyway, Kafka, Redis, Kubernetes). Los ejemplos salen de ese trabajo real.

## 1. Autoevaluación (una fila, un ejemplo reciente)

| Fila | Dónde estoy | Ejemplo concreto |
|---|---|---|
| Recibe | Senior | Me pidieron "que el servicio sobreviva a un despliegue"; el problema no venía descrito: lo traduje en sondas, apagado ordenado y un `preStop`. |
| Entrega | Intermedio | Entregué el Deployment, pero no el *runbook* ni el tablero que dirían si el despliegue falla en producción. |
| Ambigüedad | Senior | Ante "OOMKilled" no pedí ayuda: medí con `-Xmx1g` y 300 Mi, reproduje la muerte y comparé con `MaxRAMPercentage`. |
| Impacto | Intermedio | Mi cambio mejora mi servicio; no dejé una plantilla que otros equipos reutilicen. |
| Ante un problema | Senior/Intermedio | Comparé tres estrategias de generación de código, pero no consulté a quien opera el sistema antes de elegir. |

**Las dos filas más lejos de senior:** *Impacto* (entrego para mi servicio, no para el sistema de varios equipos) y *Entrega* (falta la parte operable: tableros, alertas, runbook).

## 2. Caso: "que las peticiones no fallen al desplegar"

Versión junior: añadir réplicas y esperar que baste.

Versión senior:

- **Preguntas:** ¿qué significa fallar (error 5xx, timeout)? ¿cuántas peticiones por segundo, de qué duración? ¿Qué tolera el negocio durante un despliegue? ¿Quién despliega y con qué frecuencia?
- **Alternativas:** (a) nada, aceptar errores; (b) apagado ordenado de Spring; (c) `preStop` con espera para que el Service deje de enrutar; (d) `maxSurge`/`maxUnavailable` más conservadores; (e) *blue/green*.
- **Métrica:** peticiones fallidas durante un `rollout restart` con carga constante (k6), clasificadas por tipo de fallo. Medido en el taller 10-03 con la misma gracia (45 s), una variable por vez, 20 usuarios y peticiones de 8 s: con `SERVER_SHUTDOWN=immediate` y sin `preStop`, el apagado corta peticiones en vuelo (en tres ejecuciones, de 3 a 8 cortadas, con duraciones de unos 4,4 s) y además rechaza conexiones (de 9 a 10); con apagado ordenado y sin `preStop`, ninguna petición en vuelo se corta, pero pueden rechazarse conexiones mientras el Service aún enruta al pod (de 0 a 7 según la ejecución); con apagado ordenado más `preStop`, 0 de ambos tipos. El total de fallos NO separa de forma fiable la primera y la segunda variante (rangos vistos: a de 4 a 17, b de 0 a 7); lo que sí separa es el tipo: solo (a) corta peticiones en vuelo. Con peticiones de 2 s no se observó ningún corte en vuelo, por eso el taller usa 8 s. Nota: desde Spring Boot 3.4 el apagado ordenado ya es el valor por defecto, así que la variante "sin apagado ordenado" hay que pedirla a propósito.
- **Decisión:** (b)+(c); (e) se descarta por costo de duplicar el entorno para el volumen actual.

## 3. Compromisos que nadie mencionó

Decisión: usar Flyway al arrancar la aplicación (la migración corre en cada pod).

| Costo no dicho | Efecto |
|---|---|
| Varias réplicas arrancan a la vez | Flyway usa un bloqueo, pero un despliegue lento por una migración larga retrasa la sonda de arranque. |
| La migración y el código viajan juntos | La reversa de la imagen no revierte el esquema: toda migración debe ser compatible hacia atrás. |
| El usuario de la aplicación necesita permisos de DDL | Mínimo privilegio roto en producción. |

## 4. Multiplicar: tres cosas que solo yo sé

| Conocimiento | Cómo deja de depender de mí |
|---|---|
| Cómo reproducir el `OOMKilled` y leer `describe pod` | Runbook de una página con los comandos y la salida esperada. |
| Por qué el límite de memoria usa 75 % y no 90 % | ADR con la medición (ver 12-02). |
| El orden correcto para reconstruir el entorno local | `verificar.sh` ejecutable: la documentación que se prueba sola. |

## 5. Plan de seis meses

| Objetivo | Evidencia observable |
|---|---|
| Que cualquiera del equipo pueda operar un despliegue | Dos compañeros ejecutan un despliegue y una reversa siguiendo solo el runbook, sin preguntas. |
| Escribir un ADR por decisión que no sea reversible | Al menos 6 ADR en el repositorio, cada uno con alternativas y consecuencias negativas. |
| Reducir el tiempo de revisión de mis cambios | Mediana de tiempo hasta primera revisión menor a un día hábil, medida en el tablero del repositorio. |
