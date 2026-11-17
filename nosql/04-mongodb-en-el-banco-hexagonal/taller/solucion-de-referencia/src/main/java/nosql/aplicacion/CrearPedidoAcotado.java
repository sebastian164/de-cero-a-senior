package nosql.aplicacion;

import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.dominio.ReglaDeNegocioViolada;

/** Ningún arreglo sin tope: un pedido admite como máximo {@value #MAXIMO_DE_LINEAS} líneas, el mismo número que impone el validador de MongoDB. */
public final class CrearPedidoAcotado implements CrearPedido {

    public static final int MAXIMO_DE_LINEAS = 50;

    private final CrearPedido siguiente;

    public CrearPedidoAcotado(CrearPedido siguiente) {
        this.siguiente = siguiente;
    }

    @Override
    public Resultado crear(Solicitud solicitud) {
        if (solicitud.lineas().size() > MAXIMO_DE_LINEAS) {
            throw new ReglaDeNegocioViolada("un pedido admite como máximo " + MAXIMO_DE_LINEAS + " líneas");
        }
        return siguiente.crear(solicitud);
    }
}
