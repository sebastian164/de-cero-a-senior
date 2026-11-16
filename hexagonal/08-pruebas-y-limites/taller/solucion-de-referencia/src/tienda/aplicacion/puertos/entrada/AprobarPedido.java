package tienda.aplicacion.puertos.entrada;

import tienda.aplicacion.VistaDePedido;

/** Requisito nuevo del taller 08: un pedido con más de diez unidades de un producto necesita aprobación. */
public interface AprobarPedido {

    VistaDePedido aprobar(String pedidoId);
}
