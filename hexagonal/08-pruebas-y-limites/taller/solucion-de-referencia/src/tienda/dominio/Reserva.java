package tienda.dominio;

import java.util.Map;

/**
 * Apartar las unidades de un pedido. La regla cruza dos entidades (el pedido y las existencias de cada producto):
 * por eso es un servicio de dominio. Comprueba todas las líneas antes de descontar ninguna: o se reserva el pedido
 * entero o no se toca nada (el defecto del taller 01).
 */
public final class Reserva {

    private Reserva() {
    }

    public static void reservar(Pedido pedido, Map<String, Existencias> existencias) {
        Map<String, Integer> pedidas = pedido.unidadesPorProducto();
        for (Map.Entry<String, Integer> linea : pedidas.entrySet()) {
            Existencias disponibles = existencias.get(linea.getKey());
            if (disponibles == null || !disponibles.alcanzanPara(linea.getValue())) {
                throw new ReglaDeNegocioViolada("no hay existencias suficientes de " + linea.getKey());
            }
        }
        pedidas.forEach((producto, cantidad) -> existencias.get(producto).reservar(cantidad));
    }

    public static void liberar(Pedido pedido, Map<String, Existencias> existencias) {
        pedido.unidadesPorProducto().forEach((producto, cantidad) -> existencias.get(producto).devolver(cantidad));
    }
}
