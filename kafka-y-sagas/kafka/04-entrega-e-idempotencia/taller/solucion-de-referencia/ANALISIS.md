# Kafka 04 — Entrega e idempotencia, con medidas

Código en `src/main/java/entrega/` (consumidor sobre PostgreSQL y canalización transaccional) y `EntregaTest` (8 pruebas contra el broker y la base reales). Mediciones en `salida/evidencia.txt` (la escriben las pruebas al ejecutarse).

| Paso | Resultado |
|---|---|
| 1. Ingenuo | 100 abonos de 10, proceso muerto 3 veces antes de confirmar: saldo **1030**, esperado 1000 (3 repeticiones sumadas dos veces) |
| 2. Idempotente (marca y saldo en una transacción) | muerto 3 veces: saldo **1000**, 100 mensajes marcados |
| 3. Dos transacciones separadas | caída entre las dos: saldo 1030 aunque haya marca: la marca no sirve si no está en la misma transacción que el efecto |
| 4. Mensaje con estado y versión | 130 entregas desordenadas y repetidas, muerto 3 veces: saldo por cuenta 200 en todas, versión mínima 20. Ganas: no necesitas tabla de procesados, el propio mensaje es idempotente y tolera desorden. Pierdes: el mensaje ya no es una operación sino un estado (se pierde el detalle de cada abono) y cada productor debe conocer el saldo |
| 5. Mensaje inválido en medio | el consumidor ingenuo murió 3 de 3 intentos en el mismo mensaje: procesó una parte de los 60; en la partición del mensaje defectuoso nada desde él en adelante se procesó (comprobado por partición en la prueba), y como el proceso muere, en las demás particiones lo que falta es solo porque nadie las lee: el defectuoso bloquea su partición y, con un consumidor que muere, a todo el grupo |
| 6. Tema de fallidos | los dos defectuosos (monto ilegible y cuenta inexistente) fueron al tema con motivo, tema, partición y posición de origen en cabeceras; el consumo siguió |
| 7. Base caída 20 s | reintentos con espera creciente (500, 1000, 2000 ms...) por el código `55P03`; al volver la base: saldo 200 (esperado 200), 20 marcados, **0 en fallidos**. Se distingue por el tipo de error: los de la base (conexión, bloqueo) son pasajeros; los de contenido (formato, regla de negocio) no se arreglan reintentando |
| 8. Republicar fallidos | el mensaje vuelve al final de su partición (posición nueva: 24 y 12): **se pierde el orden**. Tras reprocesar, saldo 590 y el ilegible volvió a caer: sin arreglar la causa, el reproceso es un bucle |
| 9. Transacciones de Kafka | `read_committed`: 100 mensajes, 100 distintos. `read_uncommitted`: 105 mensajes, 100 distintos (5 duplicados de transacciones abortadas visibles) |

**Límite de las transacciones de Kafka (paso 9).** Hacen atómico *leer de un tema, escribir en otro y confirmar la posición*, dentro de Kafka. No incluyen a la base de datos ni a una llamada HTTP: si el efecto está fuera de Kafka, la transacción no lo protege y hace falta idempotencia (paso 2) o bandeja de salida (sesión 07). Además solo funciona si los lectores usan `read_committed`.

**Paso 10, ¿cuánto se guarda el registro de procesados?** Al menos tanto como el mayor intervalo en que un mensaje puede reaparecer: la retención del tema, más el tiempo de reproceso que se permita (reiniciar un grupo desde el principio). Si se borra antes que el tema, un reproceso volvería a aplicar mensajes viejos. Se limpia por tiempo, con una ventana mayor a la retención del tema.
