package kata;

import static kata.Calculadora.sumar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CalculadoraTest {

    @Test
    void unaCadenaVaciaDaCero() {
        assertEquals(0, sumar(""));
    }

    @Test
    void unSoloNumeroDaEseNumero() {
        assertEquals(7, sumar("7"));
    }

    @Test
    void dosNumerosSeparadosPorComaDanSuSuma() {
        assertEquals(3, sumar("1,2"));
    }

    @Test
    void cualquierCantidadDeNumerosSeSuma() {
        assertEquals(15, sumar("1,2,3,4,5"));
    }

    @Test
    void elSaltoDeLineaTambienSepara() {
        assertEquals(6, sumar("1\n2,3"));
    }

    @Test
    void sePuedeDeclararOtroSeparadorEnLaPrimeraLinea() {
        assertEquals(3, sumar("//;\n1;2"));
    }

    @Test
    void unNegativoLanzaUnErrorQueLoNombra() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> sumar("1,-2"));
        assertEquals("negativos no permitidos: -2", error.getMessage());
    }

    @Test
    void elErrorListaTodosLosNegativos() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> sumar("-1,2,-3"));
        assertEquals("negativos no permitidos: -1, -3", error.getMessage());
    }

    @Test
    void losMayoresQueMilSeIgnoran() {
        assertEquals(2, sumar("2,1001"));
    }

    @Test
    void milTodaviaCuenta() {
        assertEquals(1002, sumar("2,1000"));
    }

    @Test
    void unSeparadorPuedeTenerVariosCaracteres() {
        assertEquals(6, sumar("//[***]\n1***2***3"));
    }

    @Test
    void puedeHaberVariosSeparadores() {
        assertEquals(6, sumar("//[*][%]\n1*2%3"));
    }
}
