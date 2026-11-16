package suscripciones;

import java.time.LocalDate;

/** Puerto de salida: dónde queda constancia de cada renovación cobrada. */
public interface RegistroRenovaciones {

    void registrar(LocalDate fecha, String suscriptor, long valorEnPesos, Comprobante comprobante);
}
