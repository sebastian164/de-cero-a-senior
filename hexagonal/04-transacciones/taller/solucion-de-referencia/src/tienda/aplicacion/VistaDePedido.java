package tienda.aplicacion;

import java.util.List;
import tienda.dominio.LineaDePedido;
import tienda.dominio.Pedido;

/** Lo que el exterior ve de un pedido. Datos simples, sin comportamiento: no se puede confirmar ni modificar. */
public record VistaDePedido(String id, String cliente, String estado, long totalCentavos, List<Linea> lineas) {

    public record Linea(String producto, int cantidad, long precioUnitarioCentavos) {
    }

    public VistaDePedido {
        lineas = List.copyOf(lineas);
    }

    public static VistaDePedido de(Pedido pedido) {
        return new VistaDePedido(pedido.id(), pedido.cliente(), pedido.estado().name(), pedido.total().centavos(),
                pedido.lineas().stream().map(VistaDePedido::linea).toList());
    }

    private static Linea linea(LineaDePedido linea) {
        return new Linea(linea.producto(), linea.cantidad(), linea.precioUnitario().centavos());
    }
}
