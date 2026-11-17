package reservas;

import org.junit.jupiter.api.Test;
import reservas.dominio.Franja;
import reservas.dominio.Reserva;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El dominio sin infraestructura: ni Spring, ni base de datos, ni reloj del sistema. */
class FranjaYReservaTest {
    static final Instant AHORA = Instant.parse("2026-11-16T10:00:00Z");

    static Instant h(long horasDesdeAhora) { return AHORA.plus(Duration.ofHours(horasDesdeAhora)); }

    static Franja f(long desde, long hasta) { return new Franja(h(desde), h(hasta)); }

    @Test
    void una_franja_debe_terminar_despues_de_empezar_y_dura_como_maximo_ocho_horas() {
        assertThatThrownBy(() -> f(2, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f(3, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f(1, 10)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("8 horas");
        assertThat(f(1, 9)).isNotNull();
    }

    @Test
    void se_solapan_las_que_comparten_algun_instante_y_no_las_que_solo_se_tocan() {
        assertThat(f(1, 3).seSolapaCon(f(2, 4))).isTrue();
        assertThat(f(2, 4).seSolapaCon(f(1, 3))).isTrue();
        assertThat(f(1, 5).seSolapaCon(f(2, 3))).as("una contiene a la otra").isTrue();
        assertThat(f(1, 3).seSolapaCon(f(3, 5))).as("terminar cuando la otra empieza no es solapar").isFalse();
        assertThat(f(3, 5).seSolapaCon(f(1, 3))).isFalse();
        assertThat(f(1, 2).seSolapaCon(f(5, 6))).isFalse();
    }

    @Test
    void no_se_reserva_en_el_pasado_ni_sin_espacio() {
        assertThatThrownBy(() -> Reserva.nueva("sala", "ana", f(-2, -1), AHORA)).hasMessageContaining("pasado");
        assertThatThrownBy(() -> Reserva.nueva("sala", "ana", f(0, 1), AHORA)).as("empezar ahora mismo ya no es futuro").hasMessageContaining("pasado");
        assertThatThrownBy(() -> Reserva.nueva(" ", "ana", f(1, 2), AHORA)).hasMessageContaining("espacio");
        assertThat(Reserva.nueva("sala", "ana", f(1, 2), AHORA).estado()).isEqualTo(Reserva.Estado.ACTIVA);
    }

    @Test
    void se_cancela_hasta_24_horas_antes_del_inicio_y_despues_no() {
        Reserva r = Reserva.nueva("sala", "ana", f(48, 49), AHORA);          // empieza en 48 h; el plazo vence en la hora 24
        r.cancelar(h(24));                                                     // justo en el límite: se permite
        assertThat(r.estado()).isEqualTo(Reserva.Estado.CANCELADA);

        Reserva tarde = Reserva.nueva("sala", "ana", f(48, 49), AHORA);
        assertThatThrownBy(() -> tarde.cancelar(h(24).plusSeconds(1))).isInstanceOf(Reserva.CancelacionFueraDePlazo.class);
        assertThat(tarde.estado()).as("sigue activa").isEqualTo(Reserva.Estado.ACTIVA);
    }

    @Test
    void cancelar_dos_veces_es_inofensivo() {
        Reserva r = Reserva.nueva("sala", "ana", f(100, 101), AHORA);
        r.cancelar(AHORA);
        r.cancelar(h(99));                                                     // ya cancelada: no falla aunque ya pasó el plazo
        assertThat(r.estado()).isEqualTo(Reserva.Estado.CANCELADA);
    }

    @Test
    void dos_reservas_chocan_solo_si_son_del_mismo_espacio_estan_activas_y_se_solapan() {
        Reserva a = Reserva.nueva("sala-1", "ana", f(10, 12), AHORA);
        assertThat(a.chocaCon(Reserva.nueva("sala-1", "beto", f(11, 13), AHORA))).isTrue();
        assertThat(a.chocaCon(Reserva.nueva("sala-2", "beto", f(11, 13), AHORA))).as("otro espacio").isFalse();
        assertThat(a.chocaCon(Reserva.nueva("sala-1", "beto", f(12, 14), AHORA))).as("contiguas").isFalse();
        Reserva cancelada = new Reserva(UUID.randomUUID(), "sala-1", "beto", f(11, 13), Reserva.Estado.CANCELADA);
        assertThat(a.chocaCon(cancelada)).as("una cancelada no ocupa").isFalse();
    }

    @Test
    void los_huecos_libres_son_lo_que_no_cubre_ninguna_franja_ocupada() {
        var libres = Franja.huecosLibres(h(0), h(10), List.of(f(5, 6), f(2, 3), f(2, 4)));        // desordenadas y solapadas entre sí
        assertThat(libres).containsExactly(new Franja.Hueco(h(0), h(2)), new Franja.Hueco(h(4), h(5)), new Franja.Hueco(h(6), h(10)));
        assertThat(Franja.huecosLibres(h(0), h(3), List.of())).containsExactly(new Franja.Hueco(h(0), h(3)));
        assertThat(Franja.huecosLibres(h(2), h(4), List.of(f(0, 8)))).as("todo ocupado").isEmpty();
        assertThat(Franja.huecosLibres(h(0), h(2), List.of(f(2, 3), f(-1, 0)))).as("las que solo tocan el borde no cuentan").containsExactly(new Franja.Hueco(h(0), h(2)));
        assertThatThrownBy(() -> Franja.huecosLibres(h(2), h(2), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
