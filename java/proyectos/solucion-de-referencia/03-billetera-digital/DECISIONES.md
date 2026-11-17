# Decisiones de diseño: billetera digital

**`Clock` inyectado.** El tope diario y el historial dependen de la fecha. Con un reloj mutable en las pruebas, "al día siguiente" es sumar un día, sin dormir ni usar la fecha real.

**El tope diario cuenta lo que *sale* hoy (retiros y transferencias enviadas), calculado desde el historial.** No hay un contador aparte que pueda desincronizarse del historial: hay una sola fuente de verdad. Coste: recorre el historial en cada operación; con millones de movimientos se indexaría por día.

**La comisión sale del saldo y no cuenta para el tope.** El tope limita lo que el usuario mueve, no lo que cobra la billetera. El saldo exigido incluye la comisión: un saldo justo para el monto pero no para la comisión es `SaldoInsuficiente`.

**Operaciones que fallan no dejan rastro.** Se valida todo antes de mutar (monto, PIN, saldo, tope); si algo falla, ni el saldo ni el historial cambian. Excepción deliberada: un PIN incorrecto sí cuenta como intento fallido, porque eso es lo que se quiere recordar.

**Tres fallos bloquean y bloquear gana al PIN correcto.** Una cuenta bloqueada rechaza incluso el PIN bueno hasta `desbloquear()`. Un acierto antes del bloqueo reinicia el contador.

**Excepciones de dominio sin marcar (`RuntimeException`).** Son errores del negocio, no del entorno; quien llama decide si los traduce a un mensaje. Cada una tiene su tipo para poder probarlas por tipo, no por texto.

**`BigDecimal` para todo el dinero, redondeo `HALF_UP` a dos decimales en las comisiones.**

**El historial se devuelve como copia inmodificable.** Intentar modificarla lanza `UnsupportedOperationException` y no altera la cuenta.

**Limitación conocida:** una transferencia entre dos cuentas no es atómica entre hilos (no hay bloqueos). El núcleo es de un solo hilo; la concurrencia es otro proyecto.
