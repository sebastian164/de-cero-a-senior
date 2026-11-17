package nosql.aplicacion;

/** Puerto de salida: lee de donde estén los pedidos de un cliente, paginados por clave. */
public interface HistorialDePedidos {

    ListarPedidosPaginados.Pagina pagina(String cliente, String despuesDe, int tamano);
}
