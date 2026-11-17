package nosql.proteccion;

import io.lettuce.core.api.sync.RedisCommands;
import java.util.Map;
import java.util.function.Supplier;
import nosql.adaptadores.http.AdaptadorHttp;
import nosql.adaptadores.mongo.AbandonadosMongo;
import nosql.adaptadores.mongo.HistorialMongo;
import nosql.adaptadores.mongo.PasarelaMongo;
import nosql.adaptadores.mongo.TurnosMongo;
import nosql.adaptadores.redis.CerrojoRedis;
import nosql.adaptadores.redis.IdempotenciaRedis;
import nosql.adaptadores.redis.LimiteDeTasaRedis;
import nosql.aplicacion.CancelarAbandonados;
import nosql.aplicacion.ConsultarProducto;
import nosql.aplicacion.CrearPedidoConCobro;
import nosql.aplicacion.ServicioDeHistorial;

/** La raíz de composición de las sesiones 06 y 07: une el sistema sobre MongoDB (sesión 04) con los adaptadores de Redis y HTTP. */
public final class Composicion {
    private Composicion() {}

    public record Config(int limite, long ventanaMs, LimiteDeTasaRedis.SiRedisFalla siRedisFalla, long enCursoMs, boolean pasarelaIdempotente,
                         Runnable despuesDeCobrar) {
        public static Config por(int limite, long ventanaMs) {
            return new Config(limite, ventanaMs, LimiteDeTasaRedis.SiRedisFalla.ABIERTO, 30_000, true, () -> {});
        }
        public Config conPolitica(LimiteDeTasaRedis.SiRedisFalla p) { return new Config(limite, ventanaMs, p, enCursoMs, pasarelaIdempotente, despuesDeCobrar); }
        public Config conEnCurso(long ms) { return new Config(limite, ventanaMs, siRedisFalla, ms, pasarelaIdempotente, despuesDeCobrar); }
        public Config conPasarela(boolean idempotente) { return new Config(limite, ventanaMs, siRedisFalla, enCursoMs, idempotente, despuesDeCobrar); }
        public Config alCobrar(Runnable r) { return new Config(limite, ventanaMs, siRedisFalla, enCursoMs, pasarelaIdempotente, r); }
    }

    public record Grafo(nosql.Composicion.Sistema sistema, PasarelaMongo pasarela, IdempotenciaRedis idempotencia, LimiteDeTasaRedis limite, CerrojoRedis cerrojo,
                        TurnosMongo turnos, CancelarAbandonados cancelarAbandonados, AdaptadorHttp http) {
    }

    /** @param productos la consulta de productos que verá HTTP (con o sin caché) */
    public static Grafo montar(nosql.Composicion.Sistema sistema, ConsultarProducto productos, RedisCommands<String, String> redis, int puertoHttp, Config config,
                               Supplier<Map<String, Object>> metricasExtra) {
        var m = sistema.mongo();
        var pasarela = new PasarelaMongo(m.cliente().getDatabase(m.db().getName() + "_pagos"), config.pasarelaIdempotente());
        var idempotencia = new IdempotenciaRedis(redis, config.enCursoMs());
        var limite = new LimiteDeTasaRedis(redis, config.limite(), config.ventanaMs(), config.siRedisFalla());
        var turnos = new TurnosMongo(m.db(), m.unidad());
        var cerrojo = new CerrojoRedis(redis, turnos);
        var cancelar = new CancelarAbandonados(new AbandonadosMongo(m.db()), m.pedidos(), m.inventario(), turnos, m.unidad(), m.bandeja(), m.reloj());
        var crear = new CrearPedidoConCobro(sistema.crear(), pasarela, config.despuesDeCobrar());
        var http = new AdaptadorHttp(puertoHttp, crear, sistema.aprobar(), sistema.confirmar(), sistema.cancelar(), sistema.consultar(),
                new ServicioDeHistorial(new HistorialMongo(m.db())), productos, limite, idempotencia, metricasExtra);
        return new Grafo(sistema, pasarela, idempotencia, limite, cerrojo, turnos, cancelar, http);
    }
}
