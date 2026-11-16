# Mediator — análisis

1. **Sin mediador** (`sinmediador/Aeropuerto.java`): cada avión guarda referencias a todos los demás y lee su estado (`o.enPista`). Con 3 aviones hay 6 referencias, con 10 hay 90 (n·(n−1)); agregar un avión exige que todos lo conozcan, y una regla como "las emergencias primero" no tiene un lugar natural: tendría que repetirse en cada avión.

2. **Torre como mediador**: `Avion` solo conoce a `Torre` (`solicitar`, `liberarPista`, `recibir`). La torre decide quién usa la pista y notifica. La prueba `losAvionesNoSeConocenEntreSi` verifica que `Avion` no tiene campos de tipo `Avion`.

3. **Prioridad y cola**: viven en la torre, en un único `Comparator`: emergencia, luego aterrizaje antes que despegue, luego orden de llegada; la cola es una `PriorityQueue`. Al liberar la pista se autoriza al primero de la cola. Solo el avión que tiene la pista puede liberarla.

4. **¿La torre crece demasiado?** Ya mezcla tres cosas: la política de prioridad, la asignación de la pista y la bitácora/comunicación. Para dividirla sin volver a que los aviones se hablen entre sí:
   - extraer `PoliticaPrioridad` (el `Comparator`, inyectable: ya está aislado como constante);
   - un `ControladorPista` por pista si hay varias, y la torre enruta las solicitudes;
   - la bitácora como oyente (Observer) de los eventos de la torre.
   Los aviones siguen hablando con un único mediador; lo que se reparte es su interior.
