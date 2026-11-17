package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SagasTest {

    static final int STOCK = 100;
    static final String CONTENEDOR = "pub-kafka-y-sagas-kafka-1";
    Mundo mundo;

    @AfterEach
    void cierre() throws Exception {
        if (mundo != null) mundo.close();
        Kf.docker("start", CONTENEDOR);
    }

    static void iniciar(Orquestador o, String id, String camino) {
        switch (camino) {
            case "feliz" -> o.iniciar(id, 1000, "libro", 1, "Quito");
            case "pago-rechazado" -> o.iniciar(id, Pagos.IMPORTE_RECHAZADO, "libro", 1, "Quito");
            case "sin-existencias" -> o.iniciar(id, 1000, "raro", 1, "Quito");
            case "envio-imposible" -> o.iniciar(id, 1000, "libro", 1, "Atlantis");
            default -> throw new IllegalArgumentException(camino);
        }
    }

    static final List<String> CAMINOS = List.of("feliz", "pago-rechazado", "sin-existencias", "envio-imposible");

    // ---------------------------------------------------------------- paso 5 y 10: los cuatro caminos
    @Test
    @Order(1)
    void paso5_loscuatroCaminosYElEstadoEnLasBases() throws Exception {
        mundo = new Mundo(STOCK);
        mundo.arrancarTodos();
        Orquestador o = mundo.cliente();
        for (String c : CAMINOS) for (int i = 0; i < 3; i++) iniciar(o, c + "-" + i, c);
        mundo.esperarTerminadas(120_000);
        Map<String, Long> estados = o.sagasPorEstado();
        Ev.nota("5. 12 sagas (4 caminos x 3): estados en la base del orquestador " + estados);
        for (String c : CAMINOS) {
            String estado = mundo.bd("orquestador").queryForObject("SELECT estado FROM sagas WHERE id = ?", String.class, c + "-0");
            String motivo = mundo.bd("orquestador").queryForObject("SELECT coalesce(motivo, '-') FROM sagas WHERE id = ?", String.class, c + "-0");
            Ev.nota("5.   " + c + ": " + estado + " (" + motivo + "); cobros vigentes " + mundo.n("pagos", "SELECT count(*) FROM cobros WHERE saga_id = '" + c + "-0' AND NOT reembolsado")
                    + ", reservas vigentes " + mundo.n("inventario", "SELECT count(*) FROM reservas WHERE saga_id = '" + c + "-0' AND NOT liberada")
                    + ", despachos " + mundo.n("envios", "SELECT count(*) FROM despachos WHERE saga_id = '" + c + "-0'"));
        }
        assertEquals(Map.of("COMPLETADA", 3L, "CANCELADA", 9L), estados);
        assertEquals(3, mundo.n("pagos", "SELECT count(*) FROM cobros WHERE NOT reembolsado"));
        assertEquals(List.of(), mundo.violaciones(STOCK));
        assertEquals(0, o.noTerminadas().size());
    }

    // ---------------------------------------------------------------- paso 6: caídas
    private void cargaYCaidas(String quien, String propiedadDeMuerte) throws Exception {
        mundo = new Mundo(STOCK);
        mundo.arrancarTodos();
        Orquestador o = mundo.cliente();
        for (int i = 0; i < 24; i++) iniciar(o, CAMINOS.get(i % 4) + "-" + i, CAMINOS.get(i % 4));
        Thread.sleep(1500);
        // se mata el proceso en pleno trabajo (kill -9) y se reinicia
        int idx = Mundo.SERVICIOS.indexOf(quien);
        Process victima = mundo.procesos.get(idx);
        Mundo.matar(victima);
        Thread.sleep(2000);
        mundo.arrancar(quien);
        Mundo.esperarGrupo(quien, 1);
        mundo.esperarTerminadas(180_000);
        List<String> v = mundo.violaciones(STOCK);
        Ev.nota("6. kill -9 al " + quien + " con 24 sagas en vuelo: sagas no terminadas tras reiniciar 0; cobros " + mundo.n("pagos", "SELECT count(*) FROM cobros") + " (uno por saga a lo sumo); violaciones " + v);
        assertEquals(List.of(), v);
        assertEquals(0, o.noTerminadas().size());
    }

    @Test
    @Order(2)
    void paso6_caeElOrquestador() throws Exception {
        cargaYCaidas("orquestador", null);
    }

    @Test
    @Order(3)
    void paso6_caeUnParticipante() throws Exception {
        cargaYCaidas("pagos", null);
    }

    @Test
    @Order(4)
    void paso6_caeUnParticipanteJustoDespuesDeProcesarYAntesDeConfirmar() throws Exception {
        mundo = new Mundo(STOCK);
        for (String s : List.of("orquestador", "inventario", "envios")) mundo.arrancar(s);
        Process pagos = mundo.arrancar("pagos", "--tienda.muere-tras=3");     // muere tras procesar la tercera orden, antes de confirmar su posición
        for (String g : Mundo.SERVICIOS) Mundo.esperarGrupo(g, 1);
        Orquestador o = mundo.cliente();
        for (int i = 0; i < 12; i++) o.iniciar("feliz-" + i, 1000, "libro", 1, "Quito");
        assertTrue(pagos.waitFor(120, TimeUnit.SECONDS), "debía morir");
        assertEquals(137, pagos.exitValue());
        mundo.arrancar("pagos");
        Mundo.esperarGrupo("pagos", 1);
        mundo.esperarTerminadas(180_000);
        long cobros = mundo.n("pagos", "SELECT count(*) FROM cobros");
        Ev.nota("6. Pagos muerto tras procesar y antes de confirmar la posición: 12 sagas terminan, cobros " + cobros + " (la orden repetida se reconoció y no cobró otra vez); violaciones " + mundo.violaciones(STOCK));
        assertEquals(12, cobros);
        assertEquals(List.of(), mundo.violaciones(STOCK));
    }

    @Test
    @Order(5)
    void paso6_caeElBroker() throws Exception {
        mundo = new Mundo(STOCK);
        mundo.arrancarTodos();
        Orquestador o = mundo.cliente();
        for (int i = 0; i < 12; i++) iniciar(o, CAMINOS.get(i % 4) + "-" + i, CAMINOS.get(i % 4));
        Thread.sleep(1200);
        Kf.docker("stop", CONTENEDOR);
        for (int i = 12; i < 24; i++) iniciar(o, CAMINOS.get(i % 4) + "-" + i, CAMINOS.get(i % 4));     // se siguen aceptando: la tabla de salida los retiene
        Thread.sleep(8000);
        long pendientesSinBroker = mundo.n("orquestador", "SELECT count(*) FROM salida WHERE publicada IS NULL");
        Kf.docker("start", CONTENEDOR);
        mundo.esperarTerminadas(240_000);
        Ev.nota("6. broker detenido 8 s con sagas en vuelo: " + pendientesSinBroker + " órdenes esperaban en la tabla de salida; al volver, todas terminan; violaciones " + mundo.violaciones(STOCK));
        assertTrue(pendientesSinBroker > 0);
        assertEquals(List.of(), mundo.violaciones(STOCK));
    }

    // ---------------------------------------------------------------- paso 7: dos instancias y conflicto de versión
    @Test
    @Order(6)
    void paso7_dosOrquestadores() throws Exception {
        mundo = new Mundo(STOCK);
        mundo.arrancarTodos();
        mundo.arrancar("orquestador");                                   // segunda instancia, mismo grupo
        Mundo.esperarGrupo("orquestador", 2);
        Orquestador o = mundo.cliente();
        for (int i = 0; i < 40; i++) iniciar(o, CAMINOS.get(i % 4) + "-" + i, CAMINOS.get(i % 4));
        mundo.esperarTerminadas(180_000);
        long versionMaxima = mundo.n("orquestador", "SELECT max(version) FROM sagas");
        Ev.nota("7. dos instancias del orquestador: 40 sagas terminadas; versión máxima de una saga " + versionMaxima + " (3 a 6 transiciones, ninguna repetida); violaciones " + mundo.violaciones(STOCK));
        assertEquals(List.of(), mundo.violaciones(STOCK));
        assertTrue(versionMaxima <= 6);
    }

    @Test
    @Order(7)
    void paso7_conflictoDeVersionForzado() throws Exception {
        mundo = new Mundo(STOCK);
        Orquestador a = mundo.cliente();
        a.iniciar("s-1", 1000, "libro", 1, "Quito");
        // dos respuestas DISTINTAS llegan a la vez a dos instancias y las dos leen la misma versión: solo una puede escribir
        Orquestador b = mundo.cliente();
        CountDownLatch ambasLeyeron = new CountDownLatch(2);
        Runnable esperar = () -> {
            ambasLeyeron.countDown();
            try { ambasLeyeron.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        a.antesDeGuardar = esperar;
        b.antesDeGuardar = esperar;
        Thread t1 = new Thread(() -> a.atender(new Mensajes.Respuesta("s-1/reservar-inventario->inventario-reservado", "s-1", "s-1", "inventario-reservado")));
        Thread t2 = new Thread(() -> b.atender(new Mensajes.Respuesta("s-1/reservar-inventario->inventario-reservado#copia", "s-1", "s-1", "inventario-reservado")));   // otro id: no es un duplicado del mismo mensaje
        t1.start(); t2.start(); t1.join(); t2.join();
        long version = mundo.n("orquestador", "SELECT version FROM sagas WHERE id = 's-1'");
        String estado = mundo.bd("orquestador").queryForObject("SELECT estado FROM sagas WHERE id = 's-1'", String.class);
        Ev.nota("7. conflicto de versión forzado: conflictos detectados " + (a.conflictos.get() + b.conflictos.get()) + "; la saga avanzó una vez (versión " + version + ", estado " + estado + "); la perdedora releyó y encontró la respuesta fuera de lugar");
        assertEquals(1, a.conflictos.get() + b.conflictos.get());
        assertEquals(1, version);
        assertEquals("COBRANDO", estado);
        assertEquals(1, mundo.n("orquestador", "SELECT count(*) FROM salida WHERE tema = 'compra.ordenes.pagos'"), "una sola orden de cobro");
    }

    // ---------------------------------------------------------------- paso 8: tiempo límite
    @Test
    @Order(8)
    void paso8_inventarioCaidoReintentaCompensaYAlVolverQuedaConsistente() throws Exception {
        mundo = new Mundo(STOCK);
        for (String s : List.of("orquestador", "pagos", "envios")) mundo.arrancar(s, "--tienda.espera-ms=2000");
        for (String g : List.of("orquestador", "pagos", "envios")) Mundo.esperarGrupo(g, 1);
        Orquestador o = mundo.cliente();
        for (int i = 0; i < 6; i++) o.iniciar("c-" + i, 1000, "libro", 1, "Quito");     // Inventario está caído: las órdenes esperan en el tema
        Kf.esperar(() -> mundo.n("orquestador", "SELECT count(*) FROM sagas WHERE estado = 'LIBERANDO'") == 6, 90_000, "las 6 sagas vencen y pasan a compensar");
        Ev.nota("8. Inventario detenido: tras reintentar, las 6 sagas vencieron y pasaron a LIBERANDO (intentos y motivo: "
                + mundo.bd("orquestador").queryForList("SELECT intentos, motivo FROM sagas ORDER BY id LIMIT 1") + ")");
        mundo.arrancar("inventario");                                    // vuelve: encuentra en el tema las órdenes de reservar acumuladas y luego las de liberar
        Mundo.esperarGrupo("inventario", 1);
        mundo.esperarTerminadas(120_000);
        long reservasVigentes = mundo.n("inventario", "SELECT count(*) FROM reservas WHERE NOT liberada");
        long libres = mundo.n("inventario", "SELECT disponibles FROM stock WHERE producto = 'libro'");
        Ev.nota("8. Inventario de vuelta: las 6 sagas CANCELADAS, reservas vigentes " + reservasVigentes + ", existencias " + libres + " de " + STOCK + "; violaciones " + mundo.violaciones(STOCK));
        assertEquals(6, mundo.n("orquestador", "SELECT count(*) FROM sagas WHERE estado = 'CANCELADA'"));
        assertEquals(0, reservasVigentes);
        assertEquals(STOCK, libres);
        assertEquals(List.of(), mundo.violaciones(STOCK));
    }

    // ---------------------------------------------------------------- paso 9: mensaje defectuoso
    @Test
    @Order(9)
    void paso9_respuestaIlegibleNoBloqueaLasDemasSagas() throws Exception {
        mundo = new Mundo(STOCK);
        mundo.arrancarTodos();
        Orquestador o = mundo.cliente();
        try (var p = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(Map.of("bootstrap.servers", Mundo.B,
                "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName(), "value.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName()))) {
            for (int part = 0; part < 6; part++) p.send(new org.apache.kafka.clients.producer.ProducerRecord<>(Mensajes.RESPUESTAS, "basura-" + part, "{ esto no es una respuesta"));
            p.flush();
        }
        for (int i = 0; i < 12; i++) iniciar(o, CAMINOS.get(i % 4) + "-" + i, CAMINOS.get(i % 4));
        mundo.esperarTerminadas(120_000);
        Kf.esperar(() -> Kf.contar(Mensajes.FALLIDOS) == 6, 60_000, "las 6 basuras en fallidos");
        Ev.nota("9. seis respuestas ilegibles publicadas a mano (en las tres particiones) en compra.respuestas: " + Kf.contar(Mensajes.FALLIDOS) + " en compra.fallidos; las 12 sagas terminaron; violaciones " + mundo.violaciones(STOCK));
        assertEquals(List.of(), mundo.violaciones(STOCK));
    }

    // ---------------------------------------------------------------- paso 11: métricas y alertas
    @Test
    @Order(10)
    void paso11_metricasDeLaTablaDeObservabilidad() throws Exception {
        mundo = new Mundo(STOCK);
        Orquestador o = mundo.cliente();                                 // sin servicios arriba: nada avanza
        for (int i = 0; i < 5; i++) o.iniciar("m-" + i, 1000, "libro", 1, "Quito");
        Thread.sleep(2500);
        Ev.nota("11. métricas con los servicios detenidos: sagas por estado " + o.sagasPorEstado() + ", sagas vencidas (>2 s sin cambio) " + o.vencidas(2) + ", casos en revisión " + o.enRevision()
                + ", filas pendientes en la tabla de salida " + mundo.n("orquestador", "SELECT count(*) FROM salida WHERE publicada IS NULL"));
        assertEquals(Map.of("RESERVANDO", 5L), o.sagasPorEstado());
        assertEquals(5, o.vencidas(2));
    }

    // ---------------------------------------------------------------- paso 6, el peor momento: morir entre publicar y marcar
    @Test
    @Order(11)
    void paso6_muereElPublicadorEntrePublicarYMarcar() throws Exception {
        mundo = new Mundo(STOCK);
        for (String s : Mundo.SERVICIOS) {
            if (s.equals("orquestador")) mundo.arrancar(s, "--tienda.muere-al-publicar=3");        // publica la 3.ª fila y muere ANTES de marcarla
            else mundo.arrancar(s);
        }
        for (String g : Mundo.SERVICIOS) Mundo.esperarGrupo(g, 1);
        Orquestador o = mundo.cliente();
        for (int i = 0; i < 8; i++) iniciar(o, "mp-" + i, "feliz");
        Process orquestador = mundo.procesos.get(0);
        assertTrue(orquestador.waitFor(90, TimeUnit.SECONDS), "el orquestador debía morir al publicar");
        assertEquals(137, orquestador.exitValue());
        long sinMarcar = mundo.n("orquestador", "SELECT count(*) FROM salida WHERE publicada IS NULL");
        mundo.arrancar("orquestador");                                                            // reinicio normal: republica la fila que ya había salido
        Mundo.esperarGrupo("orquestador", 1);
        mundo.esperarTerminadas(180_000);
        long cobros = mundo.n("pagos", "SELECT count(*) FROM cobros"), doble = mundo.n("pagos", "SELECT count(*) FROM (SELECT saga_id FROM cobros GROUP BY saga_id HAVING count(*) > 1) x");
        long completadas = mundo.n("orquestador", "SELECT count(*) FROM sagas WHERE estado = 'COMPLETADA'");
        long mensajes = 0;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String tema : List.of(Mensajes.ORDENES_INVENTARIO, Mensajes.ORDENES_PAGOS, Mensajes.ORDENES_ENVIOS)) {
            for (var r : Kf.leerTodo(Mundo.B, tema, "read_committed")) {
                mensajes++;
                ids.add(r.value().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
            }
        }
        long versiones = mundo.n("orquestador", "SELECT sum(version) FROM sagas");
        Ev.nota("6c. el orquestador muere tras publicar la 3.ª fila y antes de marcarla (" + sinMarcar + " filas sin marcar al morir); al reiniciar se republica: " + mensajes + " órdenes en los temas para "
                + ids.size() + " ids distintos (duplicados " + (mensajes - ids.size()) + "); sagas completadas " + completadas + " de 8, cobros " + cobros + " (dobles " + doble + "), suma de versiones " + versiones + " (8 sagas x 3 pasos = 24)");
        assertTrue(mensajes > ids.size(), "la fila publicada y sin marcar salió dos veces: es el al-menos-una-vez");
        assertEquals(8, completadas);
        assertEquals(8, cobros);
        assertEquals(0, doble);
        assertEquals(24, versiones, "el duplicado no avanzó ninguna saga dos veces");
        assertEquals(List.of(), mundo.violaciones(STOCK));
    }
}
