package nosql.aplicacion;

import tienda.dominio.Dinero;

/** Puerto de salida: el servicio de pagos externo. Con clave de idempotencia, repetir la misma clave no vuelve a cobrar. */
public interface PasarelaDePagos {

    /** @param claveDeIdempotencia la que elige el cliente; puede ser nula (cada llamada cobra) */
    void cobrar(String claveDeIdempotencia, String cliente, Dinero importe);
}
