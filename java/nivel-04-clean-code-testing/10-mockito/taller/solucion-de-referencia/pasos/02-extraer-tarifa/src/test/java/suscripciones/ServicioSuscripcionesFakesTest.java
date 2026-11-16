package suscripciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Estilo 2: fakes escritos a mano. Se verifica el estado final, no las llamadas. */
class ServicioSuscripcionesFakesTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-01-31T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate HOY = LocalDate.of(2026, 1, 31);

    private final PasarelaFalsa pasarela = new PasarelaFalsa();
    private final RegistroEnMemoria registro = new RegistroEnMemoria();
    private final ServicioSuscripciones servicio = new ServicioSuscripciones(pasarela, registro, RELOJ);

    @Test
    void renovarTresMesesCobraElValorCompleto() {
        servicio.renovar("ana", 3);

        assertThat(pasarela.cobrosRecibidos()).containsExactly(new Cobro("ana", 90_000, "Renovación 3 meses"));
    }

    @Test
    void renovarDoceMesesCobraConVeintePorCientoDeDescuento() {
        servicio.renovar("ana", 12);

        assertThat(pasarela.cobrosRecibidos()).extracting(Cobro::valorEnPesos).containsExactly(288_000L);
    }

    @Test
    void renovarOnceMesesNoTieneDescuento() {
        servicio.renovar("ana", 11);

        assertThat(pasarela.cobrosRecibidos()).extracting(Cobro::valorEnPesos).containsExactly(330_000L);
    }

    @Test
    void elVencimientoEsHoyMasLosMesesRenovados() {
        assertThat(servicio.renovar("ana", 1)).isEqualTo("Renovado hasta 2026-02-28 (FALSO-1)");
        assertThat(servicio.renovar("ana", 3)).isEqualTo("Renovado hasta 2026-04-30 (FALSO-2)");
    }

    @Test
    void registraLaRenovacionCobradaConSuComprobante() {
        servicio.renovar("ana", 3);

        assertThat(registro.lineas()).containsExactly(new RegistroEnMemoria.Linea(HOY, "ana", 90_000, "FALSO-1"));
    }

    @Test
    void siLaPasarelaRechazaElCobroNoSeRegistraLaRenovacion() {
        pasarela.rechazarConMotivo("Tarjeta vencida");

        assertThatThrownBy(() -> servicio.renovar("ana", 3))
                .isInstanceOf(PagoRechazadoException.class)
                .hasMessage("Tarjeta vencida");

        assertThat(registro.lineas()).isEmpty();
    }
}
