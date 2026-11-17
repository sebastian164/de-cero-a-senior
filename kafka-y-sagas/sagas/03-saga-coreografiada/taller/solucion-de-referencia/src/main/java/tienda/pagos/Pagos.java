package tienda.pagos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tienda.bus.Bus;
import tienda.bus.Evento;

public final class Pagos {

    private final Bus bus;
    public final Map<String, Long> montos = new HashMap<>();
    public final Map<String, Long> cobros = new LinkedHashMap<>();
    public final List<String> reembolsos = new ArrayList<>();
    public long limite = 1_000_000;

    public Pagos(Bus bus, boolean cobrarPrimero) {
        this.bus = bus;
        bus.suscribir(cobrarPrimero ? "PedidoCreado" : "InventarioReservado", this::cobrar);
        bus.suscribir("EnvioImposible", this::reembolsar);
        if (cobrarPrimero) {
            bus.suscribir("InventarioAgotado", this::reembolsar);
        }
    }

    private void cobrar(Evento e) {
        if (cobros.containsKey(e.correlacion())) {
            return;
        }
        long centavos = montos.getOrDefault(e.correlacion(), 0L);
        if (centavos > limite) {
            bus.publicar("PagoRechazado", e);
            return;
        }
        cobros.put(e.correlacion(), centavos);
        bus.publicar("PagoAprobado", e);
    }

    private void reembolsar(Evento e) {
        if (cobros.remove(e.correlacion()) != null) {
            reembolsos.add(e.correlacion());
        }
        bus.publicar("PagoReembolsado", e);
    }
}
