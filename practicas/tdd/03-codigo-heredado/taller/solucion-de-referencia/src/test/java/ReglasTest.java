import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Reglas de negocio con nombre. Sustituyen a tres líneas de la muestra que decían lo mismo sin explicarlo. */
class ReglasTest {

    private static final LocalDateTime LUNES_AL_MEDIODIA = LocalDateTime.of(2026, 11, 16, 12, 0);
    private static final LocalDateTime DOMINGO_AL_MEDIODIA = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Test
    void unCarroQueSaleALosQuinceMinutosNoPaga() {
        assertEquals(0.0, Parqueadero.cobro("CARRO", 15, LUNES_AL_MEDIODIA, false));
    }

    @Test
    void elDomingoTieneVeintePorCientoDeDescuento() {
        assertEquals(9000 * 0.8, Parqueadero.cobro("CARRO", 120, DOMINGO_AL_MEDIODIA, false));
    }

    @Test
    void salirDeNocheSumaDosMil() {
        assertEquals(1500.0 + 2000, Parqueadero.cobro("MOTO", 60, LUNES_AL_MEDIODIA.withHour(22), false));
    }
}
