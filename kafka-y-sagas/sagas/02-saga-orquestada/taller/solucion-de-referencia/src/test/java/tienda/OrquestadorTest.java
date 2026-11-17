package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import tienda.Orquestador.Caida;

class OrquestadorTest {

    @Test
    void losCuatroCaminos() {
        Tienda t = new Tienda(1, true, false);
        t.comprar("exito", 200_000);
        t.correr(10);
        t.comprar("sin-existencias", 200_000);
        t.correr(10);
        Tienda t2 = new Tienda(5, true, false);
        t2.envios.sinCobertura.add("envio");
        t2.comprar("rechazado", 5_000_000);
        t2.comprar("envio", 100_000);
        t2.correr(10);
        assertEquals(Estado.COMPLETADA, t.estado("exito"));
        assertEquals(Estado.CANCELADA, t.estado("sin-existencias"));
        assertEquals(Estado.CANCELADA, t2.estado("rechazado"));
        assertEquals(Estado.CANCELADA, t2.estado("envio"));
        assertEquals(List.of("RESERVANDO --inventario-reservado--> COBRANDO", "COBRANDO --pago-aprobado--> PROGRAMANDO_ENVIO",
                "PROGRAMANDO_ENVIO --envio-imposible--> REEMBOLSANDO", "REEMBOLSANDO --pago-reembolsado--> LIBERANDO",
                "LIBERANDO --inventario-liberado--> CANCELADA"), t2.almacen.sagas.get("envio").transiciones.subList(1, 6));
        assertEquals(List.of("envio"), t2.pagos.reembolsos);
        assertEquals(5, t2.inventario.existencias);
        assertEquals("", t.inconsistencias());
        assertEquals("", t2.inconsistencias());
    }

    /** Paso 4: tres puntos de caída en la misma saga. En los tres, un orquestador nuevo la termina. */
    @ParameterizedTest
    @EnumSource(value = Caida.class, names = {"DENTRO_DE_LA_TRANSACCION", "TRAS_CONFIRMAR_LA_TRANSACCION"})
    void sobreviveASuPropiaCaida(Caida caida) {
        Tienda t = new Tienda(5, true, false);
        t.comprar("s", 200_000);
        t.correr(2);                                   // ya va por COBRANDO
        t.orquestador.caida = caida;
        t.correr(10);
        assertEquals(1, t.caidas);
        assertEquals(Estado.COMPLETADA, t.estado("s"));
        assertEquals(1, t.pagos.efectos, "un solo cobro");
        assertEquals("", t.inconsistencias());
    }

    @Test
    void sobreviveALaCaidaDelPublicadorTrasPublicar() {
        Tienda t = new Tienda(5, true, false);
        t.comprar("s", 200_000);
        t.correr(1);
        t.publicador.caerTrasPublicarUna = true;      // la orden sale dos veces
        t.correr(10);
        assertEquals(Estado.COMPLETADA, t.estado("s"));
        assertEquals(1, t.pagos.efectos, "la orden repetida no cobra dos veces");
        assertTrue(t.orquestador.ignoradas.stream().anyMatch(i -> i.startsWith("repetida")));
    }

    /** Paso 5: sin bandeja de salida, una caída entre guardar el estado y enviar la orden deja la saga muda. */
    @Test
    void sinSalidaLaCaidaEntreGuardarYEnviarDejaLaSagaAtascada() {
        Tienda t = new Tienda(5, false, false);
        t.relojActivo = false;                         // sin tiempo límite que la rescate
        t.comprar("s", 200_000);
        t.correr(1);
        t.orquestador.caida = Caida.ENTRE_GUARDAR_Y_ENVIAR;
        t.correr(20);
        assertEquals(Estado.PROGRAMANDO_ENVIO, t.estado("s"), "el estado dice PROGRAMANDO_ENVIO");
        assertEquals(0, t.envios.efectos, "y la orden de programar el envío nunca salió");
        assertEquals(1, t.orquestador.atascadas(t.ciclo, 10).size(), "solo la consulta del paso 8 lo delata");
    }

    @Test
    void sinSalidaPeroConRelojElReintentoLaRescata() {
        Tienda t = new Tienda(5, false, false);
        t.comprar("s", 200_000);
        t.correr(1);
        t.orquestador.caida = Caida.ENTRE_GUARDAR_Y_ENVIAR;
        t.correr(20);
        assertEquals(Estado.COMPLETADA, t.estado("s"));
        assertTrue(t.almacen.sagas.get("s").transiciones.contains("reintento 1 de programar-envio"));
    }

    @Test
    void conSalidaLaMismaCaidaNoExisteYLaSagaTermina() {
        Tienda t = new Tienda(5, true, false);
        t.relojActivo = false;
        t.comprar("s", 200_000);
        t.correr(1);
        t.orquestador.caida = Caida.ENTRE_GUARDAR_Y_ENVIAR;   // con salida no hay envío directo: este punto no existe
        t.correr(20);
        assertEquals(Estado.COMPLETADA, t.estado("s"));
        assertEquals(0, t.caidas);
    }

    /** Paso 6: tiempo límite. Una orden perdida se reintenta; tres sin respuesta, se compensa. */
    @Test
    void unaOrdenPerdidaSeReintenta() {
        Tienda t = new Tienda(5, true, false);
        t.inventario.ordenesQueIgnora = 1;
        t.comprar("s", 200_000);
        t.correr(20);
        assertEquals(Estado.COMPLETADA, t.estado("s"));
        assertTrue(t.almacen.sagas.get("s").transiciones.contains("reintento 1 de reservar-inventario"));
    }

    @Test
    void tresIntentosSinRespuestaCompensan() {
        Tienda t = new Tienda(5, true, false);
        t.pagos.ordenesQueIgnora = 3;                  // las tres órdenes de cobrar se pierden
        t.comprar("s", 200_000);
        t.correr(30);
        assertEquals(Estado.CANCELADA, t.estado("s"));
        assertTrue(t.almacen.sagas.get("s").transiciones.contains("COBRANDO --vencimiento--> REEMBOLSANDO"));
        assertTrue(t.pagos.lapidas.contains("s"), "Pagos recibió un reembolso sin cobro: deja lápida");
        assertEquals(5, t.inventario.existencias);
        assertEquals("", t.inconsistencias());
    }

    /** Paso 7: respuesta repetida y respuesta fuera de lugar. */
    @Test
    void respuestasRepetidasOFueraDeLugarNoLoCorrompen() {
        Tienda t = new Tienda(5, true, false);
        t.comprar("s", 5_000_000);                     // será rechazado: la saga acabará liberando
        t.correr(1);                                   // inventario respondió y el orquestador ya pidió cobrar
        Mensaje reservado = t.broker.enviados.stream().filter(m -> m.tipo().equals("inventario-reservado")).findFirst().orElseThrow();
        t.broker.enviar("orquestador", reservado);     // la misma respuesta, dos veces
        t.correr(1);                                   // cobra (rechazado) y pide liberar
        assertEquals(Estado.LIBERANDO, t.estado("s"));
        t.broker.enviar("orquestador", new Mensaje("tardio-1", "s", "pago-aprobado"));
        t.correr(10);
        assertEquals(Estado.CANCELADA, t.estado("s"));
        assertTrue(t.orquestador.ignoradas.contains("repetida inventario-reservado [s]"), t.orquestador.ignoradas.toString());
        assertTrue(t.orquestador.ignoradas.stream().anyMatch(i -> i.startsWith("fuera de lugar pago-aprobado")),
                t.orquestador.ignoradas.toString());
        assertEquals("", t.inconsistencias());
    }

    /** Paso 8: listar las sagas que llevan demasiado sin terminar. */
    @Test
    void consultaDeSagasAtascadas() {
        Tienda t = new Tienda(5, true, false);
        t.envios.ordenesQueIgnora = 100;                // envíos caído: después del pivote no se abandona
        t.comprar("s", 200_000);
        t.correr(40);
        assertEquals(Estado.PROGRAMANDO_ENVIO, t.estado("s"));
        List<String> atascadas = t.orquestador.atascadas(t.ciclo, 20);
        assertEquals(1, atascadas.size());
        assertTrue(atascadas.get(0).startsWith("s en PROGRAMANDO_ENVIO"), atascadas.get(0));
    }

    /** Paso 9: el paso de fidelidad. */
    @Test
    void puntosDeFidelidadSoloEnLasCompletadas() {
        Tienda t = new Tienda(5, true, true);
        t.comprar("bien", 200_000);
        t.comprar("mal", 5_000_000);
        t.correr(15);
        assertEquals(Estado.COMPLETADA, t.estado("bien"));
        assertEquals(Estado.CANCELADA, t.estado("mal"));
        assertEquals(List.of("bien"), List.copyOf(t.fidelidad.puntos));
    }
}
