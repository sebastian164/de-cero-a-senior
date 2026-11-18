# Taller 11-02: escalabilidad (resultados medidos)

Aplicación: `pedidos` (Spring Boot 4.1.2, hilos virtuales, pool de Hikari por defecto) con PostgreSQL 16 y 1.000.000 de pedidos; carga con k6, 10 s por nivel, clientes elegidos con sesgo hacia pocos populares. Todo corre en una sola máquina de 12 núcleos (la aplicación, la base y el generador de carga se pelean los mismos núcleos). El script las imprime en cada ejecución; las de abajo son de una ejecución (los órdenes de magnitud se repiten, los números exactos no).

## 1. Punto de saturación

| Usuarios virtuales | Peticiones/s | p95 (ms) |
|---|---|---|
| 1 | 24 | 51 |
| 4 | 83 | 65 |
| 16 | 85 | 298 |
| 32 | 91 | 667 |
| 64 | 99 | 1.316 |

El rendimiento se aplana desde unos 4 a 16 usuarios y la latencia sigue creciendo: **saturación**. El recurso que se agota es la **CPU de PostgreSQL** (≈1.050 % de CPU, es decir ~10 núcleos, frente a ~9 % de la JVM): cada petición recorre un millón de filas porque no hay índice (`EXPLAIN` muestra `Seq Scan`). Se mide el recurso, no se adivina.

Nota sobre la variabilidad: la máquina está compartida con otros procesos y las cifras cambian mucho entre ejecuciones (la línea base de 32 usuarios fue de 91 a 154 peticiones/s; con el índice, de 6.000 a 8.000). El orden de las ganancias (el índice vale más que todo lo demás) se repitió siempre.

## 2. Mejoras en orden, midiendo tras cada una (32 usuarios virtuales en todas las filas)

| Paso | Peticiones/s | p95 (ms) | Ganancia |
|---|---|---|---|
| Línea base (sin índice, 32 VUs) | 91 | 667 | |
| + índice sobre `cliente_id` | 6.370 | 11 | **x70** |
| + caché Caffeine (60 s) | 9.645 | 11 | x1,5 sobre el paso anterior |

**Segunda instancia.** Si las dos JVM corren en el anfitrión, la segunda instancia da x0,7 ("no mejora"): tras el índice y la caché el cuello ya no es la CPU de la aplicación, y las JVM, nginx y k6 se reparten los mismos núcleos. Para que la medición muestre el escalado horizontal, cada instancia corre en un contenedor con la CPU limitada (`--cpus=0.5`) tras nginx, de modo que **el cuello vuelve a ser la CPU de la aplicación**, y se compara una instancia con dos (mejor de tres mediciones de 10 s cada una, con calentamiento):

| Configuración | Peticiones/s | p95 (ms) |
|---|---|---|
| 1 instancia (0,5 CPU) tras nginx | 1.955 | 65 |
| 2 instancias (0,5 CPU cada una) tras nginx | 2.752 | 74 |
| Ganancia | **x1,41** (en otras ejecuciones, de x1,15 a x2,4) | |

La ganancia es menos que x2 porque nginx, k6 y PostgreSQL siguen compartiendo los núcleos de la máquina y porque la medición es ruidosa; el script exige al menos x1,3 y comprueba que el balanceador no pierde peticiones y que reparte entre las dos instancias. **Cuándo no ayuda** (el caso de las dos JVM sin tope): si el cuello es un recurso compartido (la base) o la máquina es la misma y las instancias no tienen un tope propio, el balanceador no crea capacidad, solo reparte la que haya.

## 3. El problema de la sesión local con dos instancias

Con balanceo por turnos, el contador de visitas de `/api/visitas` devuelve secuencias como `1,1,2,1` (la sesión solo existe en la instancia que la creó y el balanceador alterna). Con el perfil `redis` (sesión en Redis) devuelve `1,2,3,4`. Se comprueba en el script.

## 4. Particionamiento de la tabla de mensajes de un chat (diseño en papel, comprobado)

- **Clave elegida:** `chat_id` con partición por *hash* (4 particiones en la demostración; en producción decenas).
- **Justificación:** el patrón dominante es "los últimos N mensajes de un chat"; con `chat_id` toda la consulta cae en una partición (`EXPLAIN` toca solo `mensajes_pN`), y los chats se reparten parejo. Clave primaria `(chat_id, mensaje_id)`.
- **Consulta que queda difícil:** "todos los mensajes de un usuario" (o buscar texto en todos los chats): toca las 4 particiones (comprobado). Se resuelve con una tabla derivada por usuario (índice secundario global mantenido por eventos) o con un motor de búsqueda.
- **Riesgo adicional:** un chat gigante (un canal con millones de miembros) se convierte en una partición caliente; se mitiga con subpartición por rango de fecha dentro del chat.

## 5. Operación lenta movida a una cola

Envío de correo de 500 ms: la petición síncrona tarda **505 ms** de media; con `@Async` y respuesta 202, **1 ms**; el trabajo se completa después (la cola envió los correos). **Limitación:** esa "cola" es `@Async` en memoria: no es durable, no tiene límite y el trabajo pendiente se pierde si el proceso cae. Sirve para mostrar la latencia percibida; en producción sería una cola durable (el Kafka del Compose de 10-02, con un consumidor aparte). El usuario percibe 500 veces menos espera; se cede la certeza inmediata de que el correo se envió (hay que ofrecer estado y reintentos).

## 6. Siguiente paso de escalado para un sistema conocido (el servicio `pedidos`)

Tras índice y caché, el siguiente cuello esperado es el **pool de conexiones y la base primaria bajo escritura**. Paso propuesto: réplica de lectura para `/reporte/{cliente}` y partición por cliente solo si las escrituras superan lo que una primaria sostiene. **Métrica que lo justificaría:** CPU de la primaria sostenida sobre 60 % o percentil 95 de espera por conexión del pool sobre 50 ms durante un día pico. Sin esa métrica, no se añade nada.
