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
}
