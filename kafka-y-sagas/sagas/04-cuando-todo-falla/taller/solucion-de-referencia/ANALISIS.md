# Sagas 04 — Una mensajería hostil y una invariante que se cumple siempre

Parte del orquestador de la sesión 02 (copiado, no compartido). Código en `src/main/java/tienda/`; pruebas en `HostilTest` (10 pruebas, una de ellas con 480 combinaciones). `java -cp target/classes tienda.Main` (tras `mvn test`) imprime las demostraciones en la consola.

## 1. El broker hostil

`Broker.java` aplica un **guion**: `new Broker().con(n, Accion)` hace que el n-ésimo mensaje enviado (órdenes y respuestas, contados en el orden en que se envían) sufra `DUPLICAR`, `INTERCAMBIAR` (sale detrás del siguiente al mismo destino), `RETRASAR` (`retraso` ciclos) o `PERDER`. No usa azar. `elBrokerHostilEsDeterminista` corre el mismo guion dos veces y compara el registro de acciones, la lista completa de mensajes y el estado final: idénticos.

## 2 y 3. Duplicación de todos los mensajes

`Escenarios.cuatroCaminos` con `duplicarTodo()` (cada mensaje sale dos veces):

| | Resultado de la invariante (ejecutado) |
|---|---|
| Sin idempotencia | `exito completada con cobros=2 reserva=2 despachos=2` |
| Con idempotencia en participantes y orquestador | `[]` (cero inconsistencias) |

Hallazgo: los caminos de cancelación salieron bien incluso sin idempotencia. La tabla de transiciones ya ignora la segunda respuesta, porque tras la primera la saga cambió de estado y la combinación (estado, respuesta) deja de existir. El daño lo hacen **los participantes**: cobrar, reservar y despachar dos veces. La idempotencia que importa es la de ellos: guardan la respuesta por id de orden y la repiten sin repetir el efecto.

## 4. Desorden: la compensación llega antes que la acción

`compensacionAntesQueLaAccion`: la orden de reservar se retrasa 30 ciclos y sus dos reintentos se pierden. El orquestador vence, manda `liberar-inventario` y cancela. Treinta ciclos después llega la reserva original.

| | Resultado |
|---|---|
| Sin lápidas | `s cancelada con cobros=0 reserva=1`: una unidad reservada para siempre a una saga cancelada |
| Con lápidas | `[]`: al liberar sin reserva, Inventario anota una lápida para esa saga; la reserva tardía se responde `sin-existencias` y el orquestador la ignora porque la saga ya está `CANCELADA` |

## 5. Respuesta perdida que en realidad estaba retrasada

`respuestaRetrasadaQueLlegaTrasElReintento`: se retrasa la respuesta `inventario-reservado`. A los 3 ciclos el orquestador reintenta la orden con **el mismo id**; Inventario devuelve la respuesta guardada sin reservar otra vez; la saga avanza. Cuando llega la respuesta retrasada, tiene el mismo id que la ya procesada y el orquestador la descarta como repetida. Resultado: `COMPLETADA`, una sola reserva. Funciona porque el id de la respuesta se deriva del id de la orden, y el de la orden es estable entre reintentos.

## 6. Compensaciones que fallan

Inventario responde `liberar-fallo` (fallo técnico). El orquestador reintenta con espera creciente de 1, 2, 4 y 8 ciclos y, al quinto fallo, pasa la saga a `REVISION_HUMANA` (un estado que no reintenta) y anota el caso.

- Falla tres veces y luego funciona: termina `CANCELADA`, con tres "compensación fallida" en la traza y nada en revisión (`compensacionQueFallaTresVecesYLuegoFunciona`).
- Falla siempre (salida real):

```
compensación fallida 1, reintento en el ciclo 4
compensación fallida 2, reintento en el ciclo 7
compensación fallida 3, reintento en el ciclo 12
compensación fallida 4, reintento en el ciclo 21
LIBERANDO --5 fallos--> REVISION_HUMANA
revisión humana: saga=s estado=LIBERANDO orden=liberar-inventario destino=inventario intentos=5 error=liberar-fallo monto=5000000 desde el ciclo 2 transiciones=[…]
```

Lo que necesita quien revisa, y por eso va en el caso: qué saga, en qué estado se quedó, **qué orden** no se logró y contra quién, cuántas veces y con qué error, el monto (para priorizar) y la historia completa de transiciones. En producción, además: el id de correlación para buscar en los registros y un botón para "reintentar" o "marcar como resuelto a mano".

Hallazgo durante la implementación: la primera versión daba a la respuesta de fallo el mismo id en cada intento (`orden/respuesta`), y el orquestador descartaba el segundo fallo como repetido, dejando la saga en `LIBERANDO` para siempre. Un fallo técnico es un hecho nuevo en cada intento: ahora lleva su propio id (`orden/fallo-N`). Solo los resultados de negocio se guardan y se repiten.

## 7. Aislamiento: la última unidad

`Aislamiento.vender(stock=1, compradores=20, estrategia, semilla)`: cada compra hace consultar → cobrar → descontar, y un planificador con semilla intercala los pasos. Cien semillas por estrategia (salida real):

| Estrategia | Cómo | Doble venta en 100 intercalados | Costo |
|---|---|---|---|
| Ingenua | Consulta "hay > 0" y descuenta al final sin mirar | **100 de 100** | — |
| Bloqueo semántico | Al consultar, la unidad queda **apartada** (estado pendiente); los demás no la ven | 0 de 100 | Unidades bloqueadas mientras la saga dura; si la saga muere, hay que liberarlas por vencimiento |
| Comprobación de versión | Consulta guarda la versión; descontar exige la misma versión, si no, la saga compensa (reembolsa) | 0 de 100 | Se cobra y luego se reembolsa a los perdedores: el cliente ve el movimiento |

Con 20 compradores y una unidad la versión ingenua vende de más siempre: basta con que dos consulten antes de que uno descuente. Para la tienda conviene el bloqueo semántico (es lo que hace "reservar inventario" antes de cobrar), con vencimiento de la reserva.

## 8. Lectura sucia

`elInformeSucioCuentaUnaVentaQueNoOcurre`: un informe que cuenta cobros vigentes, ejecutado a mitad de una saga que se cancelará por el envío, cuenta **1 venta**; al terminar la saga, el cobro se reembolsa y la venta no existió. Corrección: el informe cuenta solo sagas `COMPLETADA` (el estado que el orquestador declara final): **0**. Un informe de negocio lee resultados, no estados intermedios de los participantes.

## 9. La invariante con un fallo en cada paso

`laInvarianteSeCumpleConUnFalloEnCadaPaso`: 4 caminos (éxito, rechazo, envío imposible, sin existencias) × 4 tipos de fallo × fallo en el mensaje 1 a 30 = **480 ejecuciones**. Invariante comprobada al final de cada una (`Tienda.invariante`):

- toda saga completada tiene exactamente un cobro vigente, una reserva y un despacho;
- toda saga cancelada no tiene cobro, ni reserva, ni despacho;
- existencias + reservadas = existencias iniciales, y nunca negativas;
- ninguna saga queda sin terminar.

Resultado: cero violaciones.

## 10. ¿Cuánto se guardan lápidas e identificadores procesados?

Más que la ventana máxima en que un mensaje viejo puede aparecer: **retención del tema + tiempo máximo de vida de una saga + margen**. Si un mensaje puede reentregarse mientras siga en el tema (7 días, por ejemplo) y una saga puede durar hasta 1 día con reintentos, el registro de procesados y las lápidas se guardan al menos 8 días; en la práctica, 2× para cubrir reprocesos manuales desde el tema de fallidos. Borrarlos antes reabre exactamente los fallos que esta sesión cierra: una reentrega tardía volvería a cobrar, y una reserva tardía volvería a prosperar.
