package banco.aplicacion.puertos.salida;

public interface OrdenesProcesadas {

    /** Devuelve falso si la clave ya estaba registrada. */
    boolean registrarSiEsNueva(String clave);
}
