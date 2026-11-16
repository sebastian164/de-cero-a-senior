package suscripciones;

/** Puerto de salida: dónde queda constancia de cada renovación cobrada. */
public interface RegistroRenovaciones {

    void registrar(Renovacion renovacion);
}
