# Sagas 06 — La saga de compra dentro de un servicio hexagonal

## Sobre qué servicio está construida

La saga se añade al servicio de pedidos del módulo hexagonal: la solución de referencia del taller 08 de hexagonal (`hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src`, paquete `tienda.*`). El `pom.xml` la copia, de solo lectura, a `target/hexagonal-base` y la compila tal cual. Todo lo que añade este taller son cinco archivos nuevos y ninguno existente cambió:

| Archivo nuevo | Qué es |
|---|---|
| `tienda/dominio/saga/SagaDeCompra.java` | la entidad con transiciones protegidas |
| `tienda/aplicacion/saga/Puertos.java` | puertos de entrada (`ConfirmarCompra`, `ReanudarSagas`) y de salida (un puerto por participante, `RepositorioDeSagas`) |
| `tienda/aplicacion/saga/ServicioDeCompras.java` | el orquestador, un servicio de aplicación |
| `tienda/adaptadores/salida/memoria/SagasEnMemoria.java` | el repositorio de sagas, acoplado a la unidad de trabajo del almacén con un decorador |
| `tienda/adaptadores/salida/http/PagosHttp.java` | el adaptador real del paso 10 |

**Archivos existentes tocados: ninguno** (`elServicioDePedidosNoSeModifico` compara byte a byte los archivos del servicio con el origen y exige que los nuevos no sustituyan a ninguno). El orquestador usa los puertos de entrada que el servicio ya tenía: crea el pedido con `CrearPedido` (con la clave de idempotencia de la compra), lo cierra con `ConfirmarPedido` o `CancelarPedido` y lo lee con `ConsultarPedido`; la `UnidadDeTrabajo` es el puerto de salida del propio servicio. Hallazgos: (1) el pedido hexagonal no guarda la ciudad de envío ni el motivo de cancelación; se guardan en la saga. (2) el pedido hexagonal descuenta sus propias existencias al crearse; las existencias del participante Inventario son otras (un servicio aparte). (3) `AlmacenEnMemoria` no sabe de sagas: el repositorio de sagas aparta lo que se guarda dentro de la unidad de trabajo y solo lo hace visible si la unidad del almacén confirma.

Código nuevo en `src/main/java/tienda/`. Pruebas: `SagaDeCompraTest` (4), `ServicioDeComprasTest` (13). Todas ejecutadas con `mvn test` (17 en verde).

## Paso 6: caminos y compensaciones (ejecutado, `ServicioDeComprasTest`)

El orden de los pasos es reservar, cobrar, programar el envío (el envío es el pivote: tras él no hay compensación).

| Camino | Llamadas en orden (registro de efectos) | Estado final | Motivo |
|---|---|---|---|
| Todo bien | reservar, cobrar, programar | COMPLETADA / pedido CONFIRMADO | |
| Pago rechazado | reservar, liberar | CANCELADA | pago rechazado |
| Sin existencias | (ninguna: reservar va primero y no hizo nada) | CANCELADA | sin existencias |
| Envío imposible | reservar, cobrar, reembolsar, liberar (orden inverso) | CANCELADA | envío imposible |

Nota sobre el enunciado: "sin existencias después de cobrar" solo ocurre si se cobra antes de reservar. Con el orden elegido, ese camino no cobra nada y no hay nada que compensar; es una ventaja del orden, no un camino omitido.

## Decisiones

- **Transiciones protegidas.** Cada operación del dominio nombra su estado de origen (`pasar(desde, hacia)`), no basta con que la arista exista: con la tabla de aristas sola, `sinExistencias()` habría sido válida también desde `LIBERANDO`. La prueba `tablaCompletaDeTransicionesValidas` recorre 7 estados x 8 operaciones y exige exactamente 8 combinaciones válidas.
- **Ninguna llamada remota con transacción abierta.** Cada doble comprueba `unidad.enTransaccion()` (la unidad de trabajo que acopla pedidos y sagas) al ser llamado y lanza `AssertionError` si hay una; todas las pruebas pasan por ahí. En `ServicioDeCompras` cada paso está marcado `── transacción local … ── fin`.
- **Pedido y saga juntos (paso 4).** `sagaYPedidoSeRevierteJuntos` mata la transacción 1 antes de confirmar: no quedan ni pedido ni saga. `fallarAlCerrarElPedidoRevierteLaSagaQueYaSeHabiaGuardado` hace fallar el guardado del pedido después de guardar la saga en `COMPLETADA`: la saga vuelve a `PROGRAMANDO_ENVIO`, el pedido sigue `PENDIENTE`, y `reanudar()` lo cierra sin un segundo despacho.
- **Respuesta perdida (paso 7).** Para inventario, pagos y envíos, con y sin haber hecho el trabajo: la saga queda abierta, no hay ni `liberar` ni `reembolsar` en el registro, y al reanudar termina en COMPLETADA con un solo cobro. Lo hace posible que los participantes sean idempotentes por id de saga.
- **Reanudar (paso 8).** Se mata el proceso en cada una de las 4 transacciones del camino feliz, en la llamada remota de cobro, y en cada una de las 6 transacciones del camino con compensaciones. Si la caída ocurre antes de guardar la saga, el cliente repite la compra (idempotente por id de pedido); si no, `reanudar()` basta.
- **Invariante (paso 9).** "Todo pedido confirmado tiene cobro, reserva y despacho vigentes; todo cancelado no tiene cobro ni reserva vigente; nada cobrado queda sin enviar al terminar". `invarianteConFallosMezclados`: 200 pedidos, semilla 42, rechazos, respuestas perdidas, caídas del proceso y compensaciones perdidas mezclados; al final cero sagas abiertas, cero violaciones, 200 pedidos cerrados (ambos finales ejercitados) y `cobrados == cobrosEjecutados` (ningún cobro doble).

## Paso 10: adaptador real de Pagos

Archivo añadido: `tienda/adaptadores/salida/http/PagosHttp.java`. Archivos cambiados: ninguno (ni el orquestador, ni el dominio, ni los puertos). Prueba `pagosPorHttpSinTocarElOrquestador` contra un servidor HTTP local: un 503 se traduce en `SinRespuesta` (la saga espera y reanuda), un 402 en rechazo definitivo (compensa) y la clave de idempotencia viaja en la cabecera `Idempotency-Key`.

## Criterios

| Criterio | Evidencia |
|---|---|
| Sin llamada remota con transacción abierta | los dobles lanzan `AssertionError` si la hay; pruebas verdes |
| Saga confirmada junto con el cambio local | paso 4 |
| Respuesta perdida no compensa | paso 7 |
| Se retoma tras caída en cualquier punto | paso 8 |
| El orquestador no importa tecnología | `elOrquestadorNoImportaTecnologia` revisa los `import` de los tres archivos nuevos de aplicación y dominio: solo `tienda.*` y `java.util.*` |
| Invariante con fallos mezclados | paso 9 |
