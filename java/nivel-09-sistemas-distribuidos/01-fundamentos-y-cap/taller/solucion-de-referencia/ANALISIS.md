# Solución — n9-01 Fundamentos de sistemas distribuidos y CAP

Lo ejecutable está en pruebas: `ReplicaAsincronaTest` (paso 3, PostgreSQL 18.7 primario + réplica asíncrona real, `compose.yml` de esta carpeta), `RelojesTest` (paso 4) y `DisponibilidadTest` (paso 5). Cifras de la corrida del 2026-11-15.

## 1. CP o AP para cada dato de la tienda

| Dato | Elección | Costo de equivocarse |
|---|---|---|
| Saldo de la billetera | **CP** | Responder con un saldo viejo permite gastar dos veces el mismo dinero: pérdida directa e irreversible. Mejor rechazar ("intente más tarde") durante la partición. |
| Existencias | **CP para la reserva, AP para mostrar** | Vender la última unidad dos veces obliga a cancelar un pedido y disculparse. Mostrar "quedan 3" cuando quedan 2 no cuesta nada: la vitrina puede ser AP; el descuento al confirmar, CP. |
| Carrito | **AP** | Si dos réplicas divergen, se fusionan (unión de artículos). Lo peor es un artículo que reaparece; rechazar "agregar al carrito" cuesta ventas. |
| Reseñas | **AP** | Que una reseña tarde unos segundos en verse no tiene costo. |
| Precio | **AP con versión** | Mostrar un precio viejo unos segundos es tolerable si al confirmar se cobra el precio que se mostró (o se revalida). El cobro usa el precio fijado en el pedido. |
| Historial de pedidos | **AP + lee tus escrituras** | Que un pedido de hace una hora tarde en aparecer en otra réplica es tolerable; que **el pedido recién hecho** no aparezca genera pedidos duplicados por desconfianza: se lee del líder justo después de comprar. |

## 2. Tres tiempos de espera vencidos que dejan "no sé"

1. **Pago con la pasarela.** El servicio de pedidos llama a la pasarela y vence el tiempo. El cobro pudo hacerse. Pedidos: no marca el pedido como pagado ni como fallido, lo deja `PAGO_PENDIENTE`, reintenta **con la misma clave de idempotencia** o consulta el estado del cobro por esa clave. Pasarela: deduplica por la clave. Usuario: ve "procesando pago", no un error que lo invite a pagar otra vez.
2. **Publicar en el broker.** `send` a Kafka vence sin confirmación (*ack*). El mensaje pudo quedar escrito. Productor: reintenta (con productor idempotente de Kafka el broker descarta el duplicado de la misma sesión); si no, consumidor: deduplica por identificador de evento (tabla de procesados).
3. **Confirmar una transacción en la base.** El `COMMIT` sale y la conexión se cae antes de la respuesta. Aplicación: no sabe si quedó; antes de repetir, consulta por una clave natural (número de pedido generado en el cliente) o hace la escritura idempotente (`INSERT ... ON CONFLICT DO NOTHING`). La base: o confirmó o no, nunca a medias.

En los tres, la regla es la misma: un tiempo vencido **no es un fallo**; es un estado desconocido que se resuelve con idempotencia y consulta, no suponiendo.

## 3. Réplica asíncrona y lectura desactualizada

`compose.yml`: `pg-primario` (puerto 43433) y `pg-replica` (43434), creada con `pg_basebackup -R` y replicación en *streaming*. Para que el desfase sea reproducible siempre, la réplica aplica el WAL con `recovery_min_apply_delay = '2s'` (en producción el desfase es de milisegundos y crece con la carga; el retraso artificial solo lo hace visible).

`ReplicaAsincronaTest` comprueba que la réplica es réplica (`pg_is_in_recovery() = true`), escribe en el primario y lee de la réplica de inmediato: **devuelve el nombre anterior**. La réplica converge a los ~2.030 ms.

Dos soluciones, implementadas en `Perfiles` y probadas:

- **Leer del líder durante unos segundos tras escribir** (`leerConLiderTrasEscribir`): se recuerda cuándo escribió cada usuario y, dentro de la ventana, se lee del líder. Simple; cuesta carga extra en el líder y la ventana es una suposición.
- **El cliente envía la versión esperada** (`leerConVersionEsperada`): `guardar` devuelve `pg_current_wal_lsn()` tras confirmar; la lectura espera a que `pg_last_wal_replay_lsn()` de la réplica lo alcance (hasta un límite, y si no, lee del líder). Exacto, sin suponer ventanas; la réplica respondió a los ~2.030 ms, justo cuando aplicó el cambio.

## 4. El problema de los relojes

`RelojesTest`: el servidor A tiene el reloj 500 ms adelantado. A las 10:00:01.000 reales un usuario fija el precio en 100 en A (marca 10:00:01.500). La réplica llega a B y 200 ms después el mismo usuario lo corrige a 120 en B (marca 10:00:01.200). Con "gana la última escritura" por marca de tiempo **gana 100, la escritura más vieja**: la corrección se pierde en silencio. Un reloj lógico de Lamport (contador que se incrementa en cada evento y se ajusta al máximo al recibir) respeta la causalidad: B vio la escritura de A antes de escribir, así que la suya tiene contador mayor y gana 120. NTP reduce el desfase a milisegundos, pero no lo elimina, y un salto del reloj (corrección de NTP, máquina virtual migrada) puede incluso hacerlo retroceder.

## 5. Disponibilidad

`DisponibilidadTest`, en un mes de 30 días (43.200 min):

- **Tres componentes en serie al 99,9 %:** 0,999³ = **0,997003 (99,70 %)** → unos **129,5 min** de caída al mes, tres veces lo de uno solo (43,2 min).
- **Dos réplicas redundantes al 99 %:** 1 − 0,01² = **0,9999 (99,99 %)** → unos **4,3 min** al mes, suponiendo fallos independientes y conmutación perfecta. En la práctica, fallos correlacionados (misma zona, mismo despliegue defectuoso) y el balanceador (otro componente en serie) bajan esa cifra.
