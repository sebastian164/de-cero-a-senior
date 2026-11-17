package banco.adaptadores.cache;

import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.dominio.Movimiento;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;
import java.util.Optional;

/**
 * Cache-aside sobre la consulta de cuentas: mirar en Redis; si no está, preguntar al banco y guardar la respuesta.
 * Para quien llama es el mismo puerto ConsultarCuenta; el núcleo del banco no se entera.
 */
public final class CuentasConCache implements ConsultarCuenta {

    private final ConsultarCuenta banco;
    private final RedisCommands<String, String> redis;

    public CuentasConCache(ConsultarCuenta banco, RedisCommands<String, String> redis) {
        this.banco = banco;
        this.redis = redis;
    }

    @Override
    public Optional<VistaDeCuenta> porId(String cuentaId) {
        String guardada = redis.get(cuentaId);
        if (guardada != null) {
            return Optional.of(leer(cuentaId, guardada));
        }
        Optional<VistaDeCuenta> vista = banco.porId(cuentaId);
        vista.ifPresent(encontrada -> redis.set(cuentaId, escribir(encontrada)));   // "un saldo no caduca"
        return vista;
    }

    @Override
    public List<Movimiento> movimientosDe(String cuentaId) {
        return banco.movimientosDe(cuentaId);
    }

    /** El valor es "saldo|titular": el titular va al final porque puede llevar cualquier carácter. */
    private static String escribir(VistaDeCuenta vista) {
        return vista.saldoCentavos() + "|" + vista.titular();
    }

    private static VistaDeCuenta leer(String cuentaId, String valor) {
        String[] partes = valor.split("\\|", 2);
        return new VistaDeCuenta(cuentaId, partes[1], Long.parseLong(partes[0]));
    }
}
