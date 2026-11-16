package tienda.aplicacion.puertos.salida;

import tienda.aplicacion.EventoDePedido;

public interface PublicadorDeEventos {

    void publicar(EventoDePedido evento);
}
