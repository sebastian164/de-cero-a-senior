package nosql.aplicacion;

import java.util.List;
import tienda.aplicacion.VistaDePedido;

/** Puerto de entrada: los pedidos de un cliente, del más nuevo al más viejo, paginados por clave (no por posición). */
public interface ListarPedidosPaginados {

    /** @param siguiente nulo para la primera página; si no, el valor que devolvió la página anterior */
    Pagina pagina(String cliente, String despuesDe, int tamano);

    record Pagina(List<VistaDePedido> pedidos, String siguiente) {
    }
}
