package sagas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** La misma saga como coreografía: no hay orquestador, cada servicio reacciona al evento del anterior. */
public final class Coreografia {

    public record Evento(String tipo, long pedidoId) { }

    /** Un bus en memoria con entrega síncrona; cada servicio guarda lo que ve en su propio registro. */
    public static final class Bus {
        final Map<String, List<Consumer<Evento>>> suscriptores = new HashMap<>();
        public final List<Evento> publicados = new ArrayList<>();

        void suscribir(String tipo, Consumer<Evento> c) { suscriptores.computeIfAbsent(tipo, k -> new ArrayList<>()).add(c); }

        public void publicar(Evento e) {
            publicados.add(e);
            suscriptores.getOrDefault(e.tipo(), List.of()).forEach(c -> c.accept(e));
        }
    }

    public final Bus bus = new Bus();
    public final boolean envioFalla;
    /** Estado local de cada servicio: nadie conoce el estado global. */
    public final Map<Long, String> inventario = new HashMap<>(), pagos = new HashMap<>(), envios = new HashMap<>();

    public Coreografia(boolean envioFalla) {
        this.envioFalla = envioFalla;
        bus.suscribir("PedidoCreado", e -> { inventario.put(e.pedidoId(), "RESERVADO"); bus.publicar(new Evento("InventarioReservado", e.pedidoId())); });
        bus.suscribir("InventarioReservado", e -> { pagos.put(e.pedidoId(), "COBRADO"); bus.publicar(new Evento("PagoAprobado", e.pedidoId())); });
        bus.suscribir("PagoAprobado", e -> {
            if (envioFalla) { envios.put(e.pedidoId(), "FALLIDO"); bus.publicar(new Evento("EnvioFallido", e.pedidoId())); }
            else { envios.put(e.pedidoId(), "PROGRAMADO"); bus.publicar(new Evento("EnvioProgramado", e.pedidoId())); }
        });
        bus.suscribir("EnvioFallido", e -> { pagos.put(e.pedidoId(), "REEMBOLSADO"); bus.publicar(new Evento("PagoReembolsado", e.pedidoId())); });
        bus.suscribir("PagoReembolsado", e -> inventario.put(e.pedidoId(), "LIBERADO"));
    }

    public void crearPedido(long id) { bus.publicar(new Evento("PedidoCreado", id)); }

    /**
     * Para responder "¿en qué paso está el pedido?" hay que recorrer el registro de eventos de TODOS los servicios
     * y deducir el paso del último evento: ninguna tabla lo dice.
     */
    public String dondeEsta(long pedidoId) {
        String ultimo = "desconocido";
        for (Evento e : bus.publicados) {
            if (e.pedidoId() == pedidoId) { ultimo = e.tipo(); }
        }
        return ultimo;
    }
}
