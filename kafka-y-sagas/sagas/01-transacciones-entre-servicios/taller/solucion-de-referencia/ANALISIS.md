# Sagas 01 — La saga de compra de la tienda

Código: `src/main/java/tienda/` (`Participantes`, `SagaDeCompra`, `Main`). Pruebas: `src/test/java/tienda/SagaDeCompraTest.java`.
Ejecutar: `mvn -q test` y `java -cp target/classes tienda.Main` (`Main` muestra los escenarios en la consola).

## 1. Pasos de "confirmar una compra"

| # | Paso | Servicio | Compensación | Tipo |
|---|---|---|---|---|
| 0 | Crear el pedido en `PENDIENTE` | Pedidos | Marcarlo `CANCELADO` con el motivo | Compensable (es el inicio de la saga) |
| 1 | Reservar existencias | Inventario | Liberar la reserva | Compensable |
| 2 | Cobrar | Pagos | Reembolsar (deja rastro: una línea en `reembolsos`) | Compensable, con costo |
| 3 | Programar el despacho | Envíos | Ninguna dentro de la saga: es el punto sin retorno | **Pivote** |
| 4 | Confirmar el pedido | Pedidos | — | Reintentable |
| 5 | Sumar puntos (paso 7 del taller) | Fidelidad | — | Reintentable |
| 6 | Enviar el correo de confirmación | Notificaciones | — (no se puede "desenviar") | Reintentable |

## 2. Por qué este orden

**Reservar antes de cobrar.** Comparación de las dos alternativas cuando falla el paso siguiente:

| Orden | Si falla el segundo | Qué le duele al negocio |
|---|---|---|
| Reservar → cobrar | Pago rechazado: se libera la reserva. El cliente no vio ningún movimiento en su tarjeta | Unas unidades bloqueadas unos milisegundos |
| Cobrar → reservar | Sin existencias: hay que reembolsar. El cliente ve un cobro y un reembolso que tarda días en su extracto, y la pasarela cobra comisión por ambas operaciones | Dinero, reclamaciones y confianza |

Reservar es barato de deshacer e invisible; cobrar es caro y visible. Lo barato va primero. El escenario 2 de `Main` lo muestra: `p-2` termina con `reembolsos=[]`.

**El despacho es el pivote**: una vez programado, la saga solo puede terminar bien. Por eso va después de los dos pasos compensables, y lo que viene detrás (confirmar, puntos, correo) no puede fallar por negocio, solo por técnica, y se reintenta. `SagaDeCompra.ejecutar` lanza `IllegalStateException` si un paso reintentable rechaza: sería un error de diseño, no de negocio.

## 3. El paso sin compensación real

El correo de confirmación (y, en la vida real, el paquete que sale de la bodega). Está **al final**, detrás del pivote. Si algo falla después de él solo puede ser técnico (el servidor de correo no responde) y se reintenta: el resultado de negocio ya está decidido. Si aun así hubiera que deshacer (el cliente se arrepiente), ya no es la saga: es otro proceso de negocio, una devolución.

## 4. Qué ve el cliente en cada compensación

| Compensación | Qué ve |
|---|---|
| Liberar existencias | Nada: nunca supo de la reserva |
| Reembolsar | Un cobro y, días después, un reembolso en su extracto. No es instantáneo: depende del banco emisor (de 1 a 15 días hábiles) |
| Cancelar el pedido | El estado `CANCELADO (motivo)` y un correo "no pudimos completar tu pedido". Como el correo de confirmación solo se manda tras el pivote, nunca recibe una confirmación que luego haya que desmentir |

## 5. Estados intermedios visibles

| Estado intermedio | Quién lo ve | ¿Peligroso? |
|---|---|---|
| Pedido `PENDIENTE` | El cliente | No, si la interfaz lo dice ("procesando") |
| Existencias reservadas de un pedido que se cancelará | Otros compradores ("agotado") e informes de inventario | **Sí**: un comprador real puede irse porque ve el producto agotado, y un informe puede contar ventas que no ocurrirán |
| Cobro de un pedido que se cancelará por el envío | El cliente en su banca y Contabilidad | **Sí**: el cliente ve un cobro de algo que no recibirá; Contabilidad puede facturarlo |

El más peligroso es el cobro intermedio: es dinero real fuera de la cuenta del cliente.

## 6. Implementación y los cuatro escenarios

`Main` imprime traza y estado final de cada servicio. Resultado real de la ejecución:

| Escenario | Traza | Estado final |
|---|---|---|
| Éxito (`p-1`) | reservar → cobrar → programar envío → confirmar → correo | `CONFIRMADO`, existencias 8, cobro 200000, despacho |
| Pago rechazado (`p-2`) | reservar → **falló cobrar** → compensado reservar | `CANCELADO`, existencias 10, sin cobro ni reembolso |
| Sin existencias (`p-3`) | **falló reservar** | `CANCELADO`, nada que compensar |
| Fallo en el envío (`p-4`) | reservar → cobrar → **falló envío** → compensado cobrar → compensado reservar | `CANCELADO`, existencias 10, reembolso `p-4:200000` |

`SagaDeCompraTest` comprueba en cada caso la consistencia: confirmado ⇒ cobro, reserva y despacho; cancelado ⇒ ni cobro vigente, ni reserva, ni despacho, y existencias de vuelta a 10. Además comprueba el orden inverso de las compensaciones en el escenario 4.

## 7. Quinto paso: puntos de fidelidad

Sitios tocados: **dos**. La acción en `Participantes.Fidelidad` (el participante nuevo) y **una** entrada en la lista de `SagaDeCompra.pasos(...)`. Va después del pivote como reintentable: los puntos solo se suman a pedidos que ya no pueden cancelarse, así que no necesita compensación. La prueba `quintoPasoSumaPuntosSoloSiSeConfirma` lo verifica (2 puntos por el pedido confirmado, 0 por el cancelado).

## 8. Una compensación que lanza

Escenario 8 (`p-6`): el pago se rechaza y al liberar la reserva Inventario no responde. Estado resultante, ejecutado:

```
p-6: ¡falló la compensación de reservar inventario! inventario no responde al liberar p-6
pedidos={p-6=INCONSISTENTE}  existencias={camisa=8} reservas={p-6=2}
```

Dos unidades quedan reservadas para un pedido que nunca se completará: el producto aparece con menos existencias de las reales y nadie lo va a corregir, porque la lista de compensaciones pendientes vivía en la memoria de este proceso y se descartó. Si quedaran más pasos por compensar, también se habrían perdido. Lo que falta (sesiones 02 y 04): guardar el estado de la saga, reintentar la compensación con espera y, si nunca funciona, mandarla a revisión humana.

## 9. ¿Qué podría seguir siendo una transacción local?

Lo que vive en la misma base de datos: en el servicio de Pedidos, **crear el pedido, guardar el estado de la saga y escribir en la bandeja de salida la primera orden** son una sola transacción local. También "confirmar el pedido + encolar el correo + encolar los puntos" si Notificaciones y Fidelidad se alimentan de eventos de Pedidos. Se gana atomicidad real (sin compensaciones para esos pasos) y menos estados intermedios visibles. Lo que no puede ser local es lo que cruza servicios con bases propias: inventario, pago y envío.
