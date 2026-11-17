package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tienda.Broker.Accion;

class HostilTest {

    @Test
    void elBrokerHostilEsDeterminista() {
        Tienda a = Escenarios.cuatroCaminos(new Broker().con(2, Accion.INTERCAMBIAR).con(5, Accion.RETRASAR).con(7, Accion.PERDER).con(9, Accion.DUPLICAR), true);
        Tienda b = Escenarios.cuatroCaminos(new Broker().con(2, Accion.INTERCAMBIAR).con(5, Accion.RETRASAR).con(7, Accion.PERDER).con(9, Accion.DUPLICAR), true);
        assertEquals(a.broker.registro, b.broker.registro);
        assertEquals(a.broker.enviados, b.broker.enviados);
        assertEquals(a.estados(), b.estados());
    }

    @Test
    void conDuplicacionSinIdempotenciaHayInconsistencias() {
        String inconsistencias = Escenarios.cuatroCaminos(Escenarios.duplicarTodo(), false).invariante(3);
        assertFalse(inconsistencias.isEmpty());
        assertTrue(inconsistencias.contains("exito completada con cobros=2"), inconsistencias);
    }

    @Test
    void conDuplicacionEIdempotenciaCeroInconsistencias() {
        Tienda t = Escenarios.cuatroCaminos(Escenarios.duplicarTodo(), true);
        assertEquals("", t.invariante(3));
        assertEquals(Estado.COMPLETADA, t.estado("exito"));
        assertEquals(Estado.CANCELADA, t.estado("rechazo"));
        assertEquals(Estado.CANCELADA, t.estado("envio"));
        assertEquals(Estado.CANCELADA, t.estado("agotado"));
    }

    @Test
    void laLapidaEvitaQueLaReservaTardiaProspere() {
        Tienda sin = Escenarios.compensacionAntesQueLaAccion(false);
        assertEquals(Estado.CANCELADA, sin.estado("s"));
        assertTrue(sin.invariante(1).contains("s cancelada con cobros=0 reserva=1"), sin.invariante(1));
        Tienda con = Escenarios.compensacionAntesQueLaAccion(true);
        assertEquals("", con.invariante(1));
        assertTrue(con.inventario.lapidas.contains("s"));
    }

    /** Paso 5: la respuesta "perdida" solo estaba retrasada y llega después del reintento. */
    @Test
    void respuestaRetrasadaQueLlegaTrasElReintento() {
        Broker b = new Broker().con(2, Accion.RETRASAR);   // #2 es la respuesta inventario-reservado
        Tienda t = new Tienda(5, b);
        t.comprar("s", 100_000);
        t.correr(40);
        assertEquals(Estado.COMPLETADA, t.estado("s"));
        assertTrue(t.almacen.sagas.get("s").transiciones.contains("reintento 1 de reservar-inventario"));
        assertTrue(t.orquestador.ignoradas.contains("repetida inventario-reservado [s]"), t.orquestador.ignoradas.toString());
        assertEquals("", t.invariante(5));
    }

    @Test
    void compensacionQueFallaTresVecesYLuegoFunciona() {
        Tienda t = new Tienda(5, new Broker());
        t.inventario.fallosAlLiberar = 3;
        t.comprar("s", 5_000_000);
        t.correr(40);
        assertEquals(Estado.CANCELADA, t.estado("s"));
        List<String> esperas = t.almacen.sagas.get("s").transiciones.stream().filter(x -> x.startsWith("compensación fallida")).toList();
        assertEquals(3, esperas.size());
        assertTrue(t.almacen.revisionHumana.isEmpty());
        assertEquals("", t.invariante(5));
    }

    @Test
    void compensacionQueFallaSiempreVaARevisionHumana() {
        Tienda t = new Tienda(5, new Broker());
        t.inventario.fallosAlLiberar = 1000;
        t.comprar("s", 5_000_000);
        t.correr(100);
        assertEquals(Estado.REVISION_HUMANA, t.estado("s"));
        assertEquals(1, t.almacen.revisionHumana.size());
        String caso = t.almacen.revisionHumana.get(0);
        assertTrue(caso.contains("orden=liberar-inventario") && caso.contains("intentos=5") && caso.contains("error=liberar-fallo"), caso);
    }

    @Test
    void sinAislamientoHayDobleVentaYConLasDosContramedidasNunca() {
        int dobles = 0;
        for (long semilla = 1; semilla <= 100; semilla++) {
            if (Aislamiento.vender(1, 20, Aislamiento.Estrategia.INGENUA, semilla).dobleVenta(1)) {
                dobles++;
            }
            for (Aislamiento.Estrategia e : List.of(Aislamiento.Estrategia.BLOQUEO_SEMANTICO, Aislamiento.Estrategia.VERSION)) {
                Aislamiento.Resultado r = Aislamiento.vender(1, 20, e, semilla);
                assertFalse(r.dobleVenta(1), e + " semilla " + semilla + ": " + r);
                assertEquals(1, r.vendidas(), e + " semilla " + semilla);
            }
        }
        assertTrue(dobles > 0, "la versión ingenua vende de más en algún intercalado");
    }

    @Test
    void elInformeSucioCuentaUnaVentaQueNoOcurre() {
        Tienda i = new Tienda(5, new Broker());
        i.envios.sinCobertura.add("s");
        i.comprar("s", 100_000);
        i.correr(2);
        assertEquals(1, i.pagos.cobros.size(), "lectura sucia: cuenta el cobro de una saga en curso");
        assertEquals(0, Escenarios.ventasConfirmadas(i), "el informe corregido solo cuenta sagas completadas");
        i.correr(10);
        assertEquals(Estado.CANCELADA, i.estado("s"));
        assertEquals(0, i.pagos.cobros.size());
    }

    /** Paso 9: cada camino × cada tipo de fallo × cada posición de mensaje. La invariante se cumple siempre. */
    @Test
    void laInvarianteSeCumpleConUnFalloEnCadaPaso() {
        List<String> violaciones = new ArrayList<>();
        int combinaciones = 0;
        String[] caminos = {"exito", "rechazo", "envio", "agotado"};
        for (String camino : caminos) {
            for (Accion accion : Accion.values()) {
                for (int n = 1; n <= 30; n++) {
                    Broker b = new Broker().con(n, accion);
                    int stock = camino.equals("agotado") ? 0 : 3;
                    Tienda t = new Tienda(stock, b);
                    t.envios.sinCobertura.add("envio");
                    t.comprar(camino, camino.equals("rechazo") ? 5_000_000 : 100_000);
                    t.correr(200);
                    combinaciones++;
                    String r = t.invariante(stock);
                    if (!r.isEmpty()) {
                        violaciones.add(camino + " " + accion + " #" + n + ": " + r);
                    }
                }
            }
        }
        assertEquals(List.of(), violaciones);
        assertEquals(4 * 4 * 30, combinaciones);
    }
}
