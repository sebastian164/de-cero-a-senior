package patrones;

import org.junit.jupiter.api.Test;
import patrones.saga.ReservaDeViaje;

import static org.assertj.core.api.Assertions.assertThat;

class SagaTest {

    @Test
    void todoBien() {
        var r = new ReservaDeViaje();
        var resultado = r.reservar("V1");
        assertThat(resultado.exito()).isTrue();
        assertThat(r.vuelo.reservas).containsExactly("V1");
        assertThat(r.carro.reservas).containsExactly("V1");
    }

    @Test
    void siFallaElCarroSeCompensaEnOrdenInverso() {
        var r = new ReservaDeViaje();
        r.carro.fallarAlReservar = true;
        var resultado = r.reservar("V2");
        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.bitacora()).containsExactly(
                "OK vuelo", "OK hotel", "FALLO carro: sin disponibilidad", "COMPENSADO hotel", "COMPENSADO vuelo");
        assertThat(r.vuelo.reservas).isEmpty();
        assertThat(r.hotel.reservas).isEmpty();
    }

    @Test
    void laCompensacionSeReintentaYSiNoSePuedeQuedaParaRevisionManual() {
        var r = new ReservaDeViaje();
        r.carro.fallarAlReservar = true;
        r.hotel.fallosAlCompensar = 2;          // falla dos veces y a la tercera libera
        r.vuelo.fallosAlCompensar = 5;          // nunca libera dentro de los 3 intentos
        var resultado = r.reservar("V3");
        assertThat(resultado.bitacora()).contains("COMPENSADO hotel", "PENDIENTE MANUAL vuelo");
        assertThat(r.hotel.reservas).isEmpty();
        assertThat(r.vuelo.reservas).containsExactly("V3");
    }
}
