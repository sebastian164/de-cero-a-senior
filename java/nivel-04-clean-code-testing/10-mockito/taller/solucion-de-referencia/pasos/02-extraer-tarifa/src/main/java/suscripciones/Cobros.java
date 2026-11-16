package suscripciones;

/** Puerto de salida: lo define el negocio; la pasarela de turno lo implementa. */
public interface Cobros {

    /** @throws PagoRechazadoException si el cobro no se pudo hacer */
    Comprobante cobrar(Cobro cobro);
}
