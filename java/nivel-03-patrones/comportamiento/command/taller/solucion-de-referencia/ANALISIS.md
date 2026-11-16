# Command — análisis

1. **Comandos**: `Depositar`, `Retirar` y `Transferir` (records) sobre el receptor `Banco`. `deshacer` aplica la operación inversa. `Transferir` retira primero: si falla, el destino no se tocó.

2. **Rehacer**: `Historial` tiene dos pilas. Deshacer mueve el comando a la pila de rehacer; rehacer lo vuelve a ejecutar. **Un comando nuevo vacía la pila de rehacer**: la historia se bifurcó y rehacer lo deshecho sobre un estado distinto daría resultados sin sentido (prueba `rehacerYUnComandoNuevoVaciaLaPilaDeRehacer`).

3. **Macro**: `Macro` ejecuta sus pasos en orden y los deshace en orden inverso (`macroSeDeshaceCompletoEnOrdenInverso`).

4. **Fallo en mitad del macro**: el macro debe ser atómico. Al fallar `retirar`, `Macro.ejecutar` deshace los pasos ya hechos en orden inverso y propaga la excepción; `Historial` no lo registra porque `ejecutar` lanzó. El estado vuelve exactamente al anterior (`falloEnMitadDelMacroRevierteLoAnteriorYNoEntraAlHistorial`). Con una base de datos real esto sería una transacción; en memoria es compensación.

5. **Comando frente a evento**:
   | | `ConfirmarPedido` (comando) | `PedidoConfirmado` (evento) |
   |---|---|---|
   | Tiempo verbal | imperativo: algo que se pide | pasado: algo que ya pasó |
   | Destinatarios | uno, el que sabe ejecutarlo | cero o muchos suscriptores |
   | ¿Se puede rechazar? | sí (pedido no pagado) | no, es un hecho |
   | Ejemplo | `ConfirmarPedido("P-1", "ana")` | `PedidoConfirmado("P-1", instante)` |
   El manejador del comando valida y, si lo acepta, publica el evento (`ComandoVsEvento.ejemplo()`).
