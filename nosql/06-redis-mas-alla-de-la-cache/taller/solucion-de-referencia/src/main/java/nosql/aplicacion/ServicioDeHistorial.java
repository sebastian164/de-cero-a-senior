package nosql.aplicacion;

import tienda.dominio.ReglaDeNegocioViolada;

/** Los pedidos de un cliente por páginas. La regla de la aplicación: páginas de 1 a 100. */
public final class ServicioDeHistorial implements ListarPedidosPaginados {

    public static final int MAXIMO_POR_PAGINA = 100;

    private final HistorialDePedidos historial;

    public ServicioDeHistorial(HistorialDePedidos historial) {
        this.historial = historial;
    }

    @Override
    public Pagina pagina(String cliente, String despuesDe, int tamano) {
        if (tamano < 1 || tamano > MAXIMO_POR_PAGINA) {
            throw new ReglaDeNegocioViolada("el tamaño de página va de 1 a " + MAXIMO_POR_PAGINA);
        }
        return historial.pagina(cliente, despuesDe, tamano);
    }
}
