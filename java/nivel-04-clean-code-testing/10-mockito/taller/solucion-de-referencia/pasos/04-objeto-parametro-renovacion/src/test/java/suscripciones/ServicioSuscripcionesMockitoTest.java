package suscripciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Estilo 1: Mockito. Se simulan solo los dos puertos (lo lento y con efectos).
 * Cobro, Comprobante y el reloj son valores reales: no se simulan.
 */
@ExtendWith(MockitoExtension.class)
class ServicioSuscripcionesMockitoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-01-31T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate HOY = LocalDate.of(2026, 1, 31);
    private static final Comprobante COMPROBANTE = new Comprobante("ACME-77");

    @Mock Cobros cobros;
    @Mock RegistroRenovaciones registro;
    @Captor ArgumentCaptor<Cobro> cobroEnviado;

    private ServicioSuscripciones servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new ServicioSuscripciones(cobros, registro, RELOJ);
    }

    @Test
    void renovarTresMesesCobraElValorCompleto() {
        when(cobros.cobrar(any())).thenReturn(COMPROBANTE);

        servicio.renovar("ana", 3);

        // NECESARIO: cobrar es el efecto que justifica la operación. El captor verifica el contenido exacto.
        verify(cobros).cobrar(cobroEnviado.capture());
        assertThat(cobroEnviado.getValue()).isEqualTo(new Cobro("ana", 90_000, "Renovación 3 meses"));
    }

    @Test
    void renovarDoceMesesCobraConVeintePorCientoDeDescuento() {
        when(cobros.cobrar(any())).thenReturn(COMPROBANTE);

        servicio.renovar("ana", 12);

        verify(cobros).cobrar(cobroEnviado.capture());   // NECESARIO (mismo motivo)
        assertThat(cobroEnviado.getValue().valorEnPesos()).isEqualTo(288_000);
    }

    @Test
    void renovarOnceMesesNoTieneDescuento() {
        when(cobros.cobrar(any())).thenReturn(COMPROBANTE);

        servicio.renovar("ana", 11);

        verify(cobros).cobrar(cobroEnviado.capture());   // NECESARIO
        assertThat(cobroEnviado.getValue().valorEnPesos()).isEqualTo(330_000);
    }

    @Test
    void elVencimientoEsHoyMasLosMesesRenovados() {
        when(cobros.cobrar(any())).thenReturn(COMPROBANTE);

        // 31 de enero + 1 mes = 28 de febrero: java.time ajusta al último día del mes.
        assertThat(servicio.renovar("ana", 1)).isEqualTo("Renovado hasta 2026-02-28 (ACME-77)");
        assertThat(servicio.renovar("ana", 3)).isEqualTo("Renovado hasta 2026-04-30 (ACME-77)");
    }

    @Test
    void registraLaRenovacionCobradaConSuComprobante() {
        when(cobros.cobrar(any())).thenReturn(COMPROBANTE);

        servicio.renovar("ana", 3);

        // NECESARIO: dejar constancia es el otro efecto que la operación promete.
        verify(registro).registrar(new Renovacion(HOY, "ana", 90_000, LocalDate.of(2026, 4, 30), COMPROBANTE));
    }

    @Test
    void siLaPasarelaRechazaElCobroNoSeRegistraLaRenovacion() {
        when(cobros.cobrar(any())).thenThrow(new PagoRechazadoException("Tarjeta vencida"));

        assertThatThrownBy(() -> servicio.renovar("ana", 3))
                .isInstanceOf(PagoRechazadoException.class)
                .hasMessage("Tarjeta vencida");

        // NECESARIO: la ausencia del efecto es justo lo que se quiere comprobar.
        verifyNoInteractions(registro);
    }

    /** Ejemplo deliberado de lo que NO conviene hacer. Se deja para medir cuánto se rompe en un refactor. */
    @Test
    void sobreEspecificada_renovarLlamaACadaPuertoEnOrdenYNadaMas() {
        when(cobros.cobrar(any())).thenReturn(COMPROBANTE);

        servicio.renovar("ana", 3);

        InOrder orden = inOrder(cobros, registro);
        orden.verify(cobros, times(1)).cobrar(new Cobro("ana", 90_000, "Renovación 3 meses"));
        orden.verify(registro, times(1)).registrar(new Renovacion(HOY, "ana", 90_000, LocalDate.of(2026, 4, 30), COMPROBANTE));
        // SOBRE-ESPECIFICACIÓN: el orden ya está implícito (sin comprobante no hay qué registrar) y
        // "nada más" prohíbe que mañana el servicio, por ejemplo, consulte el registro antes de cobrar.
        verifyNoMoreInteractions(cobros, registro);
    }
}
