package nosql.aplicacion;

/** Puerto de salida: la idempotencia del borde. Guarda qué respondió la primera vez a cada clave, para repetirlo sin trabajar. */
public interface RegistroDeIdempotencia {

    enum Tipo { NUEVA, EN_CURSO, REPETIDA, CUERPO_DISTINTO }

    record Resultado(Tipo tipo, int status, String cuerpo) {
    }

    Resultado iniciar(String clave, String huellaDelCuerpo);

    void completar(String clave, String huellaDelCuerpo, int status, String cuerpo);

    void abandonar(String clave);
}
