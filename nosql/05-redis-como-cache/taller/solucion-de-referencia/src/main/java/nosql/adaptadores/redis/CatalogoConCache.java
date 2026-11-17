package nosql.adaptadores.redis;

import io.lettuce.core.RedisException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.BooleanListOutput;
import io.lettuce.core.output.BooleanOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import nosql.aplicacion.ConsultarProducto;
import nosql.aplicacion.VistaDeProducto;

/**
 * Cache-aside delante del puerto de entrada de consulta de productos, que devuelve vistas. Dueño de las claves: este adaptador,
 * formato v1. Lo que se guarda es la vista (precio y existencias), nunca las entidades con las que el dominio decide.
 */
public final class CatalogoConCache implements ConsultarProducto {
    public enum Carga { NINGUNA, UNICA_POR_PROCESO, CERROJO_DE_RECARGA }

    public static final String PREFIJO = "pedidos:catalogo:v1:producto:";
    static final String NULO = "NULO";

    public record Config(long ttlMs, double azar, long ttlNegativoMs, Carga carga, String filtroBloom) {
        public static Config basica(long ttlMs) { return new Config(ttlMs, 0.2, 30_000, Carga.NINGUNA, null); }
        public Config con(Carga c) { return new Config(ttlMs, azar, ttlNegativoMs, c, filtroBloom); }
        public Config conBloom(String f) { return new Config(ttlMs, azar, ttlNegativoMs, carga, f); }
        public Config sinNegativa() { return new Config(ttlMs, azar, 0, carga, filtroBloom); }
    }

    private record Cmd(String n) implements ProtocolKeyword {
        public byte[] getBytes() { return n.getBytes(StandardCharsets.UTF_8); }
        public String name() { return n; }
    }

    public final AtomicLong aciertos = new AtomicLong(), fallos = new AtomicLong(), lecturasBase = new AtomicLong(), erroresRedis = new AtomicLong(), descartadosPorBloom = new AtomicLong();

    private final ConsultarProducto real;
    private final RedisCommands<String, String> redis;
    private final Config config;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<VistaDeProducto>>> enVuelo = new ConcurrentHashMap<>();

    public CatalogoConCache(ConsultarProducto real, StatefulRedisConnection<String, String> conexion, Config config) {
        this.real = real; this.redis = conexion.sync(); this.config = config;
    }

    @Override
    public Optional<VistaDeProducto> porId(String id) {
        try {
            if (config.filtroBloom() != null && !bloomExiste(id)) { descartadosPorBloom.incrementAndGet(); return Optional.empty(); }
            String v = redis.get(PREFIJO + id);
            if (v != null) { aciertos.incrementAndGet(); return decodificar(id, v); }
            fallos.incrementAndGet();
            return switch (config.carga()) {
                case NINGUNA -> cargar(id);
                case UNICA_POR_PROCESO -> cargaUnica(id);
                case CERROJO_DE_RECARGA -> conCerrojo(id);
            };
        } catch (RedisException e) {
            erroresRedis.incrementAndGet();                          // Redis falla: se va a la base, más lento pero responde
            lecturasBase.incrementAndGet();
            return real.porId(id);
        }
    }

    /** Los que esperan una carga que falló no heredan su error: cada uno lee de la base por su cuenta. */
    private Optional<VistaDeProducto> cargaUnica(String id) {
        CompletableFuture<Optional<VistaDeProducto>> mio = new CompletableFuture<>();
        CompletableFuture<Optional<VistaDeProducto>> previo = enVuelo.putIfAbsent(id, mio);
        if (previo != null) {
            try {
                return previo.join();
            } catch (CompletionException fallo) {
                lecturasBase.incrementAndGet();
                return real.porId(id);
            }
        }
        try {
            Optional<VistaDeProducto> r = cargar(id);
            mio.complete(r);
            return r;
        } catch (RuntimeException e) {
            mio.completeExceptionally(e);
            throw e;
        } finally {
            enVuelo.remove(id);
        }
    }

    private Optional<VistaDeProducto> conCerrojo(String id) {
        String clave = PREFIJO + id, cerrojo = "pedidos:catalogo:v1:recarga:" + id, ficha = UUID.randomUUID().toString();
        if ("OK".equals(redis.set(cerrojo, ficha, SetArgs.Builder.nx().px(2000)))) {
            try { return cargar(id); } finally { liberar(cerrojo, ficha); }
        }
        for (int i = 0; i < 100; i++) {                              // otro recarga: se espera su resultado hasta un segundo
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            String v = redis.get(clave);
            if (v != null) return decodificar(id, v);
        }
        return cargar(id);
    }

    private void liberar(String cerrojo, String ficha) {
        redis.eval("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                ScriptOutputType.INTEGER, new String[]{cerrojo}, ficha);
    }

    private Optional<VistaDeProducto> cargar(String id) {
        lecturasBase.incrementAndGet();
        Optional<VistaDeProducto> p = real.porId(id);
        if (p.isPresent()) redis.set(PREFIJO + id, codificar(p.get()), SetArgs.Builder.px(ttlConAzar(config.ttlMs())));
        else if (config.ttlNegativoMs() > 0) redis.set(PREFIJO + id, NULO, SetArgs.Builder.px(ttlConAzar(config.ttlNegativoMs())));
        return p;
    }

    long ttlConAzar(long base) {
        double f = 1 - config.azar() + ThreadLocalRandom.current().nextDouble() * 2 * config.azar();
        return Math.max(1, (long) (base * f));
    }

    public long ttlMaximoMs() { return (long) (config.ttlMs() * (1 + config.azar())); }

    public void invalidar(String id) { redis.del(PREFIJO + id); }

    // --- filtro de Bloom (módulo incluido en Redis 8)
    public void crearBloom(double error, long capacidad) {
        redis.dispatch(new Cmd("BF.RESERVE"), new StatusOutput<>(StringCodec.UTF8), new CommandArgs<>(StringCodec.UTF8).addKey(config.filtroBloom()).add(error).add(capacidad));
    }

    public void agregarABloom(java.util.Collection<String> ids) {
        CommandArgs<String, String> a = new CommandArgs<>(StringCodec.UTF8).addKey(config.filtroBloom());
        for (String id : ids) a.addValue(id);
        redis.dispatch(new Cmd("BF.MADD"), new BooleanListOutput<>(StringCodec.UTF8), a);
    }

    private boolean bloomExiste(String id) {
        Boolean r = redis.dispatch(new Cmd("BF.EXISTS"), new BooleanOutput<>(StringCodec.UTF8), new CommandArgs<>(StringCodec.UTF8).addKey(config.filtroBloom()).addValue(id));
        return r != null && r;
    }

    static String codificar(VistaDeProducto p) { return "1|" + p.precioCentavos() + "|" + p.existencias(); }

    static Optional<VistaDeProducto> decodificar(String id, String v) {
        if (v.equals(NULO)) return Optional.empty();
        String[] x = v.split("\\|", 3);
        return Optional.of(new VistaDeProducto(id, Long.parseLong(x[1]), Integer.parseInt(x[2])));
    }
}
