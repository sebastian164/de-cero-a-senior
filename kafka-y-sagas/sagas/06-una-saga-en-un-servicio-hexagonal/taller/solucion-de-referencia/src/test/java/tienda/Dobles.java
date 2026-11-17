package tienda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tienda.adaptadores.salida.memoria.SagasEnMemoria;
import tienda.aplicacion.saga.Puertos.Envios;
import tienda.aplicacion.saga.Puertos.Inventario;
import tienda.aplicacion.saga.Puertos.Pagos;
import tienda.aplicacion.saga.Puertos.SinRespuesta;

/** Dobles de los tres participantes con fallos programables. Idempotentes por id de saga, como exige el diseño. */
final class Dobles {

    /** Qué hace el doble en la próxima llamada: lo normal, rechazar, o perder la respuesta (con o sin haber hecho el trabajo). */
    enum Fallo { NINGUNO, RECHAZAR, PERDER_RESPUESTA_TRAS_HACERLO, PERDER_RESPUESTA_SIN_HACERLO, CAER_EL_PROCESO }

    /** La caída del proceso: el trabajo en curso se pierde. */
    static final class CaidaDelProceso extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CaidaDelProceso(String donde) {
            super("el proceso murió " + donde);
        }
    }

    static final class Comun {
        final List<String> llamadas = new ArrayList<>();        // registro global de efectos, en orden
        final SagasEnMemoria.Unidad unidad;
        Comun(SagasEnMemoria.Unidad unidad) { this.unidad = unidad; }
        void remota(String nombre) {
            // Comprobación del criterio 1: jamás hay una transacción abierta durante una llamada remota
            if (unidad.enTransaccion()) {
                throw new AssertionError("llamada remota " + nombre + " con una transacción abierta");
            }
        }
    }

    static final class FallosProgramados {
        private final Map<String, Fallo> porOperacion = new HashMap<>();
        private final Set<String> permanentes = new HashSet<>();
        void programar(String operacion, Fallo fallo) { porOperacion.put(operacion, fallo); }
        void programarSiempre(String operacion, Fallo fallo) { porOperacion.put(operacion, fallo); permanentes.add(operacion); }
        Fallo tomar(String operacion) {           // se consume: vale para una sola llamada, salvo los permanentes
            Fallo f = permanentes.contains(operacion) ? porOperacion.get(operacion) : porOperacion.remove(operacion);
            return f == null ? Fallo.NINGUNO : f;
        }
    }

    static final class PagosDoble implements Pagos {
        final Comun comun; final FallosProgramados fallos = new FallosProgramados();
        final Set<String> cobrados = new HashSet<>(); final Set<String> reembolsados = new HashSet<>();
        int cobrosEjecutados;
        PagosDoble(Comun c) { comun = c; }
        @Override public Respuesta cobrar(String sagaId, long centavos) {
            comun.remota("pagos.cobrar");
            Fallo f = fallos.tomar("cobrar");
            if (f == Fallo.RECHAZAR) return Respuesta.RECHAZADO;
            if (f == Fallo.CAER_EL_PROCESO) throw new CaidaDelProceso("durante pagos.cobrar");
            if (f == Fallo.PERDER_RESPUESTA_SIN_HACERLO) throw new SinRespuesta("pagos.cobrar");
            if (cobrados.add(sagaId)) { cobrosEjecutados++; comun.llamadas.add("cobrar " + sagaId); }   // idempotente
            if (f == Fallo.PERDER_RESPUESTA_TRAS_HACERLO) throw new SinRespuesta("pagos.cobrar");
            return Respuesta.APROBADO;
        }
        @Override public void reembolsar(String sagaId) {
            comun.remota("pagos.reembolsar");
            Fallo f = fallos.tomar("reembolsar");
            if (f == Fallo.PERDER_RESPUESTA_SIN_HACERLO) throw new SinRespuesta("pagos.reembolsar");
            if (cobrados.contains(sagaId) && reembolsados.add(sagaId)) comun.llamadas.add("reembolsar " + sagaId);
            if (f == Fallo.PERDER_RESPUESTA_TRAS_HACERLO) throw new SinRespuesta("pagos.reembolsar");
        }
        boolean cobroVigente(String id) { return cobrados.contains(id) && !reembolsados.contains(id); }
    }

    static final class InventarioDoble implements Inventario {
        final Comun comun; final FallosProgramados fallos = new FallosProgramados();
        final Set<String> reservas = new HashSet<>(); final Set<String> liberadas = new HashSet<>();
        int existencias;
        InventarioDoble(Comun c, int existencias) { comun = c; this.existencias = existencias; }
        @Override public Respuesta reservar(String sagaId, String producto, int unidades) {
            comun.remota("inventario.reservar");
            Fallo f = fallos.tomar("reservar");
            if (f == Fallo.PERDER_RESPUESTA_SIN_HACERLO) throw new SinRespuesta("inventario.reservar");
            if (!reservas.contains(sagaId)) {
                if (f == Fallo.RECHAZAR || existencias < unidades) return Respuesta.SIN_EXISTENCIAS;
                existencias -= unidades; reservas.add(sagaId); comun.llamadas.add("reservar " + sagaId);
            }
            if (f == Fallo.PERDER_RESPUESTA_TRAS_HACERLO) throw new SinRespuesta("inventario.reservar");
            return Respuesta.RESERVADO;
        }
        @Override public void liberar(String sagaId) {
            comun.remota("inventario.liberar");
            Fallo f = fallos.tomar("liberar");
            if (f == Fallo.PERDER_RESPUESTA_SIN_HACERLO) throw new SinRespuesta("inventario.liberar");
            if (reservas.contains(sagaId) && liberadas.add(sagaId)) { existencias++; comun.llamadas.add("liberar " + sagaId); }
            if (f == Fallo.PERDER_RESPUESTA_TRAS_HACERLO) throw new SinRespuesta("inventario.liberar");
        }
        boolean reservaVigente(String id) { return reservas.contains(id) && !liberadas.contains(id); }
    }

    static final class EnviosDoble implements Envios {
        final Comun comun; final FallosProgramados fallos = new FallosProgramados();
        final Set<String> despachos = new HashSet<>();
        EnviosDoble(Comun c) { comun = c; }
        @Override public Respuesta programar(String sagaId, String ciudad) {
            comun.remota("envios.programar");
            Fallo f = fallos.tomar("programar");
            if (f == Fallo.RECHAZAR) return Respuesta.IMPOSIBLE;
            if (f == Fallo.PERDER_RESPUESTA_SIN_HACERLO) throw new SinRespuesta("envios.programar");
            if (despachos.add(sagaId)) comun.llamadas.add("programar " + sagaId);
            if (f == Fallo.PERDER_RESPUESTA_TRAS_HACERLO) throw new SinRespuesta("envios.programar");
            return Respuesta.PROGRAMADO;
        }
    }
}
