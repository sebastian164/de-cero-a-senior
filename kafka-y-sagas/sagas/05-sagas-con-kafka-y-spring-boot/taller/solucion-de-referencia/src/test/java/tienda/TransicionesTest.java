package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Paso 2: la lógica de la saga, pura. Sin Spring, sin Kafka, sin base: corre en milisegundos. */
class TransicionesTest {

    @Test
    void lasTransicionesSonLasDeLaSesion02() {
        Transiciones t = new Transiciones(false);
        assertEquals(Estado.COBRANDO, t.siguiente(Estado.RESERVANDO, "inventario-reservado").orElseThrow().nuevoEstado());
        assertEquals(Estado.CANCELADA, t.siguiente(Estado.RESERVANDO, "sin-existencias").orElseThrow().nuevoEstado());
        assertEquals("inventario", t.siguiente(Estado.COBRANDO, "pago-rechazado").orElseThrow().destino());
        assertEquals(Estado.REEMBOLSANDO, t.siguiente(Estado.PROGRAMANDO_ENVIO, "envio-imposible").orElseThrow().nuevoEstado());
        assertEquals(Estado.COMPLETADA, t.siguiente(Estado.PROGRAMANDO_ENVIO, "envio-programado").orElseThrow().nuevoEstado());
        assertTrue(t.siguiente(Estado.COMPLETADA, "pago-aprobado").isEmpty(), "lo que no está en la tabla se ignora");
        assertTrue(t.siguiente(Estado.CANCELADA, "inventario-reservado").isEmpty());
        assertEquals(Estado.LIBERANDO, t.alVencer(Estado.RESERVANDO).orElseThrow().nuevoEstado());
        assertEquals(Estado.REEMBOLSANDO, t.alVencer(Estado.COBRANDO).orElseThrow().nuevoEstado());
        assertTrue(t.alVencer(Estado.PROGRAMANDO_ENVIO).isEmpty(), "pasado el pivote no se abandona");
    }

    @Test
    void esElMismoComponenteQueProbaronLasSesiones02y04() throws Exception {
        for (String archivo : new String[] {"Transiciones.java", "Estado.java"}) {
            byte[] aqui = Files.readAllBytes(Path.of("src/main/java/tienda", archivo));
            byte[] sesion02 = Files.readAllBytes(Path.of("../../../02-saga-orquestada/taller/solucion-de-referencia/src/main/java/tienda", archivo));
            assertTrue(java.util.Arrays.equals(aqui, sesion02), archivo + " no es idéntico al de la sesión 02: sus pruebas ya no valen");
        }
    }
}
