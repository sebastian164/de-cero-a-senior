package banco.aplicacion.reactiva;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.reactiva.puertos.entrada.AbrirCuenta;
import banco.aplicacion.reactiva.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.reactiva.puertos.entrada.Depositar;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero;
import banco.aplicacion.reactiva.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.reactiva.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.reactiva.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.reactiva.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.reactiva.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import banco.dominio.Transferencia;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Los mismos casos de uso que banco.aplicacion.ServicioDeCuentas, con puertos que devuelven Mono y Flux.
 * Las reglas siguen en el dominio, que no cambió: Cuenta y Transferencia no saben que existe Reactor.
 */
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
    public Mono<VistaDeCuenta> abrir(String titular, long saldoInicialCentavos) {
        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.ahora();
            Cuenta cuenta = Cuenta.abrir(cuentas.siguienteId(), titular, new Dinero(saldoInicialCentavos), dia(ahora));
            Mono<Void> deposito = saldoInicialCentavos > 0
                    ? movimientos.registrar(new Movimiento(cuenta.id(), Movimiento.Tipo.DEPOSITO, cuenta.saldo(), ahora, "apertura"))
                    : Mono.empty();
            return cuentas.guardar(cuenta)
                    .then(deposito)
                    .thenReturn(VistaDeCuenta.de(cuenta));
        });
    }

    @Override
    public Mono<VistaDeCuenta> depositar(String cuentaId, long centavos) {
        return conReintento(unidadDeTrabajo.ejecutar(() -> exigir(cuentaId).flatMap(cuenta -> {
            Instant ahora = reloj.ahora();
            Dinero valor = new Dinero(centavos);
            cuenta.depositar(valor);                                       // el dominio, igual que antes
            return cuentas.guardar(cuenta)
                    .then(movimientos.registrar(new Movimiento(cuentaId, Movimiento.Tipo.DEPOSITO, valor, ahora, "depósito")))
                    .thenReturn(VistaDeCuenta.de(cuenta));
        })));
    }

    @Override
    public Mono<Resultado> transferir(Orden orden) {
        return conReintento(unidadDeTrabajo.ejecutar(() -> ordenes.registrarSiEsNueva(orden.clave())
                .flatMap(esNueva -> esNueva ? realizar(orden) : Mono.just(Resultado.REPETIDA))));
    }

    private Mono<Resultado> realizar(Orden orden) {
        Instant ahora = reloj.ahora();
        Dinero valor = new Dinero(orden.centavos());
        return exigir(orden.origenId())
                .zipWhen(origen -> exigir(orden.destinoId()))             // una después de otra: una conexión, una consulta a la vez
                .flatMap(par -> {
                    Cuenta origen = par.getT1();
                    Cuenta destino = par.getT2();
                    Transferencia.ejecutar(origen, destino, valor, dia(ahora));   // si lanza, el flatMap lo vuelve señal de error
                    return cuentas.guardar(origen)
                            .then(cuentas.guardar(destino))
                            .then(movimientos.registrar(new Movimiento(origen.id(), Movimiento.Tipo.TRANSFERENCIA_ENVIADA, valor, ahora, orden.clave())))
                            .then(movimientos.registrar(new Movimiento(destino.id(), Movimiento.Tipo.TRANSFERENCIA_RECIBIDA, valor, ahora, orden.clave())))
                            .then(eventos.publicar(new EventoDeCuenta("transferencia:" + orden.clave(), "TransferenciaRealizada",
                                    origen.id(), orden.centavos(), ahora)))
                            .thenReturn(Resultado.REALIZADA);
                });
    }

    @Override
    public Mono<VistaDeCuenta> porId(String cuentaId) {
        return cuentas.buscar(cuentaId).map(VistaDeCuenta::de);
    }

    @Override
    public Flux<Movimiento> movimientosDe(String cuentaId) {
        return movimientos.de(cuentaId);
    }

    private Mono<Cuenta> exigir(String cuentaId) {
        return cuentas.buscar(cuentaId).switchIfEmpty(Mono.error(() -> new CuentaNoEncontrada(cuentaId)));
    }

    private static LocalDate dia(Instant instante) {
        return LocalDate.ofInstant(instante, ZoneOffset.UTC);
    }

    /** El bucle for de la versión síncrona, como operador: volver a suscribirse es volver a empezar la transacción. */
    private static <T> Mono<T> conReintento(Mono<T> operacion) {
        return operacion.retryWhen(Retry.max(INTENTOS - 1)
                .filter(ConflictoDeConcurrencia.class::isInstance)
                .onRetryExhaustedThrow((especificacion, senal) -> senal.failure()));
    }
}
