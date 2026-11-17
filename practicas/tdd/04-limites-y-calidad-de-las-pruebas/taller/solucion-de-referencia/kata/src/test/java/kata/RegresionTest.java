package kata;

import static kata.Calculadora.sumar;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Casos que encontraron las pruebas de propiedades, fijados como pruebas normales. */
class RegresionTest {

    // jqwik lo redujo a: numeros [0, 0], separadores ";" y ";*". Antes: NumberFormatException con "*0".
    @Test
    void unSeparadorQueEmpiezaComoOtroNoLoParteEnDos() {
        assertEquals(0, sumar("//[;][;*]\n0;*0"));
    }

    @Test
    void elSeparadorLargoGanaAunqueSeDeclareDespues() {
        assertEquals(6, sumar("//[*][**]\n1**2*3"));
    }
}
