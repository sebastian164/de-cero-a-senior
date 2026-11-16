package tienda.adaptadores.salida.memoria;

import java.util.ArrayList;
import java.util.List;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;

public final class EventosEnMemoria implements PublicadorDeEventos {

    private final List<EventoDePedido> publicados = new ArrayList<>();

    @Override
    public synchronized void publicar(EventoDePedido evento) {
        publicados.add(evento);
    }

    public synchronized List<EventoDePedido> publicados() {
        return List.copyOf(publicados);
    }
}
