package billetera;

import billetera.Billetera.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;

import static org.assertj.core.api.Assertions.*;

class BilleteraTest {
    static class RelojMutable extends Clock {
        Instant ahora = Instant.parse("2026-03-10T15:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return ahora; }
    }

    static BigDecimal m(String v) { return new BigDecimal(v); }

    RelojMutable reloj = new RelojMutable();
    Cuenta ana = new Cuenta("ana", "1234", reloj), beto = new Cuenta("beto", "0000", reloj);

    @Test void retiro_cobra_comision_y_registra_ambos_movimientos() {
        ana.depositar(m("500"));
        ana.retirar("1234", m("100"));
        assertThat(ana.saldo()).isEqualByComparingTo("399.00");
        assertThat(ana.historial()).extracting(Movimiento::tipo).containsExactly(Tipo.DEPOSITO, Tipo.RETIRO, Tipo.COMISION);
    }

    @Test void transferencia_mueve_dinero_con_comision_para_quien_envia() {
        ana.depositar(m("200"));
        ana.transferir("1234", beto, m("100"));
        assertThat(ana.saldo()).isEqualByComparingTo("99.50");
        assertThat(beto.saldo()).isEqualByComparingTo("100");
    }

    @Test void saldo_insuficiente_incluye_la_comision_y_no_cambia_nada() {
        ana.depositar(m("100"));
        assertThatThrownBy(() -> ana.retirar("1234", m("100"))).isInstanceOf(SaldoInsuficiente.class);
        assertThat(ana.saldo()).isEqualByComparingTo("100");
        assertThat(ana.historial()).hasSize(1);
    }

    @Test void tope_diario_se_reinicia_al_dia_siguiente_con_el_reloj_controlado() {
        ana.depositar(m("5000"));
        ana.retirar("1234", m("900"));
        assertThatThrownBy(() -> ana.retirar("1234", m("101"))).isInstanceOf(TopeDiarioExcedido.class);
        ana.retirar("1234", m("100")); // justo en el tope
        assertThatThrownBy(() -> ana.retirar("1234", m("1"))).isInstanceOf(TopeDiarioExcedido.class);
        reloj.ahora = reloj.ahora.plus(Duration.ofDays(1));
        ana.retirar("1234", m("1000"));
        assertThat(ana.saldo()).isEqualByComparingTo("2980.00");
    }

    @Test void tres_pines_incorrectos_bloquean_aun_con_el_pin_correcto() {
        ana.depositar(m("100"));
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> ana.retirar("9999", m("1"))).hasMessageContaining("PIN");
        assertThat(ana.bloqueada()).isTrue();
        assertThatThrownBy(() -> ana.retirar("1234", m("1"))).isInstanceOf(CuentaBloqueada.class);
        ana.desbloquear();
        ana.retirar("1234", m("1"));
    }

    @Test void un_acierto_reinicia_el_contador_de_fallos() {
        ana.depositar(m("100"));
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> ana.retirar("x", m("1"))).isInstanceOf(ErrorDeDominio.class);
        ana.retirar("1234", m("1"));
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> ana.retirar("x", m("1"))).isInstanceOf(ErrorDeDominio.class);
        assertThat(ana.bloqueada()).isFalse();
    }

    @Test void historial_es_inmutable_y_los_montos_invalidos_se_rechazan() {
        ana.depositar(m("10"));
        assertThatThrownBy(() -> ana.historial().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ana.depositar(m("0"))).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> ana.depositar(m("-5"))).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> ana.transferir("1234", ana, m("1"))).isInstanceOf(ErrorDeDominio.class);
        assertThat(ana.historial()).hasSize(1);
    }
}
