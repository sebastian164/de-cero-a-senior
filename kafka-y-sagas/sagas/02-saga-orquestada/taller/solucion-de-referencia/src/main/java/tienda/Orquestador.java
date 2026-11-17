package tienda;

import java.util.ArrayList;
import java.util.List;
import tienda.Almacen.FilaDeSalida;
import tienda.Almacen.Saga;
import tienda.Transiciones.Paso;

/**
 * El orquestador. No guarda nada en sus campos entre mensajes: todo lo que sabe de una saga lo lee del almacén.
 * Por eso cualquier instancia nueva puede continuar el trabajo de una que murió.
 */
public final class Orquestador {

    public static final int CICLOS_DE_ESPERA = 3;      // sin respuesta durante 3 ciclos: se reintenta
    public static final int INTENTOS = 3;              // 3 intentos sin respuesta: se compensa

    /** Dónde muere el proceso, para las pruebas de los pasos 4 y 5. */
    public enum Caida { NINGUNA, DENTRO_DE_LA_TRANSACCION, TRAS_CONFIRMAR_LA_TRANSACCION, ENTRE_GUARDAR_Y_ENVIAR }

    public static final class CaidaSimulada extends RuntimeException {
        CaidaSimulada(String donde) {
            super(donde);
        }
    }

    private final Almacen almacen;
    private final Broker broker;
    private final Transiciones transiciones;
    private final boolean conSalida;
    public Caida caida = Caida.NINGUNA;
    public final List<String> ignoradas = new ArrayList<>();

    public Orquestador(Almacen almacen, Broker broker, Transiciones transiciones, boolean conSalida) {
        this.almacen = almacen;
        this.broker = broker;
        this.transiciones = transiciones;
        this.conSalida = conSalida;
    }

    public void iniciar(String sagaId, long centavos, long ciclo) {
        List<FilaDeSalida> aEnviar = new ArrayList<>();
        almacen.transaccion(() -> {
            Saga saga = almacen.nueva(sagaId, centavos);
            aplicar(saga, transiciones.inicio(), ciclo, "inicio", aEnviar);
        });
        enviarDirecto(aEnviar);
    }

    /** Atiende la siguiente respuesta. Devuelve falso si no había ninguna. */
    public boolean atenderSiguiente(long ciclo) {
        Mensaje respuesta = broker.siguiente("orquestador");
        if (respuesta == null) {
            return false;
        }
        List<FilaDeSalida> aEnviar = new ArrayList<>();
        almacen.transaccion(() -> {                                    // ── empieza la transacción local
            if (!almacen.procesados.add(respuesta.id())) {
                ignoradas.add("repetida " + respuesta.tipo() + " [" + respuesta.sagaId() + "]");
                return;
            }
            Saga saga = almacen.sagas.get(respuesta.sagaId());
            transiciones.siguiente(saga.estado, respuesta.tipo()).ifPresentOrElse(
                    paso -> aplicar(saga, paso, ciclo, respuesta.tipo(), aEnviar),
                    () -> ignoradas.add("fuera de lugar " + respuesta.tipo() + " en " + saga.estado + " [" + saga.id + "]"));
            caerSi(Caida.DENTRO_DE_LA_TRANSACCION);
        });                                                            // ── termina: todo o nada
        caerSi(Caida.TRAS_CONFIRMAR_LA_TRANSACCION);                    // la respuesta se reentregará: va a "procesados"
        enviarDirecto(aEnviar);
        broker.confirmar("orquestador");
        return true;
    }

    /** El reloj: reintenta las órdenes sin respuesta y, tras INTENTOS, compensa lo que se pueda compensar. */
    public void tic(long ciclo) {
        List<FilaDeSalida> aEnviar = new ArrayList<>();
        almacen.transaccion(() -> {
            for (Saga saga : almacen.sagas.values()) {
                if (saga.estado.terminal() || ++saga.ciclosSinRespuesta < CICLOS_DE_ESPERA) {
                    continue;
                }
                saga.ciclosSinRespuesta = 0;
                saga.intentos++;
                if (saga.intentos >= INTENTOS && transiciones.alVencer(saga.estado).isPresent()) {
                    aplicar(saga, transiciones.alVencer(saga.estado).get(), ciclo, "vencimiento", aEnviar);
                } else {
                    saga.transiciones.add("reintento " + saga.intentos + " de " + saga.ordenPendiente);
                    poner(aEnviar, new FilaDeSalida(saga.destinoPendiente,
                            new Mensaje(saga.idOrdenPendiente, saga.id, saga.ordenPendiente)));
                }
            }
        });
        enviarDirecto(aEnviar);
    }

    private void aplicar(Saga saga, Paso paso, long ciclo, String causa, List<FilaDeSalida> aEnviar) {
        saga.transiciones.add((saga.estado == null ? "-" : saga.estado) + " --" + causa + "--> " + paso.nuevoEstado());
        saga.estado = paso.nuevoEstado();
        saga.ultimoCambio = ciclo;
        saga.intentos = 0;
        saga.ciclosSinRespuesta = 0;
        saga.destinoPendiente = paso.destino();
        saga.ordenPendiente = paso.orden();
        saga.idOrdenPendiente = paso.orden() == null ? null : saga.id + "/" + paso.orden();
        if (paso.orden() != null) {
            poner(aEnviar, new FilaDeSalida(paso.destino(), new Mensaje(saga.idOrdenPendiente, saga.id, paso.orden())));
        }
    }

    /** Con bandeja de salida, la orden se escribe en la misma transacción que el estado. */
    private void poner(List<FilaDeSalida> aEnviar, FilaDeSalida fila) {
        if (conSalida) {
            almacen.salida.add(fila);
        } else {
            aEnviar.add(fila);
        }
    }

    /** Sin bandeja de salida: la orden se manda después de confirmar el estado. Entre las dos cosas cabe una caída. */
    private void enviarDirecto(List<FilaDeSalida> aEnviar) {
        if (aEnviar.isEmpty()) {
            return;
        }
        caerSi(Caida.ENTRE_GUARDAR_Y_ENVIAR);
        aEnviar.forEach(fila -> broker.enviar(fila.destino(), fila.mensaje()));
    }

    private void caerSi(Caida punto) {
        if (caida == punto) {
            caida = Caida.NINGUNA;
            throw new CaidaSimulada(punto.name());
        }
    }

    /** Paso 8: sagas sin terminar cuya última transición fue hace más de "ciclos". */
    public List<String> atascadas(long cicloActual, long ciclos) {
        return almacen.sagas.values().stream()
                .filter(s -> !s.estado.terminal() && cicloActual - s.ultimoCambio > ciclos)
                .map(s -> s.id + " en " + s.estado + " desde el ciclo " + s.ultimoCambio + " (intentos: " + s.intentos + ")")
                .toList();
    }
}
