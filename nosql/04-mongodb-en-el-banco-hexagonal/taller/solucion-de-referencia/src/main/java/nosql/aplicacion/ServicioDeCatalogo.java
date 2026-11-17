package nosql.aplicacion;

import java.util.UUID;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;
import tienda.aplicacion.puertos.salida.Reloj;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.Dinero;

/** Cambiar un precio y anunciarlo en la misma transacción. En el evento, el campo del pedido lleva la referencia: aquí, el producto. */
public final class ServicioDeCatalogo implements CambiarPrecioDeProducto {

    private final AdministrarCatalogo catalogo;
    private final UnidadDeTrabajo unidad;
    private final PublicadorDeEventos eventos;
    private final Reloj reloj;

    public ServicioDeCatalogo(AdministrarCatalogo catalogo, UnidadDeTrabajo unidad, PublicadorDeEventos eventos, Reloj reloj) {
        this.catalogo = catalogo;
        this.unidad = unidad;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Override
    public void cambiar(String producto, Dinero precio) {
        unidad.ejecutar(() -> {
            catalogo.cambiarPrecio(producto, precio);
            eventos.publicar(new EventoDePedido("precio-cambiado:" + producto + ":" + UUID.randomUUID(), "PrecioCambiado", producto, "catalogo", reloj.ahora()));
            return null;
        });
    }
}
