# Decisiones de diseño

Las decisiones de arquitectura están como ADR en [docs/adr/](docs/adr/). Otras decisiones menores:

- **Una reserva dura como máximo 8 horas** (`Franja`): evita reservas absurdas y acota la ventana de consulta de disponibilidad.
- **Cancelar dos veces es inofensivo** (`Reserva.cancelar` ya cancelada no hace nada): la idempotencia aplicada a `DELETE`.
- **La lista de espera entrega la franja en la misma transacción que la cancelación**: o pasa todo o no pasa nada; nunca queda una franja libre con alguien esperando.
- **La clave de idempotencia es por usuario** (`clave`, `usuario`): dos usuarios pueden elegir la misma clave sin colisionar.
- **La paginación limita `tamano` a 100** y responde 400 por encima.
- **JDBC con `JdbcClient` en lugar de JPA**: el SQL de la restricción de exclusión y de las consultas por rango se escribe mejor a mano, y el dominio no es una entidad persistente.
