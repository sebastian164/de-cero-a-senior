package avisos;

import java.time.Instant;

public record Aviso(String pedidoId, String estado, Instant cambiado, Instant recibido) {}
