package bicis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** El adaptador real del reloj, probado con un Clock fijo: tampoco depende de la hora real. */
class RelojDelSistemaTest {

    @Test
    void daLaHoraDelRelojQueRecibe() {
        Clock fijo = Clock.fixed(Instant.parse("2026-11-15T15:30:00Z"), ZoneOffset.ofHours(-5));
        assertEquals(LocalDateTime.of(2026, 11, 15, 10, 30), new RelojDelSistema(fijo).ahora());
    }
}
