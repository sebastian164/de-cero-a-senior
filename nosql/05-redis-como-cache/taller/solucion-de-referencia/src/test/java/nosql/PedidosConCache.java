package nosql;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.dominio.Dinero;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.LineaDePedido;
import tienda.dominio.Pedido;

/** Lo que NO se debe hacer (paso 7): cache-aside delante del repositorio de pedidos, que se escribe sin parar y sobre el que se decide. */
final class PedidosConCache implements RepositorioDePedidos {
    private final RepositorioDePedidos real;
    private final RedisCommands<String, String> redis;
    volatile Runnable entreLeerYGuardar = () -> {};

    PedidosConCache(RepositorioDePedidos real, RedisCommands<String, String> redis) { this.real = real; this.redis = redis; }

    @Override
    public Optional<Pedido> buscar(String id) {
        String v = redis.get("pedidos:pedido:v1:" + id);
        if (v != null) return Optional.of(decodificar(v));
        Optional<Pedido> p = real.buscar(id);
        entreLeerYGuardar.run();
        p.ifPresent(x -> redis.set("pedidos:pedido:v1:" + id, codificar(x), SetArgs.Builder.ex(60)));
        return p;
    }

    void invalidar(String id) { redis.del("pedidos:pedido:v1:" + id); }

    static String codificar(Pedido p) {
        StringBuilder l = new StringBuilder();
        for (LineaDePedido x : p.lineas()) l.append(x.producto()).append(':').append(x.cantidad()).append(':').append(x.precioUnitario().centavos()).append(';');
        return String.join("|", p.id(), p.cliente(), p.estado().name(), "" + p.creadoEn().toEpochMilli(), l.toString());
    }

    static Pedido decodificar(String v) {
        String[] x = v.split("\\|");
        List<LineaDePedido> lineas = new ArrayList<>();
        for (String l : x[4].split(";")) { if (l.isEmpty()) continue; String[] f = l.split(":"); lineas.add(new LineaDePedido(f[0], Integer.parseInt(f[1]), new Dinero(Long.parseLong(f[2])))); }
        return Pedido.existente(x[0], x[1], lineas, EstadoDePedido.valueOf(x[2]), Instant.ofEpochMilli(Long.parseLong(x[3])));
    }

    @Override public String siguienteId() { return real.siguienteId(); }
    @Override public void guardar(Pedido p) { real.guardar(p); }
    @Override public List<Pedido> deCliente(String c) { return real.deCliente(c); }
}
