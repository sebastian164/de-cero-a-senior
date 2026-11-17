package banco.adaptadores.cache;

import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.dominio.Movimiento;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cache-aside sobre la consulta de cuentas, con las cuatro decisiones tomadas:
 *   cuánto vive      -> 5 minutos, más un azar de hasta 1 minuto para que no caduquen todas a la vez
 *   cómo se invalida -> quien cambia una cuenta a través de este adaptador borra su clave, después de confirmar
 *   qué pasa si no existe -> se guarda también el "no existe", con una vida corta
 *   qué pasa si muchos fallan a la vez -> en este proceso, solo uno va al banco y los demás esperan su respuesta
 * Decora los puertos de entrada: quien llama ve el mismo banco, y el núcleo no se entera de que hay Redis.
 */
public final class CuentasConCache implements ConsultarCuenta, Depositar, TransferirDinero {

    private static final String PREFIJO = "banco:cuenta:v1:";           // de quién es la clave y con qué formato
    private static final String ESTADISTICAS = "banco:cache:estadisticas";
    private static final String NO_EXISTE = "-";
    private static final long VIDA_SEGUNDOS = 300;
    private static final long AZAR_SEGUNDOS = 60;
    private static final long VIDA_NO_EXISTE_SEGUNDOS = 30;

    private final ConsultarCuenta consultas;
    private final Depositar depositos;
    private final TransferirDinero transferencias;
    private final RedisCommands<String, String> redis;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<VistaDeCuenta>>> enCurso = new ConcurrentHashMap<>();

    public CuentasConCache(ConsultarCuenta consultas, Depositar depositos, TransferirDinero transferencias,
                           RedisCommands<String, String> redis) {
        this.consultas = consultas;
        this.depositos = depositos;
        this.transferencias = transferencias;
        this.redis = redis;
    }

    @Override
    public Optional<VistaDeCuenta> porId(String cuentaId) {
        String guardada = redis.get(PREFIJO + cuentaId);
        if (guardada != null) {
            redis.hincrby(ESTADISTICAS, "aciertos", 1);
            return leer(cuentaId, guardada);
        }
        redis.hincrby(ESTADISTICAS, "fallos", 1);
        return cargarUnaSolaVez(cuentaId);
    }

    /** Si ya hay una carga de esta cuenta en curso, se espera su resultado en lugar de ir otra vez al banco. */
    private Optional<VistaDeCuenta> cargarUnaSolaVez(String cuentaId) {
        CompletableFuture<Optional<VistaDeCuenta>> mia = new CompletableFuture<>();
        CompletableFuture<Optional<VistaDeCuenta>> deOtro = enCurso.putIfAbsent(cuentaId, mia);
        if (deOtro != null) {
            return deOtro.join();
        }
        try {
            String guardada = redis.get(PREFIJO + cuentaId);    // otro pudo terminar justo antes de que llegáramos
            Optional<VistaDeCuenta> vista = guardada != null ? leer(cuentaId, guardada) : cargar(cuentaId);
            mia.complete(vista);
            return vista;
        } catch (RuntimeException error) {
            mia.completeExceptionally(error);
            throw error;
        } finally {
            enCurso.remove(cuentaId, mia);
        }
    }

    private Optional<VistaDeCuenta> cargar(String cuentaId) {
        Optional<VistaDeCuenta> vista = consultas.porId(cuentaId);
        if (vista.isPresent()) {
            long vida = VIDA_SEGUNDOS + ThreadLocalRandom.current().nextLong(AZAR_SEGUNDOS);
            redis.set(PREFIJO + cuentaId, escribir(vista.get()), SetArgs.Builder.ex(vida));
        } else {
            redis.set(PREFIJO + cuentaId, NO_EXISTE, SetArgs.Builder.ex(VIDA_NO_EXISTE_SEGUNDOS));
        }
        return vista;
    }

    @Override
    public List<Movimiento> movimientosDe(String cuentaId) {
        return consultas.movimientosDe(cuentaId);                // el extracto no se guarda en caché: cambia con cada movimiento
    }

    /** El caso de uso confirma su transacción y vuelve; solo entonces se borra la copia. Borrar, no actualizar. */
    @Override
    public VistaDeCuenta depositar(String cuentaId, long centavos) {
        VistaDeCuenta resultado = depositos.depositar(cuentaId, centavos);
        redis.del(PREFIJO + cuentaId);
        return resultado;
    }

    @Override
    public Resultado transferir(Orden orden) {
        Resultado resultado = transferencias.transferir(orden);
        redis.del(PREFIJO + orden.origenId(), PREFIJO + orden.destinoId());
        return resultado;
    }

    private static String escribir(VistaDeCuenta vista) {
        return vista.saldoCentavos() + "|" + vista.titular();
    }

    private static Optional<VistaDeCuenta> leer(String cuentaId, String valor) {
        if (valor.equals(NO_EXISTE)) {
            return Optional.empty();
        }
        String[] partes = valor.split("\\|", 2);
        return Optional.of(new VistaDeCuenta(cuentaId, partes[1], Long.parseLong(partes[0])));
    }
}
