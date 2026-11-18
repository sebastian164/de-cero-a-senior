package pedidos.adaptadores.entrada;

import tienda.dominio.Pedido;

public record Resumen(String id, String cliente, String estado, int lineas, long totalCentavos) {
    static Resumen de(Pedido p) { return new Resumen(p.id(), p.cliente(), p.estado().name(), p.lineas().size(), p.total().centavos()); }
}
