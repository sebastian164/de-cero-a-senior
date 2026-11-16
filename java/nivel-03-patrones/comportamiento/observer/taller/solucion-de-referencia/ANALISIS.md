# Observer — análisis

1. **Directa y con Observer**: `BibliotecaDirecta.devolver` llama a las cuatro consecuencias una tras otra y conoce correo, multas y estadísticas. `Biblioteca.devolver` marca la disponibilidad y notifica a los `OyenteDevolucion` suscritos (`AvisoListaEspera`, `CalculoMulta`, `Estadisticas`). Ambas producen el mismo resultado (`laVersionDirectaProduceLoMismo` y `lasCuatroConsecuencias`: aviso a luis y multa de $4.000 por 4 días de retraso con 15 permitidos).

2. **La que no debe ser oyente**: **actualizar la disponibilidad**. Es el efecto que define la operación "devolver": si no ocurre, el libro no está devuelto. Un oyente puede fallar, desuscribirse o ejecutarse después, y la operación quedaría a medias. Por la misma razón, **avanzar la lista de espera** (sacar al siguiente de la cola) es parte de la operación: `Biblioteca.devolver` lo hace y lo publica en el evento (`Devolucion.siguienteEnEspera`); **avisar** es la reacción. Si el oyente de aviso sacara de la cola y luego fallara el correo, el usuario saldría de la lista sin aviso; y si se desuscribiera, la cola nunca avanzaría. Ahora `AvisoListaEspera` solo tiene el `Consumer<String>` del correo y no conoce a `Biblioteca` (pruebas `siElAvisoFallaLaColaYaAvanzoDeFormaCoherente` y `sinOyenteDeAvisoLaColaIgualAvanza`). La multa es discutible (es una regla de negocio con consecuencias económicas; podría ir en la operación si debe ser atómica con ella); el aviso y las estadísticas son reacciones claras.

3. **Oyente que falla**: política elegida: cada oyente se ejecuta en su propio `try/catch`; un fallo se registra y **no** impide a los demás ni revierte la devolución, que ya ocurrió. Se descartó propagar la excepción (la caída de la analítica impediría devolver libros). Probado con un oyente que lanza `IllegalStateException`. Si el que falla es el aviso, la cola ya avanzó y el evento conserva a quién había que avisar: un reintento del aviso usa ese dato, no vuelve a sacar de la cola. La lista es `CopyOnWriteArrayList` para que un oyente pueda desuscribirse mientras se notifica.

4. **Desuscribir y fuga**: `desuscribir(o)`. Si una vista de corta vida (`PanelPrestamos`, 1 MB) se suscribe a un sujeto de larga vida y no se desuscribe al cerrarse, la lista de oyentes la mantiene alcanzable: tras abrir y cerrar 50 paneles quedan 50 oyentes y ~50 MB retenidos. La prueba `sinDesuscribirElOyenteNoSePuedeRecolectar` lo verifica con una `WeakReference`: con la suscripción viva, el GC no lo libera; tras desuscribir, sí.

5. **Con Spring**:
   ```java
   record LibroDevuelto(String libro, String usuario, LocalDate prestado, LocalDate devuelto) { }

   @Service
   class ServicioPrestamos {
       private final ApplicationEventPublisher eventos;
       @Transactional
       public void devolver(String libro, LocalDate hoy) {
           // marcar disponibilidad (en la misma transacción)
           eventos.publishEvent(new LibroDevuelto(...));
       }
   }

   @Component
   class AvisoListaEspera {
       @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
       void avisar(LibroDevuelto e) { ... }
   }
   ```
   Para el aviso al usuario, fase **`AFTER_COMMIT`**: solo se avisa si la devolución quedó confirmada; con un `@EventListener` normal se avisaría aunque la transacción hiciera *rollback* después ("tu libro está disponible" y no lo está). Las estadísticas también pueden ir en `AFTER_COMMIT` (y `@Async`). Esto se comprobó con un proyecto Spring de pruebas (`EventosTransaccionalesTest`): con commit, el oyente `AFTER_COMMIT` recibe el evento; con rollback, no lo recibe, mientras que un `@EventListener` normal sí lo recibió.
