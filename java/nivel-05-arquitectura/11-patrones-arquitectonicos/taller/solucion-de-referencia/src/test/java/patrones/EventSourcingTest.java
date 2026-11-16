package patrones;

import org.junit.jupiter.api.Test;
import patrones.eventsourcing.CuentaBancaria;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSourcingTest {

    static final LocalDate D1 = LocalDate.of(2030, 1, 1);

    @Test
    void elEjemploDelReadme() {
        var cuenta = CuentaBancaria.abrir("001", 100, D1);
        cuenta.depositar(50, D1.plusDays(1));
        cuenta.retirar(30, D1.plusDays(2));
        assertThat(cuenta.saldo()).isEqualTo(120);                                  // 100 + 50 - 30
        var reconstruida = CuentaBancaria.desde(cuenta.eventosNuevos());
        assertThat(reconstruida.saldo()).isEqualTo(120);
        assertThat(reconstruida.numero()).isEqualTo("001");
    }

    @Test
    void saldoEnCualquierFechaPasada() {
        var cuenta = CuentaBancaria.abrir("001", 100, D1);
        cuenta.depositar(50, D1.plusDays(10));
        cuenta.retirar(30, D1.plusDays(20));
        var historia = cuenta.eventosNuevos();
        assertThat(CuentaBancaria.saldoAl(historia, D1)).isEqualTo(100);
        assertThat(CuentaBancaria.saldoAl(historia, D1.plusDays(15))).isEqualTo(150);
        assertThat(CuentaBancaria.saldoAl(historia, D1.plusDays(25))).isEqualTo(120);
    }

    @Test
    void lasReglasSeEvaluanSobreElEstadoReconstruido() {
        var cuenta = CuentaBancaria.desde(CuentaBancaria.abrir("001", 10, D1).eventosNuevos());
        assertThatThrownBy(() -> cuenta.retirar(11, D1)).hasMessage("Saldo insuficiente");
    }
}
