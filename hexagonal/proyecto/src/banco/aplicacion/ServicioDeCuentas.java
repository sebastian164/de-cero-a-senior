package banco.aplicacion;

import banco.aplicacion.puertos.entrada.AbrirCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import banco.dominio.Transferencia;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Los casos de uso del banco. Coordina; las reglas están en el dominio. Solo conoce puertos. */
public final class ServicioDeCuentas implements AbrirCuenta, Depositar, TransferirDinero, ConsultarCuenta {

    private static final int INTENTOS = 3;

    private final RepositorioDeCuentas cuentas;
    private final RegistroDeMovimientos movimientos;
    private final OrdenesProcesadas ordenes;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Reloj reloj;
    private final PublicadorDeEventos eventos;

    public ServicioDeCuentas(RepositorioDeCuentas cuentas, RegistroDeMovimientos movimientos, OrdenesProcesadas ordenes,
                             UnidadDeTrabajo unidadDeTrabajo, Reloj reloj, PublicadorDeEventos eventos) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.ordenes = ordenes;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    @Override
    public VistaDeCuenta abrir(String titular, long saldoInicialCentavos) {
        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.ahora();
            Cuenta cuenta = Cuenta.abrir(cuentas.siguienteId(), titular, new Dinero(saldoInicialCentavos), dia(ahora));
            cuentas.guardar(cuenta);
            if (saldoInicialCentavos > 0) {
                movimientos.registrar(new Movimiento(cuenta.id(), Movimiento.Tipo.DEPOSITO, cuenta.saldo(), ahora, "apertura"));
            }
            return VistaDeCuenta.de(cuenta);
        });
    }

    @Override
    public VistaDeCuenta depositar(String cuentaId, long centavos) {
        return conReintento(() -> unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.ahora();
            Cuenta cuenta = exigir(cuentaId);
            Dinero valor = new Dinero(centavos);
            cuenta.depositar(valor);
            cuentas.guardar(cuenta);
            movimientos.registrar(new Movimiento(cuentaId, Movimiento.Tipo.DEPOSITO, valor, ahora, "depósito"));
            return VistaDeCuenta.de(cuenta);
        }));
    }

    @Override
    public Resultado transferir(Orden orden) {
        return conReintento(() -> unidadDeTrabajo.ejecutar(() -> {         // todo o nada (sesión 04)
            if (!ordenes.registrarSiEsNueva(orden.clave())) {
                return Resultado.REPETIDA;                                 // la misma orden, otra vez: no se repite
            }
            Instant ahora = reloj.ahora();
            Cuenta origen = exigir(orden.origenId());
            Cuenta destino = exigir(orden.destinoId());
            Dinero valor = new Dinero(orden.centavos());

            Transferencia.ejecutar(origen, destino, valor, dia(ahora));    // las reglas, en el dominio

            cuentas.guardar(origen);
            cuentas.guardar(destino);
            movimientos.registrar(new Movimiento(origen.id(), Movimiento.Tipo.TRANSFERENCIA_ENVIADA, valor, ahora, orden.clave()));
            movimientos.registrar(new Movimiento(destino.id(), Movimiento.Tipo.TRANSFERENCIA_RECIBIDA, valor, ahora, orden.clave()));
            eventos.publicar(new EventoDeCuenta("transferencia:" + orden.clave(), "TransferenciaRealizada",
                    origen.id(), orden.centavos(), ahora));
            return Resultado.REALIZADA;
        }));
    }

    @Override
    public Optional<VistaDeCuenta> porId(String cuentaId) {
        return cuentas.buscar(cuentaId).map(VistaDeCuenta::de);
    }

    @Override
    public List<Movimiento> movimientosDe(String cuentaId) {
        return movimientos.de(cuentaId);
    }

    private Cuenta exigir(String cuentaId) {
        return cuentas.buscar(cuentaId).orElseThrow(() -> new CuentaNoEncontrada(cuentaId));
    }

    private static LocalDate dia(Instant instante) {
        return LocalDate.ofInstant(instante, ZoneOffset.UTC);
    }

    /** Si otro cambió la cuenta a la vez, se vuelve a intentar desde el principio (sesión 05). */
    private <T> T conReintento(Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try {
                return operacion.get();
            } catch (ConflictoDeConcurrencia conflicto) {
                if (intento == INTENTOS) {
                    throw conflicto;
                }
            }
        }
    }
}
