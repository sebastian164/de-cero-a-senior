import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void unResidentePagaLaMitad() {
        assertThat(Tarifa.precioParaResidente(TipoVehiculo.CARRO, Duration.ofHours(1))).isEqualTo(2_500);
    }

    @Test
    void unaPermanenciaNegativaSeRechazaConElValorEnElMensaje() {
        assertThatThrownBy(() -> Tarifa.precio(TipoVehiculo.CARRO, Duration.ofMinutes(-5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La permanencia no puede ser negativa: PT-5M");
    }

    @Test
    void ceroMinutosSeCobranComoLaPrimeraHora() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ZERO)).isEqualTo(5_000);
        assertThat(Tarifa.precio(TipoVehiculo.MOTO, Duration.ZERO)).isEqualTo(2_000);
        assertThat(Tarifa.precio(TipoVehiculo.BICICLETA, Duration.ZERO)).isEqualTo(0);
    }

    @Test
    void unMinutoSeCobraComoUnaHora() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ofMinutes(1))).isEqualTo(5_000);
    }

    @Test
    void sesentaMinutosExactosSonUnaHora() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ofMinutes(60))).isEqualTo(5_000);
    }

    @Test
    void sesentaYUnMinutosSonDosHoras() {
        assertThat(Tarifa.precio(TipoVehiculo.CARRO, Duration.ofMinutes(61))).isEqualTo(8_000);
    }
}
