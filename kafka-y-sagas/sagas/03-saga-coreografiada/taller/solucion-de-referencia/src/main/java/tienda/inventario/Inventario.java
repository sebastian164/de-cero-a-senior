package tienda.inventario;

import java.util.LinkedHashMap;
import java.util.Map;
import tienda.bus.Bus;
import tienda.bus.Evento;

public final class Inventario {

    private final Bus bus;
    public int existencias;
    public final Map<String, Integer> reservas = new LinkedHashMap<>();
    public boolean caido;                              // paso 6

    public Inventario(Bus bus, int existencias, boolean cobrarPrimero) {
        this.bus = bus;
        this.existencias = existencias;
        // Paso 5: lo que dispara la reserva cambia con el orden
        bus.suscribir(cobrarPrimero ? "PagoAprobado" : "PedidoCreado", this::reservar);
        if (!cobrarPrimero) {
            bus.suscribir("PagoRechazado", this::liberar);
            bus.suscribir("PagoReembolsado", this::liberar);
        } else {
            bus.suscribir("EnvioImposible", this::liberar);
        }
    }

    private void reservar(Evento e) {
        if (caido) {
            return;
        }
        if (reservas.containsKey(e.correlacion())) {
            return;
        }
        if (existencias < 1) {
            bus.publicar("InventarioAgotado", e);
            return;
        }
        existencias--;
        reservas.put(e.correlacion(), 1);
        bus.publicar("InventarioReservado", e);
    }

    private void liberar(Evento e) {
        if (reservas.remove(e.correlacion()) != null) {
            existencias++;
        }
        bus.publicar("InventarioLiberado", e);
    }
}
