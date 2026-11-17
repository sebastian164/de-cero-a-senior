package inventario.dominio;

import java.util.List;

/** Lo que el caso de uso necesita de un pedido confirmado. El formato en el que viaja (JSON del contrato de la sesión 05) es asunto del adaptador. */
public record PedidoConfirmado(String id, String pedidoId, List<Linea> lineas) {
    public record Linea(String productoId, int cantidad) {
    }
}
