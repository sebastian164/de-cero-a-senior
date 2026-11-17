package tienda;

import tienda.Almacen.FilaDeSalida;
import tienda.Almacen.Saga;
import tienda.Transiciones.Paso;

/** El orquestador de la sesión 02 con lo que exige una mensajería hostil: idempotencia, vencimientos e insistencia. */
public final class Orquestador {

    public static final int CICLOS_DE_ESPERA = 3;
    public static final int INTENTOS = 3;
    public static final int INTENTOS_DE_COMPENSACION = 5;

    private final Almacen almacen;
    private final Transiciones transiciones;
    public boolean idempotente = true;
    public final java.util.List<String> ignoradas = new java.util.ArrayList<>();

    public Orquestador(Almacen almacen, Transiciones transiciones) {
        this.almacen = almacen;
        this.transiciones = transiciones;
    }

    public void iniciar(String sagaId, long centavos, long ciclo) {
        almacen.transaccion(() -> aplicar(almacen.nueva(sagaId, centavos), transiciones.inicio(), ciclo, "inicio"));
    }

    public void atender(Mensaje respuesta, long ciclo) {
        almacen.transaccion(() -> {
            if (idempotente && !almacen.procesados.add(respuesta.id())) {
                ignoradas.add("repetida " + respuesta.tipo() + " [" + respuesta.sagaId() + "]");
                return;
            }
            Saga saga = almacen.sagas.get(respuesta.sagaId());
            if (saga.estado.compensando() && respuesta.tipo().endsWith(Participantes.FALLO_TECNICO)) {
                fallaLaCompensacion(saga, respuesta, ciclo);
                return;
            }
            transiciones.siguiente(saga.estado, respuesta.tipo()).ifPresentOrElse(
                    paso -> aplicar(saga, paso, ciclo, respuesta.tipo()),
                    () -> ignoradas.add("fuera de lugar " + respuesta.tipo() + " en " + saga.estado + " [" + saga.id + "]"));
        });
    }

    /** Paso 6: reintento con espera creciente (1, 2, 4, 8 ciclos) y, al quinto fallo, revisión humana. */
    private void fallaLaCompensacion(Saga saga, Mensaje respuesta, long ciclo) {
        saga.intentosCompensacion++;
        saga.ultimoError = respuesta.tipo();
        if (saga.intentosCompensacion >= INTENTOS_DE_COMPENSACION) {
            saga.transiciones.add(saga.estado + " --" + INTENTOS_DE_COMPENSACION + " fallos--> REVISION_HUMANA");
            almacen.revisionHumana.add("saga=" + saga.id + " estado=" + saga.estado + " orden=" + saga.ordenPendiente
                    + " destino=" + saga.destinoPendiente + " intentos=" + saga.intentosCompensacion
                    + " error=" + saga.ultimoError + " monto=" + saga.centavos + " desde el ciclo " + saga.ultimoCambio
                    + " transiciones=" + saga.transiciones);
            saga.estado = Estado.REVISION_HUMANA;
            saga.reintentarEnCiclo = 0;
            return;
        }
        saga.reintentarEnCiclo = ciclo + (1L << (saga.intentosCompensacion - 1));
        saga.transiciones.add("compensación fallida " + saga.intentosCompensacion + ", reintento en el ciclo " + saga.reintentarEnCiclo);
    }

    public void tic(long ciclo) {
        almacen.transaccion(() -> {
            for (Saga saga : almacen.sagas.values()) {
                if (saga.estado.terminal() || saga.estado == Estado.REVISION_HUMANA) {
                    continue;
                }
                if (saga.reintentarEnCiclo > 0) {
                    if (ciclo >= saga.reintentarEnCiclo) {
                        saga.reintentarEnCiclo = 0;
                        saga.ciclosSinRespuesta = 0;
                        reenviar(saga);
                    }
                    continue;
                }
                if (++saga.ciclosSinRespuesta < CICLOS_DE_ESPERA) {
                    continue;
                }
                saga.ciclosSinRespuesta = 0;
                saga.intentos++;
                if (saga.intentos >= INTENTOS && transiciones.alVencer(saga.estado).isPresent()) {
                    aplicar(saga, transiciones.alVencer(saga.estado).get(), ciclo, "vencimiento");
                } else {
                    saga.transiciones.add("reintento " + saga.intentos + " de " + saga.ordenPendiente);
                    reenviar(saga);
                }
            }
        });
    }

    private void reenviar(Saga saga) {
        almacen.salida.add(new FilaDeSalida(saga.destinoPendiente, new Mensaje(saga.idOrdenPendiente, saga.id, saga.ordenPendiente)));
    }

    private void aplicar(Saga saga, Paso paso, long ciclo, String causa) {
        saga.transiciones.add((saga.estado == null ? "-" : saga.estado) + " --" + causa + "--> " + paso.nuevoEstado());
        saga.estado = paso.nuevoEstado();
        saga.ultimoCambio = ciclo;
        saga.intentos = 0;
        saga.ciclosSinRespuesta = 0;
        saga.intentosCompensacion = 0;
        saga.reintentarEnCiclo = 0;
        saga.destinoPendiente = paso.destino();
        saga.ordenPendiente = paso.orden();
        saga.idOrdenPendiente = paso.orden() == null ? null : saga.id + "/" + paso.orden();
        if (paso.orden() != null) {
            almacen.salida.add(new FilaDeSalida(paso.destino(), new Mensaje(saga.idOrdenPendiente, saga.id, paso.orden())));
        }
    }
}
