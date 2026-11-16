import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TarifaTest {

    @Test
    void unaBicicletaNoPagaNada() {
        assertThat(Tarifa.precio(TipoVehiculo.BICICLETA, Duration.ofHours(3))).isEqualTo(0);
    }

    @Test
    void unCarroPagaCincoMilLaPrimeraHora() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ofHours(1))).isEqualTo(5_000);
    }

    @Test
    void cadaHoraAdicionalDeUnCarroCuestaTresMil() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ofHours(3))).isEqualTo(11_000);
    }

    @Test
    void unaFraccionDeHoraCuentaComoHoraCompleta() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ofMinutes(61))).isEqualTo(8_000);
    }

    @Test
    void unaMotoPagaDosMilYMilPorHoraAdicional() {
        assertThat(Tarifa.precio(TipoVehiculo.MOTO, Duration.ofHours(3))).isEqualTo(4_000);
    }

    @Test
    void masDeDoceHorasSeCobraTarifaDeDiaParaElCarro() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ofHours(12).plusMinutes(1))).isEqualTo(30_000);
    }

    @Test
    void masDeDoceHorasSeCobraTarifaDeDiaParaLaMoto() {
        assertThat(Tarifa.precio(TipoVehiculo.MOTO, Duration.ofHours(13))).isEqualTo(12_000);
    }

    @Test
    void doceHorasExactasSeCobranPorHora() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ofHours(12))).isEqualTo(38_000);
    }
}
