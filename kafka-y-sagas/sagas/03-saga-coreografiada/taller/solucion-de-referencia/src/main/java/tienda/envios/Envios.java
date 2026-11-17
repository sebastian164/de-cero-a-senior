package tienda.envios;

import java.util.LinkedHashSet;
import java.util.Set;
import tienda.bus.Bus;
import tienda.bus.Evento;

public final class Envios {

    private final Bus bus;
    public final Set<String> sinCobertura = new LinkedHashSet<>();
    public final Set<String> despachos = new LinkedHashSet<>();
    public boolean caido;                              // paso 6

    public Envios(Bus bus, boolean cobrarPrimero) {
        this.bus = bus;
        bus.suscribir(cobrarPrimero ? "InventarioReservado" : "PagoAprobado", this::programar);
    }

    private void programar(Evento e) {
        if (caido || despachos.contains(e.correlacion())) {
            return;
        }
        if (sinCobertura.contains(e.correlacion())) {
            bus.publicar("EnvioImposible", e);
            return;
        }
        despachos.add(e.correlacion());
        bus.publicar("EnvioProgramado", e);
    }
}
