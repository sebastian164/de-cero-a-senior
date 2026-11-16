package taller.state;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SolicitudTest {

    static final LocalDate INICIO = LocalDate.of(2025, 7, 1);

    static Stream<Function<LocalDate, Solicitud>> versiones() {
        return Stream.of(SolicitudConEnum::new, SolicitudConState::new);
    }

    @ParameterizedTest
    @MethodSource("versiones")
    void caminoFeliz(Function<LocalDate, Solicitud> nueva) {
        Solicitud s = nueva.apply(INICIO);
        assertEquals("BORRADOR", s.estado());
        s.enviar();
        assertEquals("ENVIADA", s.estado());
        s.aprobar();
        assertEquals("APROBADA", s.estado());
        s.iniciarDisfrute(INICIO);
        assertEquals("EN_DISFRUTE", s.estado());
    }

    @ParameterizedTest
    @MethodSource("versiones")
    void rechazadaVuelveABorrador(Function<LocalDate, Solicitud> nueva) {
        Solicitud s = nueva.apply(INICIO);
        s.enviar();
        s.rechazar();
        assertEquals("RECHAZADA", s.estado());
        s.corregir();
        assertEquals("BORRADOR", s.estado());
        s.enviar();
        assertEquals("ENVIADA", s.estado());
    }

    @ParameterizedTest
    @MethodSource("versiones")
    void cancelarSoloAntesDeLaFechaDeInicio(Function<LocalDate, Solicitud> nueva) {
        Solicitud antes = nueva.apply(INICIO);
        antes.enviar();
        antes.aprobar();
        antes.cancelar(INICIO.minusDays(1));
        assertEquals("CANCELADA", antes.estado());

        Solicitud tarde = nueva.apply(INICIO);
        tarde.enviar();
        tarde.aprobar();
        assertThrows(IllegalStateException.class, () -> tarde.cancelar(INICIO));
        assertEquals("APROBADA", tarde.estado());
    }

    @ParameterizedTest
    @MethodSource("versiones")
    void enDisfruteNoSePuedeCancelar(Function<LocalDate, Solicitud> nueva) {
        Solicitud s = nueva.apply(INICIO);
        s.enviar();
        s.aprobar();
        assertThrows(IllegalStateException.class, () -> s.iniciarDisfrute(INICIO.minusDays(1)));
        s.iniciarDisfrute(INICIO.plusDays(1));
        assertThrows(IllegalStateException.class, () -> s.cancelar(INICIO.minusDays(5)));
        assertEquals("EN_DISFRUTE", s.estado());
    }

    @ParameterizedTest
    @MethodSource("versiones")
    void transicionesInvalidas(Function<LocalDate, Solicitud> nueva) {
        Solicitud s = nueva.apply(INICIO);
        assertThrows(IllegalStateException.class, s::aprobar);
        assertThrows(IllegalStateException.class, s::corregir);
        assertThrows(IllegalStateException.class, () -> s.cancelar(INICIO.minusDays(1)));
        s.enviar();
        assertThrows(IllegalStateException.class, s::enviar);
        s.rechazar();
        assertThrows(IllegalStateException.class, s::aprobar);
    }

    @Test
    void laTablaRespondeDesdeDondeSeLlega() {
        assertEquals(EnumSet.of(EstadoVacaciones.ENVIADA), EstadoVacaciones.origenesDe(EstadoVacaciones.APROBADA));
        assertEquals(EnumSet.of(EstadoVacaciones.RECHAZADA), EstadoVacaciones.origenesDe(EstadoVacaciones.BORRADOR));
    }
}
