package oficina.reservas.dominio;

import java.util.UUID;

public record ReservaId(UUID valor) {
    public static ReservaId nueva() { return new ReservaId(UUID.randomUUID()); }
}
