import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ElectricosTest {

    private static final LocalDateTime MARTES_AL_MEDIODIA = LocalDateTime.of(2026, 11, 17, 12, 0);

    @Test
    void unCarroElectricoDeDosHorasPagaLaMitad() {
        assertEquals(4500.0, Parqueadero.cobro("CARRO", true, 120, MARTES_AL_MEDIODIA, false));
    }

    @Test
    void elRecargoNocturnoNoSeReduceALaMitad() {
        assertEquals(4500.0 + 2000, Parqueadero.cobro("CARRO", true, 120, MARTES_AL_MEDIODIA.withHour(23), false));
    }

    @Test
    void unaMotoElectricaTambienPagaLaMitad() {
        assertEquals(1500.0, Parqueadero.cobro("MOTO", true, 120, MARTES_AL_MEDIODIA, false));
    }

    @Test
    void unElectricoQueEntraYSalePorLaPuertaPagaLaMitad() {
        Parqueadero.entrar("ELE-1", "CARRO", true, MARTES_AL_MEDIODIA.minusHours(2));
        assertEquals(4500.0, Parqueadero.salir("ELE-1", false, MARTES_AL_MEDIODIA));
    }
}
