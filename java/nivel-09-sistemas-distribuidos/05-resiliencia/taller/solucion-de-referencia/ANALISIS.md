# Solución — n9-05 Resiliencia

`ResilienciaTest` (8 pruebas). Tres dependencias reales en procesos Spring Boot 4.1.1 propios (puertos 43093 catálogo, 43094 recomendaciones, 43095 reseñas) y el servicio (43091, 10 hilos de Tomcat) con Resilience4j 2.4.0 (`resilience4j-spring-boot4`, anotaciones, configuración en `servicio.yaml`). Corrida del 2026-11-15.

## 1. Clasificación y qué pasa al caer cada una

| Dependencia | Clase | Si cae | Comprobado |
|---|---|---|---|
| Catálogo | **Crítica** (sin ella no hay producto que mostrar) | `503 SERVICIO_NO_DISPONIBLE` limpio y rápido | sí |
| Recomendaciones | Opcional | Página `200` con "más vendidos" y `"degradado": true` | sí |
| Reseñas | Opcional | Página `200` con `"disponibles": false` (no "0 reseñas") | sí |

## 2. Cortacircuitos, tiempo límite y mamparo: transiciones medidas

Configuración (`servicio.yaml`): ventana de 10, mínimo 5 llamadas, umbral 50 %, 2 s abierto, 2 llamadas de prueba; mamparo de 3 llamadas concurrentes sin espera; el tiempo límite (400 ms) está en el cliente HTTP.

- Con la dependencia tardando 2 s: la petición se resuelve en menos de 1,2 s con el respaldo (el tiempo límite del cliente cuenta como fallo).
- Con la dependencia fallando: tras 10 peticiones el cortacircuitos pasa a **OPEN**. La métrica `resilience4j.circuitbreaker.state{name=recomendaciones,state=open}` vale 1 y `/actuator/circuitbreakers` muestra `OPEN`.
- Abierto, 20 peticiones más: **la dependencia no recibió ninguna llamada** (contador sin cambios) y todas respondieron con el respaldo.
- Al recuperarse la dependencia y pasar 2 s: **HALF_OPEN** (gauge `state=half_open` = 1); 2 llamadas buenas → **CLOSED**. Transiciones observadas: `CLOSED->OPEN, OPEN->HALF_OPEN, HALF_OPEN->CLOSED`.

**Hallazgo de orden:** poner `fallbackMethod` también en `@Bulkhead` hizo que el cortacircuitos **nunca se abriera**: el mamparo es la capa interior (el orden de Resilience4j es Retry, CircuitBreaker, RateLimiter, TimeLimiter, Bulkhead de fuera a dentro), su respaldo traga la excepción y el cortacircuitos solo ve éxitos. El respaldo va en la capa más externa (el cortacircuitos), como en el README.

## 3. Sin mamparo, una opcional lenta deja sin servicio una ruta que no la usa

Recomendaciones tarda 3 s. **Antes** (`/pagina-sin-mamparo`, cliente con 6 s de espera, sin mamparo ni cortacircuitos): 15 peticiones ocupan los 10 hilos de Tomcat; `/precio/1`, que no usa recomendaciones, **no respondió en 1 s** (`HttpTimeoutException`). Al terminar la carga se recuperó.

**Después** (`/pagina`: límite de 400 ms, mamparo de 3, cortacircuitos): con la misma carga de 15 peticiones, `/precio/1` respondió **200 en 2 ms**; las 15 páginas respondieron `200`, las que no cupieron en el mamparo recibieron el respaldo al instante (< 300 ms).

## 4. Respaldo adecuado y respaldo engañoso

| Ruta | Con el libro mayor caído | Juicio |
|---|---|---|
| `/saldo-enganoso/9` | `200 {"saldo": 0}` | **Engañoso**: el usuario cree que no tiene dinero (o el sistema cobra comisión por saldo insuficiente). Convierte un fallo en un dato falso. |
| `/saldo/9` | `503 SERVICIO_NO_DISPONIBLE` | **Adecuado** para un dato crítico: se admite que no se sabe. |
| `recomendaciones` (paso 1) | `200` con más vendidos y `"degradado": true` | **Adecuado** para un dato opcional: contenido razonable, marcado. |

Regla: un respaldo es honesto si no inventa un dato que el usuario usará para decidir, y deja huella (marca, métrica).

## 5. Límite de tasa por cliente

`FiltroLimiteDeTasa`: un `RateLimiter` de Resilience4j por valor de `X-Cliente` (5 peticiones cada 2 s, sin espera). Cliente A: las 5 primeras pasan el filtro, las 3 siguientes reciben **`429`** con **`Retry-After: 2`**; el cliente B no se ve afectado; 2,1 s después, A vuelve a pasar.

## 6. Experimento de caos

40 peticiones por segundo a `/pagina/1` durante 14 s. A los 3 s se **apaga el proceso de recomendaciones** (opcional), a los 8 s se **enciende de nuevo**, a los 11 s se **apaga el catálogo** (crítico). Lo que observó el usuario (de `target/caos.md`):

| Segundo | Peticiones | 200 completas | 200 degradadas | 503 | Otros | p95 ms |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 40 | 40 | 0 | 0 | 0 | 7 |
| 1 | 40 | 40 | 0 | 0 | 0 | 9 |
| 2 | 40 | 40 | 0 | 0 | 0 | 3 |
| 3 | 40 | 1 | 39 | 0 | 0 | 7 |
| 4 | 40 | 0 | 40 | 0 | 0 | 4 |
| 5 | 40 | 0 | 40 | 0 | 0 | 3 |
| 6 | 40 | 0 | 40 | 0 | 0 | 3 |
| 7 | 40 | 0 | 40 | 0 | 0 | 2 |
| 8 | 40 | 0 | 40 | 0 | 0 | 3 |
| 9 | 40 | 32 | 8 | 0 | 0 | 3 |
| 10 | 40 | 40 | 0 | 0 | 0 | 3 |
| 11 | 40 | 11 | 0 | 29 | 0 | 2 |
| 12 | 40 | 0 | 0 | 40 | 0 | 1 |
| 13 | 40 | 0 | 0 | 40 | 0 | 1 |
| 14 | 12 | 0 | 0 | 12 | 0 | 1 |

Descripción:

- **Segundos 0-2:** todo completo, p95 de 3-9 ms.
- **Segundos 3-8 (recomendaciones apagada):** ningún error. Desde el primer segundo las páginas salen con "más vendidos" (`degradadas`); apenas una petición en el segundo 3 vio la caída completa antes de que se abriera el cortacircuitos. La latencia **bajó** (p95 de 2-4 ms), porque con el circuito abierto ni se intenta la llamada.
- **Segundo 9 (recuperación):** recomendaciones volvió a los 8 s, pero el cortacircuitos siguió abierto hasta cumplir sus 2 s y probar con la semiabierta: 32 completas y 8 degradadas; en el 10 todo normal. Es el costo de no reintentar a ciegas.
- **Segundos 11-14 (catálogo apagado):** `503` limpio en 1-2 ms para todos (crítica: no hay degradación posible). No hay hilos colgados ni errores de conexión sin explicar.

Qué se aprendió: la caída de lo opcional es invisible para el usuario salvo por la calidad del contenido; la caída de lo crítico es visible pero inmediata. Faltó (y se anotaría como mejora) una alerta sobre el estado `OPEN` del cortacircuitos: sin ella, el servicio funciona degradado y nadie se entera.


## Hallazgo: un método de respaldo no público falla de forma intermitente bajo concurrencia

`ResilienciaTest.paso3`, al ejecutarse varias veces, recibió dos respuestas 500 de 15 peticiones a `/pagina/1` y volvió a ocurrir en 1 de 4 repeticiones. La causa: `IllegalAccessException: class io.github.resilience4j.spring6.fallback.FallbackMethod cannot access a member of class servicio.Recomendaciones with package access`. Con Resilience4j 2.4.0, `FallbackMethod.invoke` hace "comprobar si es accesible, hacerlo accesible, invocar, volver a cerrar el acceso"; con respaldos simultáneos un hilo cierra el acceso mientras otro lo usa. Afecta a cualquier `fallbackMethod` que no sea `public`.

- Corrección en la solución: `masVendidos` y `sinResenas` son `public`.
- Prueba de regresión `paso5c`: 5 rondas de 200 peticiones simultáneas con las dos dependencias opcionales caídas (1000 peticiones, 2000 respaldos); exige cero respuestas distintas de 200.
- Por eso el respaldo del README es `public`: con un respaldo `private` (por ejemplo `sinRecomendaciones`), 200 llamadas simultáneas con el destino siempre fallando producen `IllegalAccessException ... with modifiers "private"`. Con el respaldo `public` la misma prueba pasa. El README lo indica con una frase que cita la versión.

## Otros dos ajustes de la solución

- El límite de tasa probaba una ruta que no existía (`/limitado/ping` daba 404 y la prueba lo aceptaba). Ahora existe `GET /limitado/ping` y las cinco primeras peticiones deben dar 200.
- El registro de limitadores crecía sin tope, con una entrada por cada valor de `X-Cliente` (que controla quien llama): era la "cola sin límite" que la lección critica. Ahora es un LRU acotado (10.000 por defecto, propiedad `limite.max-clientes`; `paso5b` lo fija en 300, envía 500 clientes distintos y comprueba que el registro se queda en 300). Con el tope real de 10.000 la prueba abría 10.500 conexiones seguidas y agotaba los puertos efímeros de macOS (`Can't assign requested address`) en las pruebas que corrían después.
