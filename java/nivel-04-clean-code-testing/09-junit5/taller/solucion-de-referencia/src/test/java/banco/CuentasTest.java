package banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Cuentas de la lección 07 (versión mejorada). Arranca con A = 100.000 y B = 0.
 * Cada prueba crea sus propias cuentas en @BeforeEach: ninguna depende de otra ni del orden.
 */
class CuentasTest {

    private Cuentas cuentas;

    @BeforeEach
    void crearCuentas() {
        cuentas = new Cuentas();
    }

    @Test
    void arrancaConLosSaldosIniciales() {
        assertThat(cuentas.saldos()).containsOnly(entry("A", 100_000L), entry("B", 0L));
    }

    @Test
    void transferirDescuentaDelOrigenYAbonaAlDestino() {
        cuentas.transferir("A", "B", 30_000);

        assertThat(cuentas.saldos()).containsOnly(entry("A", 70_000L), entry("B", 30_000L));
    }

    @Test
    void transferirMasQueElSaldoLanzaFondosInsuficientesConElContexto() {
        assertThatThrownBy(() -> cuentas.transferir("A", "B", 500_000))
                .isInstanceOf(FondosInsuficientesException.class)
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Fondos insuficientes en A: saldo 100000, solicitado 500000");
    }

    @Test
    void unaTransferenciaRechazadaPorFondosNoModificaNingunSaldo() {
        assertThatThrownBy(() -> cuentas.transferir("A", "B", 500_000));

        assertThat(cuentas.saldos()).containsOnly(entry("A", 100_000L), entry("B", 0L));
    }

    @Test
    void transferirDesdeUnaCuentaInexistenteLanzaCuentaNoEncontrada() {
        assertThatThrownBy(() -> cuentas.transferir("X", "B", 10))
                .isInstanceOf(CuentaNoEncontradaException.class)
                .hasMessage("No existe la cuenta X");
    }

    @Test
    void transferirAUnaCuentaInexistenteLanzaCuentaNoEncontrada() {
        assertThatThrownBy(() -> cuentas.transferir("A", "X", 10))
                .isInstanceOf(CuentaNoEncontradaException.class)
                .hasMessage("No existe la cuenta X");
    }

    @Test
    void siElDestinoNoExisteElOrigenNoPierdeElDinero() {
        assertThatThrownBy(() -> cuentas.transferir("A", "X", 10));

        assertThat(cuentas.saldos()).containsEntry("A", 100_000L).doesNotContainKey("X");
    }

    @Test
    void transferirALaMismaCuentaSeRechaza() {
        assertThatThrownBy(() -> cuentas.transferir("A", "A", 40_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El origen y el destino son la misma cuenta: A");
    }

    @Test
    void unaTransferenciaALaMismaCuentaNoModificaElSaldo() {
        assertThatThrownBy(() -> cuentas.transferir("A", "A", 40_000));

        assertThat(cuentas.saldos()).containsOnly(entry("A", 100_000L), entry("B", 0L));
    }

    @Test
    void losSaldosQueSeEntreganNoPermitenModificarLasCuentas() {
        Map<String, Long> saldos = cuentas.saldos();

        assertThatThrownBy(() -> saldos.put("A", 1_000_000L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void losSaldosQueSeEntreganSonUnaFotoYNoCambianConTransferenciasPosteriores() {
        Map<String, Long> antes = cuentas.saldos();

        cuentas.transferir("A", "B", 1);

        assertThat(antes).containsEntry("A", 100_000L);
    }

    @Nested
    @DisplayName("Valores límite del monto")
    class LimitesDelMonto {

        @ParameterizedTest(name = "monto {0} no es positivo")
        @ValueSource(longs = {0, -1, Long.MIN_VALUE})
        void unMontoQueNoEsPositivoSeRechaza(long monto) {
            assertThatThrownBy(() -> cuentas.transferir("A", "B", monto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El monto debe ser positivo: " + monto);
        }

        @ParameterizedTest(name = "monto {0} cabe en el saldo de 100000")
        @ValueSource(longs = {1, 99_999, 100_000})
        void unMontoHastaElSaldoCompletoSeTransfiere(long monto) {
            cuentas.transferir("A", "B", monto);

            assertThat(cuentas.saldos()).containsOnly(entry("A", 100_000L - monto), entry("B", monto));
        }

        @ParameterizedTest(name = "monto {0} supera el saldo de 100000")
        @ValueSource(longs = {100_001, Long.MAX_VALUE})
        void unMontoMayorQueElSaldoSeRechaza(long monto) {
            assertThatThrownBy(() -> cuentas.transferir("A", "B", monto))
                    .isInstanceOf(FondosInsuficientesException.class);
        }
    }

    @Nested
    @DisplayName("Después de transferir 40.000 de A a B")
    class DespuesDeUnaTransferencia {

        @BeforeEach
        void transferir() {
            cuentas.transferir("A", "B", 40_000);
        }

        @Test
        void bPuedeDevolverTodoLoQueRecibio() {
            cuentas.transferir("B", "A", 40_000);

            assertThat(cuentas.saldos()).containsOnly(entry("A", 100_000L), entry("B", 0L));
        }

        @Test
        void bNoPuedeTransferirMasDeLoQueRecibio() {
            assertThatThrownBy(() -> cuentas.transferir("B", "A", 40_001))
                    .isInstanceOf(FondosInsuficientesException.class)
                    .hasMessage("Fondos insuficientes en B: saldo 40000, solicitado 40001");
        }

        @Test
        void elSaldoDisponibleDeAYaNoEsElInicial() {
            assertThatThrownBy(() -> cuentas.transferir("A", "B", 60_001))
                    .isInstanceOf(FondosInsuficientesException.class);
        }
    }
}
