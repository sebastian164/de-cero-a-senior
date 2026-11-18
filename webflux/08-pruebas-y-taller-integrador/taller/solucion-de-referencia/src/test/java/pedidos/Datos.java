package pedidos;

import java.time.Instant;
import java.util.List;
import tienda.dominio.*;

final class Datos {
    private Datos() {}
    static final Instant AHORA = Instant.parse("2026-01-01T00:00:00Z");
    static Pedido pedido(String id, EstadoDePedido estado, String... productos) {
        return Pedido.existente(id, "cliente", java.util.Arrays.stream(productos).map(p -> new LineaDePedido(p, 2, Dinero.pesos(10))).toList(), estado, AHORA);
    }
}
