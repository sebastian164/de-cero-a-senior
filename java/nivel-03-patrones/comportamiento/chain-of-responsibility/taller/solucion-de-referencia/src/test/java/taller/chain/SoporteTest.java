package taller.chain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SoporteTest {

    List<String> metricas = new ArrayList<>();
    AtomicLong reloj = new AtomicLong();
    Soporte soporte = Soporte.estandar(metricas::add, () -> reloj.addAndGet(100));

    String atender(String asunto, String categoria, Prioridad p, boolean corporativo) {
        return soporte.atender(new Tiquete(asunto, categoria, p, corporativo)).toString();
    }

    @Test
    void cadaEslabonAtiendeOCorta() {
        assertEquals("Spam: descartado", atender("GANA DINERO ya", "OTRO", Prioridad.CRITICA, true));
        assertEquals("FAQ: Enviado artículo 'Cambiar contraseña'", atender("olvidé la contraseña", "CUENTA", Prioridad.BAJA, false));
        assertEquals("Nivel 2: asignado", atender("caído", "PLATAFORMA", Prioridad.CRITICA, false));
        assertEquals("Nivel 2: asignado", atender("reporte", "OTRO", Prioridad.BAJA, true));
        assertEquals("Nivel 1: asignado a la cola FACTURACION", atender("factura", "FACTURACION", Prioridad.MEDIA, false));
    }

    @Test
    void elEslabonFinalRecogeLoQueNadieAtiende() {
        assertEquals("Triage manual: nadie lo atendió automáticamente", atender("hola", "SIN_CATEGORIA", Prioridad.BAJA, false));
    }

    @Test
    void reordenarFaqYSpamRespondeAlSpam() {
        Tiquete spam = new Tiquete("GANA DINERO con tu contraseña", "CUENTA", Prioridad.BAJA, false);
        assertEquals("Spam", soporte.atender(spam).quien());
        assertEquals("FAQ", Soporte.faqAntesQueSpam().atender(spam).quien(), "se le contesta al spammer");
    }

    @Test
    void reordenarNivelesMandaLosCriticosANivel1() {
        Tiquete critico = new Tiquete("caído", "PLATAFORMA", Prioridad.CRITICA, false);
        assertEquals("Nivel 1", Soporte.nivel1AntesQueNivel2().atender(critico).quien());
    }

    @Test
    void metricasMidenElRestoDeLaCadena() {
        atender("factura", "FACTURACION", Prioridad.MEDIA, false);
        atender("GANA DINERO", "X", Prioridad.BAJA, false);
        assertEquals(List.of("Nivel 1 en 100 ns", "Spam en 100 ns"), metricas);
    }
}
