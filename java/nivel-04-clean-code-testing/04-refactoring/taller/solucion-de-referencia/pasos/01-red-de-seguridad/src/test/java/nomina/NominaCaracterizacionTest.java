package nomina;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pruebas de caracterización: fijan lo que el código HACE hoy (no lo que debería hacer).
 * Los valores se obtuvieron ejecutando el liquidar original de taller/inicio.
 */
class NominaCaracterizacionTest {

    @ParameterizedTest(name = "[{index}] {0}, {1} días, {2} h extra, nocturnas={3}, detalle={4}, {5}")
    @CsvSource(delimiter = '|', textBlock = """
            3000000 | 10 |  2 | true  | false | CO | Neto: 960250
            3000000 | 10 |  2 | true  | true  | CO | Neto: 960250 (base 1000000, extra 43750, descuento 83500)
            3000000 | 30 |  0 | false | true  | MX | Neto: 2850000 (base 3000000, extra 0, descuento 150000)
            2400000 | 15 |  4 | false | true  | CO | Neto: 1150000 (base 1200000, extra 50000, descuento 100000)
            1300000 | 15 |  3 | true  | true  | PE | Neto: 678429 (base 649995, extra 28434, descuento 0)
            1300000 | 30 |  0 | false | true  | CO | Neto: 1195991 (base 1299990, extra 0, descuento 103999)
            1000001 | 30 | 10 | true  | true  | MX | Neto: 1019251 (base 999990, extra 72905, descuento 53644)
            0       | 30 |  5 | true  | true  | CO | Neto: 0 (base 0, extra 0, descuento 0)
            """)
    void conservaElResultadoActual(long salario, int dias, int horasExtra, boolean nocturnas,
                                   boolean incluirDetalle, String pais, String esperado) {
        assertThat(Nomina.liquidar(salario, dias, horasExtra, nocturnas, incluirDetalle, pais)).isEqualTo(esperado);
    }
}
