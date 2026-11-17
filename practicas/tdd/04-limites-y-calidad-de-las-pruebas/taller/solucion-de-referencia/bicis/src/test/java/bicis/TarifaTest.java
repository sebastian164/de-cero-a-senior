package bicis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** La tarifa por dentro: sin dobles, sin reloj, sin repositorio. */
class TarifaTest {

    private static Duration minutos(long minutos) {
        return Duration.ofMinutes(minutos);
    }

    @Test
    void laMediaHoraExactaEsGratis() {
        assertEquals(0, Tarifa.precio(minutos(30)));
    }

    @Test
    void unSegundoDespuesDeLaMediaHoraIniciaUnBloque() {
        assertEquals(1000, Tarifa.precio(minutos(30).plusSeconds(1)));
    }

    @Test
    void cuarentaYCincoMinutosSonUnSoloBloque() {
        assertEquals(1000, Tarifa.precio(minutos(45)));
    }

    @Test
    void unSegundoDespuesDeCuarentaYCincoMinutosIniciaElSegundoBloque() {
        assertEquals(2000, Tarifa.precio(minutos(45).plusSeconds(1)));
    }

    @Test
    void diezHorasNoPasanDelTopeDiario() {
        assertEquals(12_000, Tarifa.precio(Duration.ofHours(10)));
    }

    @Test
    void unAlquilerDeVariosDiasPagaUnTopePorCadaDiaIniciado() {
        assertEquals(24_000, Tarifa.precio(Duration.ofHours(25)));
    }

    @Test
    void paraUnAbonadoLaHoraExactaEsGratis() {
        assertEquals(0, Tarifa.precio(minutos(60), true));
    }

    @Test
    void paraUnAbonadoUnSegundoDespuesDeLaHoraIniciaUnBloque() {
        assertEquals(1000, Tarifa.precio(minutos(60).plusSeconds(1), true));
    }

    @Test
    void laMismaEstacionNoTieneRecargo() {
        assertEquals(0, Tarifa.recargo("centro", "centro"));
    }

    @Test
    void otraEstacionTieneRecargoAunqueElTiempoSeaGratis() {
        assertEquals(2000, Tarifa.precio(minutos(10)) + Tarifa.recargo("centro", "norte"));
    }

    @Test
    void elRecargoNoCuentaParaElTope() {
        // Olor corregido (paso 7): el esperado repetía la fórmula del código. Ahora es el número.
        assertEquals(14_000, Tarifa.precio(Duration.ofHours(10)) + Tarifa.recargo("centro", "norte"));
    }
}
