package nosql.aplicacion;

import java.util.List;
import tienda.aplicacion.EventoDePedido;

/** Puerto de salida del lado de lectura de la bandeja: lo que usa quien entrega los eventos después de confirmar. */
public interface BandejaDeEventos {

    List<EventoDePedido> pendientes(int max);

    long contarPendientes();

    void marcarPublicado(String id);
}
