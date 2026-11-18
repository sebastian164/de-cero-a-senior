# Taller 06: avisos de cambio de estado con un flujo de cambios

MongoDB 9.0.8 como conjunto de réplicas de un nodo (el MongoDB del entorno del curso, puerto 27018), Spring Boot 4.1.2 y el driver 5.8.1 de la plataforma. Programa único con modos (`--modo=confirmador|notificador|sondeo|servicio`). Pruebas automáticas: `mvn test` (4 pruebas, en el mismo proceso con el MongoDB real).

## Diseño
- El confirmador crea pedidos y cambia el estado de cada uno (`PENDIENTE` a `CONFIRMADO` o `RECHAZADO`) a ritmo continuo, con el campo `cambiado`.
- El flujo se filtra en el servidor: `$match` de `operationType = update` y `updateDescription.updatedFields.estado` existente. El `aggregate` que el driver envió, capturado con un `CommandListener`:
  `[{"$changeStream": {}}, {"$match": {"operationType": "update", "updateDescription.updatedFields.estado": {"$exists": true}}}]`
- **Orden: primero el aviso, después la ficha** (paso 3). Si la caída llega entre las dos, al reanudar el evento vuelve a llegar (al menos una vez) y el índice único `(pedidoId, estado)` lo vuelve inofensivo. En el orden contrario, una caída entre la ficha y el aviso pierde el aviso para siempre: no hay nada que lo reintente.
- La ficha vive en `fichas` (documento `notificador`).

## Paso 4: detención ordenada
Confirmador: 100 cambios (10 por segundo). Al notificador se le envía SIGTERM a los 3 s (14 avisos hechos), se deja detenido 5 s y se reinicia. Resultado: `avisos=100, pedidos con aviso=100, duplicados=0`. Ni faltan ni sobran. (Un shell no interactivo ignora SIGINT en procesos de fondo; SIGTERM activa el mismo gancho de cierre de la JVM que Ctrl+C.)

## Paso 5: caída brusca
Se configura `parada-tras-aviso=7`: el proceso hace `Runtime.halt(137)` justo después de insertar el aviso 7 y antes de guardar la ficha (equivale a `kill -9`; código de salida 137).
| indice-unico | Resultado tras reanudar (20 cambios) |
|---|---|
| false | `avisos=21, pedidos con aviso=20, duplicados=1`: el aviso 7 se duplicó |
| true | `avisos=20, pedidos con aviso=20, duplicados=0` |
Las pruebas `sinIndiceUnicoUnaCaidaBruscaDuplicaElAviso` (7 avisos para 6 cambios) y `conIndiceUnicoUnaCaidaBruscaNoDuplica` (6) lo repiten dentro de la suite.

## Paso 6: flujo de cambios contra sondeo cada 500 ms (40 cambios, uno cada 200 ms)
| | Latencia media (cambio a aviso) | Máxima | Comandos sobre `pedidos` |
|---|---|---|---|
| Flujo de cambios | 4,5 ms | 23 ms | `aggregate` 1 + `getMore` 46 |
| Sondeo | 251,7 ms | 494 ms | `find` 27 |
La latencia del sondeo es la mitad del intervalo (~250 ms) por construcción; la del flujo es la del propio servidor. Los `getMore` del flujo son un cursor abierto que el servidor responde cuando hay algo (o al vencer su espera), no consultas ciegas; el sondeo hace `find` aunque no haya nada (27 en 13 s, más cuanto menor el intervalo).

## Paso 7: SSE
`GET /pedidos/s1/estados` (puerto 8080) abre un flujo de cambios filtrado por `documentKey._id` en el servidor. Con el cliente `curl -N` abierto se cambió `s1` a `CONFIRMADO`, se cambió otro pedido (`otro`) y se cambió `s1` a `ENVIADO`; el cliente recibió exactamente `data:CONFIRMADO` y `data:ENVIADO`, nada del otro pedido.
