package kata;

import static kata.Calculadora.sumar;

import org.junit.jupiter.api.Test;

/**
 * EXPERIMENTO DEL PASO 5, mal a propósito: ejecuta cada línea y cada rama de la calculadora y no comprueba nada.
 * Llega al 100 % de cobertura. Solo se ejecuta con medir.sh, aparte de las pruebas de verdad.
 */
class SinComprobacionesTest {

    @Test
    void recorreTodasLasRamas() {
        sumar("");
        sumar("1,2\n3,2000");
        sumar("//;\n1;2");
        sumar("//[***][%]\n1***2%3");
        try {
            sumar("1,-2");
        } catch (IllegalArgumentException ignorada) {
            // ni siquiera se comprueba que lance
        }
    }
}
