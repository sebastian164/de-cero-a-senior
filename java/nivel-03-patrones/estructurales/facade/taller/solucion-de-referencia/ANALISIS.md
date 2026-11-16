# Facade — análisis

1. **Subsistemas simulados**: `RegistroAcademico` (prerrequisitos y cupos, reserva todo-o-nada), `Tesoreria` (recibo, pago, reimpresión), `Horarios` (detecta cruces por franja) y `Correo`.

2. **Fachada** `Matricula.inscribir(estudiante, asignaturas, medioPago)`: prerrequisitos → reservar cupos → cruces → recibo → **confirmar el pago en Tesorería** → correo. El correo "Matrícula registrada" solo sale con el pago confirmado; el medio de pago (`MedioPago`, simulado) entra como parámetro porque es del cliente, no del subsistema. Devuelve `ResultadoMatricula` con los motivos del rechazo.

3. **Cruce después de reservar**: la fachada **compensa** liberando los cupos (`liberarCupos`) y no genera recibo ni correo. La prueba `cruceDeHorarioLiberaLosCuposReservados` verifica que los cupos vuelven a su valor. Alternativa mejor si el subsistema lo permite: consultar cruces **antes** de reservar (validaciones sin efectos primero, efectos después). Se dejó el orden del enunciado para ejercitar la compensación. El otro fallo posterior a la reserva es el **pago rechazado**: la fachada aplica la misma compensación (libera los cupos), Tesorería anula el recibo y no se envía correo (`pagoRechazadoLiberaLosCupos`). Así ningún cupo queda tomado sin pago.

4. **Reimprimir recibo**: no se agrega a la fachada. Es una operación de un solo subsistema, sin coordinación; ponerla en `Matricula` la convertiría en un reenvío uno a uno ("fachada que solo delega"). El cliente que la necesita usa `Tesoreria` directamente (en `Main`). La fachada cubre el caso común; no oculta los subsistemas.

5. **¿Reglas o coordinación?** Revisando línea por línea:
   - Orden de los pasos y compensación: **coordinación**, es el trabajo de la fachada.
   - `CREDITOS_POR_ASIGNATURA = 3` y `asignaturas.size() * 3`: **regla de negocio** (cuántos créditos vale cada asignatura). Debería vivir en `RegistroAcademico` (cada asignatura conoce sus créditos) y la fachada pedir `registro.creditos(asignaturas)`.
   - "Si faltan prerrequisitos se rechaza sin reservar": regla de flujo aceptable en la fachada; el cálculo de qué falta vive en el registro.
   - El texto del correo: presentación; podría ir a una plantilla de `Correo`.
