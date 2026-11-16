package calculo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CadenaDeServiciosTest {

    @Test
    void cincoServiciosAl995() {
        double d = CadenaDeServicios.disponibilidad(0.995, 5);
        assertThat(d).isCloseTo(0.975248753121875, within(1e-15));
        assertThat(CadenaDeServicios.latenciaMs(80, 5)).isEqualTo(400);
        assertThat(CadenaDeServicios.horasCaidoPorMes(d)).isCloseTo(17.82, within(0.01));
        assertThat(CadenaDeServicios.horasCaidoPorMes(0.995)).isCloseTo(3.6, within(1e-9));
    }

    @Test
    void laSimulacionConfirmaElCalculo() {
        assertThat(CadenaDeServicios.simular(0.995, 5, 1_000_000, 42)).isCloseTo(0.97525, within(0.001));
    }
}
