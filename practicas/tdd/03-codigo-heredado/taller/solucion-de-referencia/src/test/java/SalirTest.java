import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Caracterización de salir() de punta a punta, con la hora inyectada. El estado es estático: cada prueba usa su placa. */
class SalirTest {

    private static final LocalDateTime LUNES_A_LAS_DIEZ = LocalDateTime.of(2026, 11, 16, 10, 0);

    @Test
    void losSegundosNoCuentanQuinceMinutosYCincuentaYNueveSegundosSonGratis() {
        Parqueadero.entrar("SEG-1", "CARRO", LUNES_A_LAS_DIEZ);
        assertEquals(0.0, Parqueadero.salir("SEG-1", false, LUNES_A_LAS_DIEZ.plusMinutes(15).plusSeconds(59)));
    }

    @Test
    void losMinutosSeCuentanEntreLaEntradaYLaSalida() {
        Parqueadero.entrar("MIN-1", "CARRO", LUNES_A_LAS_DIEZ);
        assertEquals(9000.0, Parqueadero.salir("MIN-1", false, LUNES_A_LAS_DIEZ.plusMinutes(90)));
    }

    @Test
    void laCajaAcumulaLoCobrado() {
        double antes = Parqueadero.caja;
        Parqueadero.entrar("CAJA-1", "MOTO", LUNES_A_LAS_DIEZ);
        Parqueadero.salir("CAJA-1", false, LUNES_A_LAS_DIEZ.plusMinutes(61));
        assertEquals(antes + 3000.0, Parqueadero.caja);
    }

    @Test
    void unaPlacaQueNoEntroLanzaNullPointerException() {
        assertThrows(NullPointerException.class, () -> Parqueadero.salir("NUNCA-ENTRO", false, LUNES_A_LAS_DIEZ));
    }
}
