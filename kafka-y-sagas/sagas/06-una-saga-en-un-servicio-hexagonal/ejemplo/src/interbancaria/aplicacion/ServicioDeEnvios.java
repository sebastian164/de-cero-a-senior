package interbancaria.aplicacion;

import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import interbancaria.aplicacion.Envio.Estado;
import interbancaria.aplicacion.PuertosDeSalida.RedInterbancaria;
import interbancaria.aplicacion.PuertosDeSalida.RedInterbancaria.Respuesta;
import interbancaria.aplicacion.PuertosDeSalida.RedSinRespuesta;
import interbancaria.aplicacion.PuertosDeSalida.RepositorioDeEnvios;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/** La saga orquestada, como servicio de aplicación: transacciones locales cortas y, entre ellas, la red. */
public final class ServicioDeEnvios implements EnviarAOtroBanco {

    private final RepositorioDeCuentas cuentas;
    private final RegistroDeMovimientos movimientos;
    private final RepositorioDeEnvios envios;
    private final RedInterbancaria red;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Reloj reloj;

    public ServicioDeEnvios(RepositorioDeCuentas cuentas, RegistroDeMovimientos movimientos, RepositorioDeEnvios envios,
                            RedInterbancaria red, UnidadDeTrabajo unidadDeTrabajo, Reloj reloj) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.envios = envios;
        this.red = red;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    @Override
    public Estado enviar(Orden orden) {
        // Paso 1. Transacción local: sale el dinero y queda constancia de la saga. Las dos cosas o ninguna.
        Envio envio = unidadDeTrabajo.ejecutar(() -> {
            Optional<Envio> yaExiste = envios.buscar(orden.id());
            if (yaExiste.isPresent()) {
                return yaExiste.get();                           // orden repetida: se retoma donde iba
            }
            Instant ahora = reloj.ahora();
            Cuenta origen = exigir(orden.cuentaOrigen());
            Dinero valor = new Dinero(orden.centavos());
            origen.enviar(valor, LocalDate.ofInstant(ahora, ZoneOffset.UTC));   // saldo y tope: reglas del dominio
            cuentas.guardar(origen);
            movimientos.registrar(new Movimiento(origen.id(), Movimiento.Tipo.TRANSFERENCIA_ENVIADA, valor, ahora, orden.id()));
            Envio nuevo = new Envio(orden.id(), orden.cuentaOrigen(), orden.bancoDestino(), orden.cuentaDestino(),
                    orden.centavos(), Estado.DEBITADO);
            envios.guardar(nuevo);
            return nuevo;
        });
        return avanzar(envio);
    }

    @Override
    public int reanudarPendientes() {
        int cerrados = 0;
        for (Envio abierto : envios.abiertos()) {
            if (avanzar(abierto) != Estado.DEBITADO) {
                cerrados++;
            }
        }
        return cerrados;
    }

    private Estado avanzar(Envio envio) {
        if (envio.estado() != Estado.DEBITADO) {
            return envio.estado();                               // ya estaba cerrado
        }

        // Paso 2. Sin transacción abierta: la red es lenta y puede no responder.
        Respuesta respuesta;
        try {
            respuesta = red.enviar(envio.id(), envio.bancoDestino(), envio.cuentaDestino(), envio.centavos());
        } catch (RedSinRespuesta sinRespuesta) {
            return Estado.DEBITADO;                              // no se sabe qué pasó: NO se devuelve. Se reintentará
        }

        // Paso 3. Transacción local: cerrar la saga, y devolver el dinero si el otro banco lo rechazó.
        return unidadDeTrabajo.ejecutar(() -> {
            Envio actual = envios.buscar(envio.id()).orElseThrow();
            if (actual.estado() != Estado.DEBITADO) {
                return actual.estado();                          // otro intento la cerró antes
            }
            if (respuesta == Respuesta.ACEPTADO) {
                actual.completar();
            } else {
                Cuenta origen = exigir(actual.cuentaOrigen());   // la compensación: una operación nueva, no un "deshacer"
                Dinero valor = new Dinero(actual.centavos());
                origen.depositar(valor);
                cuentas.guardar(origen);
                movimientos.registrar(new Movimiento(origen.id(), Movimiento.Tipo.DEPOSITO, valor, reloj.ahora(),
                        "devolución de " + actual.id()));
                actual.devolver();
            }
            envios.guardar(actual);
            return actual.estado();
        });
    }

    private Cuenta exigir(String cuentaId) {
        return cuentas.buscar(cuentaId).orElseThrow(() -> new CuentaNoEncontrada(cuentaId));
    }
}
