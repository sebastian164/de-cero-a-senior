package fundamentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import org.junit.jupiter.api.Test;

/** Paso 5: disponibilidad en serie y en paralelo. */
class DisponibilidadTest {

    static final MathContext MC = MathContext.DECIMAL64;
    static final BigDecimal MINUTOS_MES = new BigDecimal(30 * 24 * 60);

    @Test
    void tres_componentes_en_serie_al_99_9() {
        BigDecimal a = new BigDecimal("0.999").pow(3, MC);
        BigDecimal caida = BigDecimal.ONE.subtract(a).multiply(MINUTOS_MES);
        System.out.printf("Serie: %s → %.1f min de caída al mes (30 días)%n", a, caida);
        assertThat(a).isEqualByComparingTo("0.997002999");
        assertThat(caida.doubleValue()).isCloseTo(129.5, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void dos_replicas_redundantes_al_99() {
        BigDecimal a = BigDecimal.ONE.subtract(new BigDecimal("0.01").pow(2, MC));
        BigDecimal caida = BigDecimal.ONE.subtract(a).multiply(MINUTOS_MES);
        System.out.printf("Paralelo: %s → %.1f min de caída al mes (30 días)%n", a, caida);
        assertThat(a).isEqualByComparingTo("0.9999");
        assertThat(caida.doubleValue()).isCloseTo(4.3, org.assertj.core.data.Offset.offset(0.1));
    }
}
