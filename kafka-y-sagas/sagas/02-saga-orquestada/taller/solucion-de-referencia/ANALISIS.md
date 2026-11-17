# Sagas 02 — El orquestador de la compra como máquina de estados duradera

Código en `src/main/java/tienda/`, pruebas en `src/test/java/tienda/OrquestadorTest.java` (12 pruebas). `mvn test` corre las pruebas y `java -cp target/classes tienda.Main` muestra los cuatro caminos.

| Pieza | Archivo | Sobrevive a una caída |
|---|---|---|
| Tabla de transiciones (función pura) | `Transiciones.java` | — (no tiene estado) |
| Orquestador, sin campos de estado de negocio | `Orquestador.java` | No: se reemplaza por uno nuevo |
| Almacén del orquestador: sagas, mensajes procesados, bandeja de salida; con transacciones | `Almacen.java` | **Sí** |
| Broker: colas por destino, entrega al menos una vez | `Broker.java` | **Sí** |
| Publicador de la bandeja de salida | `Publicador.java` | No |
| Participantes idempotentes con lápidas | `Participantes.java` | Sí (son otros servicios) |

## 1. Tabla de transiciones

| Estado | Respuesta | Nuevo estado | Orden que se envía |
|---|---|---|---|
| (inicio) | — | `RESERVANDO` | `reservar-inventario` → Inventario |
| `RESERVANDO` | `inventario-reservado` | `COBRANDO` | `cobrar` → Pagos |
| `RESERVANDO` | `sin-existencias` | `CANCELADA` | — |
| `COBRANDO` | `pago-aprobado` | `PROGRAMANDO_ENVIO` | `programar-envio` → Envíos |
| `COBRANDO` | `pago-rechazado` | `LIBERANDO` | `liberar-inventario` → Inventario |
| `PROGRAMANDO_ENVIO` | `envio-programado` | `COMPLETADA` (o `SUMANDO_PUNTOS` con fidelidad) | (`sumar-puntos` → Fidelidad) |
| `PROGRAMANDO_ENVIO` | `envio-imposible` | `REEMBOLSANDO` | `reembolsar` → Pagos |
| `SUMANDO_PUNTOS` | `puntos-sumados` | `COMPLETADA` | — |
| `REEMBOLSANDO` | `pago-reembolsado` | `LIBERANDO` | `liberar-inventario` → Inventario |
| `LIBERANDO` | `inventario-liberado` | `CANCELADA` | — |
| cualquiera | cualquier otra | (sin cambio) | — se registra como "fuera de lugar" |

Vencimientos (paso 6), en `Transiciones.alVencer`:

| Estado | Tras 3 intentos sin respuesta |
|---|---|
| `RESERVANDO` | → `LIBERANDO`, orden `liberar-inventario` (no sabemos si reservó) |
| `COBRANDO` | → `REEMBOLSANDO`, orden `reembolsar` (no sabemos si cobró) |
| `PROGRAMANDO_ENVIO`, `SUMANDO_PUNTOS`, `REEMBOLSANDO`, `LIBERANDO` | Se sigue reintentando: después del pivote y durante una compensación no se abandona |

## 2 y 3. Implementación y los cuatro caminos

El código sigue la tabla literalmente: `Transiciones` es el único sitio donde aparece un estado de destino. Salida real de `Main`:

```
s-1 → COMPLETADA        RESERVANDO → COBRANDO → PROGRAMANDO_ENVIO → COMPLETADA
s-2 → CANCELADA         RESERVANDO → COBRANDO --pago-rechazado--> LIBERANDO → CANCELADA
s-3 → CANCELADA         RESERVANDO --sin-existencias--> CANCELADA
s-5 → COMPLETADA        (toma la unidad que liberó s-2)
estado de los servicios: existencias=0 reservas=[s-1, s-5] cobros=[s-1, s-5] reembolsos=[] despachos=[s-1, s-5]
s-4 → CANCELADA         … PROGRAMANDO_ENVIO --envio-imposible--> REEMBOLSANDO → LIBERANDO → CANCELADA
estado de los servicios: existencias=5 reservas=[] cobros=[] reembolsos=[s-4] despachos=[]
inconsistencias: []
```

## 4. Caídas del orquestador en tres puntos

El orquestador es un objeto sin estado: `Tienda.reiniciarOrquestador()` lo tira y crea otro que solo comparte `Almacen` y `Broker`.

| Punto | Qué pasa | Prueba |
|---|---|---|
| Dentro de la transacción (tras cambiar el estado, antes de confirmar) | El almacén deshace todo; la respuesta no se confirmó en el broker y se reentrega; el nuevo orquestador la procesa desde cero | `sobreviveASuPropiaCaida[DENTRO_DE_LA_TRANSACCION]` |
| Tras confirmar la transacción y antes de confirmar la respuesta en el broker | La respuesta se reentrega; como su id ya está en `procesados`, se ignora como repetida | `sobreviveASuPropiaCaida[TRAS_CONFIRMAR_LA_TRANSACCION]` |
| El publicador cae tras publicar una orden y antes de borrarla de la salida | La orden sale dos veces; Pagos responde a la segunda con la respuesta guardada, sin cobrar otra vez | `sobreviveALaCaidaDelPublicadorTrasPublicar` |

En los tres la saga termina `COMPLETADA` con `pagos.efectos == 1`.

## 5. Caída entre guardar el estado y enviar la orden

Con `conSalida = false` el orquestador confirma el estado y **después** manda la orden. `sinSalidaLaCaidaEntreGuardarYEnviarDejaLaSagaAtascada` mata el proceso justo ahí con el reloj apagado: el estado dice `PROGRAMANDO_ENVIO` y Envíos nunca recibió nada (`envios.efectos == 0`). Nadie se entera: solo la consulta del paso 8 la ve.

Con bandeja de salida (`conSalida = true`), la orden es una fila de `Almacen.salida` escrita **en la misma transacción** que el estado. El punto de caída "entre guardar y enviar" deja de existir: si la transacción se confirma, la orden está en la salida y el publicador la llevará; si no, no hay ni estado ni orden. `conSalidaLaMismaCaidaNoExisteYLaSagaTermina` lo comprueba (la saga termina y no hubo caída posible).

Hallazgo: sin salida pero **con** tiempo límite, el reintento por vencimiento rescata la saga (reenvía la orden pendiente; prueba `sinSalidaPeroConRelojElReintentoLaRescata`). El tiempo límite tapa el agujero, pero tarde (3 ciclos) y por casualidad: outbox lo cierra por construcción.

## 6. Tiempo límite

`Orquestador.tic` cuenta ciclos sin respuesta; a los 3 reenvía **la misma orden con el mismo id**; tras 3 intentos aplica `alVencer`.

- `unaOrdenPerdidaSeReintenta`: Inventario pierde la primera orden; el reintento 1 la recupera y la saga se completa.
- `tresIntentosSinRespuestaCompensan`: Pagos pierde las tres órdenes de cobro; la saga pasa `COBRANDO --vencimiento--> REEMBOLSANDO`, Pagos recibe un reembolso de algo que no cobró, deja una lápida y la saga termina `CANCELADA` con las existencias de vuelta.

Lo que exige de los participantes:

1. **Idempotencia por id de orden**, devolviendo la misma respuesta a la orden repetida (no basta con ignorarla: si la primera respuesta se perdió, el orquestador necesita otra).
2. **Tolerar la compensación de algo que no hicieron** (o que aún no les llegó): liberar sin reserva y reembolsar sin cobro responden bien y dejan una **lápida**, para que la acción original, si llega tarde, se rechace.

## 7. Respuesta repetida y respuesta fuera de lugar

`respuestasRepetidasOFueraDeLugarNoLoCorrompen`:

| Caso | Qué hace el orquestador | Por qué |
|---|---|---|
| `inventario-reservado` dos veces (mismo id) | La segunda se ignora: "repetida" | Su id ya está en `procesados`, escrito en la misma transacción que la transición |
| `pago-aprobado` cuando la saga está en `LIBERANDO` | Se ignora: "fuera de lugar" y queda registrado | No hay fila en la tabla para (`LIBERANDO`, `pago-aprobado`). La saga ya decidió cancelar; si de verdad hubo un cobro tardío, la lápida de Pagos y el reembolso lo cubren |

La saga termina `CANCELADA` y consistente. Las ignoradas quedan en `Orquestador.ignoradas` para operación.

## 8. Sagas sin terminar

`Orquestador.atascadas(cicloActual, ciclos)` lista las sagas no terminales cuya última transición fue hace más de `ciclos`. En `consultaDeSagasAtascadas`, con Envíos caído, la saga queda en `PROGRAMANDO_ENVIO` reintentando para siempre (no se puede compensar tras el pivote) y la consulta la devuelve. En operación es la alerta que dice "hay compras cobradas sin despacho": sin ella, una saga atascada es invisible, porque no hay error, solo silencio.

## 9. Puntos de fidelidad

Sitios tocados: **tres**. Una fila en la tabla (`Transiciones`, la de `envio-programado` cambia de destino y se añade `puntos-sumados`), el valor `SUMANDO_PUNTOS` en `Estado` y el participante `Fidelidad`. En la sesión 01 fueron dos (participante y una línea en la lista de pasos). Aquí cuesta un poco más porque cada paso es un estado explícito, pero a cambio el paso es duradero y reintentable. `puntosDeFidelidadSoloEnLasCompletadas` comprueba que la saga cancelada no suma.

## 10. Reglas de negocio fuera del orquestador

La tentación natural es que el orquestador decida "si el total supera el límite de la tarjeta, cancelar sin cobrar", porque tiene el monto a mano. Es una regla de Pagos (el límite es de la tarjeta y lo conoce la pasarela), así que vive en `Participantes.Pagos.hacer`: el orquestador solo sabe que `pago-rechazado` lleva a `LIBERANDO`. Igual con las existencias: el orquestador nunca mira cuántas unidades hay. Lo que queda en el orquestador es solo el **flujo**: qué sigue, qué se compensa y cuándo se da por vencido.
