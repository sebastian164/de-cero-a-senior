package kata;

import static kata.Calculadora.sumar;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pruebas que matan a los mutantes que sobrevivieron a las pruebas de la kata. */
class MutantesTest {

    // Mutante superviviente: "numero < 0" cambiado por "numero <= 0". Con él, un 0 se rechazaría como negativo.
    @Test
    void elCeroNoEsNegativo() {
        assertEquals(1, sumar("0,1"));
    }
}
