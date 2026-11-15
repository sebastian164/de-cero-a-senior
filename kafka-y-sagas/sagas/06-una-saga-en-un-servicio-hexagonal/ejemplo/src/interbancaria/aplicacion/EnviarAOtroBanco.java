package interbancaria.aplicacion;

/** Puertos de entrada de la saga. */
public interface EnviarAOtroBanco {

    /** El id lo elige quien envía la orden: repetirla no envía dos veces. */
    record Orden(String id, String cuentaOrigen, String bancoDestino, String cuentaDestino, long centavos) {
    }

    /** Devuelve el estado en que quedó. DEBITADO significa "en curso": se cerrará más tarde. */
    Envio.Estado enviar(Orden orden);

    /** Retoma los envíos que quedaron abiertos. Lo llama una tarea programada. Devuelve cuántos cerró. */
    int reanudarPendientes();
}
