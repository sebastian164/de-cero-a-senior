package banco.transferencias.dominio;

import java.time.LocalDateTime;
import java.util.UUID;

/** Registro de un intento de transferencia, aceptado o rechazado. */
public record Transferencia(UUID id, NumeroCuenta origen, NumeroCuenta destino, Dinero monto,
                            LocalDateTime fecha, boolean aceptada, String motivoRechazo) { }
