package tienda.integrador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** El simulacro de la sesión 07: cada prueba termina con el verificador de invariantes. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimulacroTest {

    static final String CONTENEDOR = "pub-kafka-y-sagas-kafka-1";
    static final List<String> CAMINOS = List.of("feliz", "pago-rechazado", "sin-existencias", "envio-imposible");
    static final int P = 47601;
    Mundo7 mundo;

    @AfterEach
    void cierre() throws Exception {
        if (mundo != null) mundo.close();
        Kf.docker("start", CONTENEDOR);
    }

    /** Crea n pedidos mezclando los cuatro caminos; devuelve cuántos respondieron 201. */
    int carga(String prefijo, int n) {
        int creados = 0;
        for (int i = 0; i < n; i++) if (Mundo7.pedir(P, prefijo + "-" + i, CAMINOS.get(i % 4)) == 201) creados++;
        return creados;
    }

    void invariantes(String prueba) {
        List<String> v = mundo.verificar();
        Ev.nota("   verificador tras '" + prueba + "': " + (v.isEmpty() ? "sin violaciones" : v.toString()));
        assertEquals(List.of(), v);
    }

    static KafkaProducer<String, String> productor() {
        return new KafkaProducer<>(Map.of("bootstrap.servers", Mundo7.B, "key.serializer", StringSerializer.class.getName(), "value.serializer", StringSerializer.class.getName()));
    }

    // ------------------------------------------------------------------ los cuatro caminos, HTTP, notificaciones, puntos, correlación
    @Test
    @Order(1)
    void caminosFuncionalesYCorrelacion() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos();
        assertEquals(201, Mundo7.pedir(P, "corr-feliz", "feliz"));
        String respuesta = Mundo7.cuerpo("GET", P, "/pedidos/corr-feliz");
        Ev.nota("F1/F2. GET /pedidos/corr-feliz justo tras el POST: " + respuesta);
        for (int i = 1; i < 12; i++) assertEquals(201, Mundo7.pedir(P, "c-" + i, CAMINOS.get(i % 4)));
        // la correlación explícita en la cabecera HTTP
        assertEquals(201, Mundo7.codigo("POST", P, "/pedidos?id=con-cabecera&producto=libro&cantidad=1&centavos=1000&ciudad=Quito", "CORR-777"));
        mundo.esperarTerminados(120_000);
        for (String c : CAMINOS) {
            int i = CAMINOS.indexOf(c) == 0 ? 4 : CAMINOS.indexOf(c);       // c-4 es feliz (4 % 4 = 0), c-1, c-2, c-3 los otros
            String id = "c-" + i;
            Ev.nota("F3/F4. " + c + ": GET /pedidos/" + id + " -> " + Mundo7.cuerpo("GET", P, "/pedidos/" + id));
        }
        long confirmados = mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado = 'CONFIRMADO'");
        long cancelados = mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado = 'CANCELADO'");
        assertEquals("CONFIRMADO", mundo.bd.get("pedidos").queryForObject("SELECT estado FROM pedidos WHERE id = 'corr-feliz'", String.class));
        assertEquals("CANCELADO", mundo.bd.get("pedidos").queryForObject("SELECT estado FROM pedidos WHERE id = 'c-1'", String.class));
        assertTrue(mundo.bd.get("pedidos").queryForObject("SELECT motivo FROM pedidos WHERE id = 'c-1'", String.class).length() > 0, "un pedido cancelado explica el motivo");
        Kf.esperar(() -> mundo.n("notificaciones", "SELECT count(*) FROM notificaciones") == confirmados + cancelados
                && mundo.n("fidelidad", "SELECT count(*) FROM puntos") == confirmados, 30_000, "notificaciones y puntos");
        Ev.nota("F5. 13 pedidos: " + confirmados + " confirmados, " + cancelados + " cancelados; " + mundo.n("notificaciones", "SELECT count(*) FROM notificaciones") + " notificaciones, "
                + mundo.n("fidelidad", "SELECT count(*) FROM puntos") + " registros de puntos (solo confirmados)");
        // correlación: la cabecera CORR-777 aparece en el registro de los siete servicios
        StringBuilder donde = new StringBuilder();
        for (String s : Mundo7.SERVICIOS) {
            boolean esta = mundo.salidas.get(s).stream().anyMatch(b -> b.toString().contains("correlacion=CORR-777"));
            donde.append(s).append(esta ? " si, " : " NO, ");
            assertTrue(esta, "correlacion=CORR-777 no aparece en el registro de " + s);
        }
        Ev.nota("F/operación. correlacion=CORR-777 (cabecera HTTP) aparece en el registro de: " + donde);
        invariantes("caminos");
    }

    // ------------------------------------------------------------------ el simulacro, una fila por prueba
    @Test
    @Order(2)
    void matarElOrquestador() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos();
        carga("o1", 20);
        Thread.sleep(800);
        mundo.matar("orquestador");
        int duranteLaCaida = carga("o2", 20);
        Thread.sleep(2000);
        mundo.arrancar("orquestador");
        mundo.esperarListos(1);
        mundo.esperarTerminados(180_000);
        Ev.nota("S1. kill -9 al orquestador con carga continua: " + duranteLaCaida + "/20 POST aceptados mientras estaba caído; tras reiniciar, 0 pedidos PENDIENTES");
        assertEquals(20, duranteLaCaida);
        invariantes("matar el orquestador");
    }

    @Test
    @Order(3)
    void matarPagosDosMinutos() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos("--tienda.espera-ms=3000");
        carga("p1", 20);
        mundo.matar("pagos");
        carga("p2", 20);
        Thread.sleep(120_000);
        long cobrosSinPagos = mundo.n("pagos", "SELECT count(*) FROM cobros");
        mundo.arrancar("pagos", "--tienda.espera-ms=3000");
        mundo.esperarListos(1);
        mundo.esperarTerminados(240_000);
        long doble = mundo.n("pagos", "SELECT count(*) FROM (SELECT saga_id FROM cobros GROUP BY saga_id HAVING count(*) > 1) x");
        Ev.nota("S2. Pagos muerto 120 s con carga: las sagas esperaron, reintentaron y terminaron (confirmados " + mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado='CONFIRMADO'")
                + ", cancelados " + mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado='CANCELADO'") + "); cobros dobles " + doble);
        assertEquals(0, doble);
        invariantes("matar Pagos dos minutos");
    }

    @Test
    @Order(4)
    void matarElBrokerUnMinuto() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos();
        carga("b1", 12);
        Thread.sleep(1000);
        Kf.docker("stop", CONTENEDOR);
        long t0 = System.currentTimeMillis();
        int aceptados = 0, total = 0;
        while (System.currentTimeMillis() - t0 < 60_000) {
            total++;
            if (Mundo7.pedir(P, "b2-" + total, CAMINOS.get(total % 4)) == 201) aceptados++;
            Thread.sleep(1500);
        }
        long pendientesSalida = mundo.n("pedidos", "SELECT count(*) FROM salida WHERE publicada IS NULL");
        Kf.docker("start", CONTENEDOR);
        mundo.esperarTerminados(300_000);
        Ev.nota("S3. broker detenido 60 s: POST /pedidos respondió 201 en " + aceptados + " de " + total + " intentos; " + pendientesSalida + " eventos esperaban en la tabla de salida de Pedidos; al volver, todo terminó");
        assertEquals(total, aceptados);
        assertTrue(pendientesSalida > 0);
        invariantes("matar el broker");
    }

    @Test
    @Order(5)
    void detenerElPublicadorDePedidos() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos();
        assertEquals(200, Mundo7.codigo("POST", P, "/admin/publicador/detener", null));
        int aceptados = carga("pub", 12);
        Thread.sleep(5000);
        long pendientes = mundo.n("pedidos", "SELECT count(*) FROM salida WHERE publicada IS NULL");
        long aunPendientes = mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado = 'PENDIENTE'");
        assertEquals(200, Mundo7.codigo("POST", P, "/admin/publicador/iniciar", null));
        mundo.esperarTerminados(120_000);
        Ev.nota("S4. publicador de Pedidos detenido: " + aceptados + "/12 pedidos aceptados, " + pendientes + " eventos en la tabla de salida y " + aunPendientes + " pedidos PENDIENTES; al reiniciarlo, todos terminan");
        assertEquals(12, aceptados);
        assertEquals(12, pendientes);
        assertEquals(12, aunPendientes);
        invariantes("detener el publicador");
    }

    @Test
    @Order(6)
    void duplicarCienMensajes() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos();
        carga("d", 48);
        mundo.esperarTerminados(120_000);
        Kf.esperar(() -> mundo.n("notificaciones", "SELECT count(*) FROM notificaciones") == 48, 30_000, "notificaciones");
        long cobros = mundo.n("pagos", "SELECT count(*) FROM cobros"), reservas = mundo.n("inventario", "SELECT count(*) FROM reservas"), despachos = mundo.n("envios", "SELECT count(*) FROM despachos");
        long puntos = mundo.n("fidelidad", "SELECT count(*) FROM puntos"), notifs = mundo.n("notificaciones", "SELECT count(*) FROM notificaciones");
        long versiones = mundo.n("orquestador", "SELECT sum(version) FROM sagas");
        int copias = 0;
        try (var p = productor()) {
            for (String tema : List.of(Temas.PEDIDOS, Temas.ORDENES_PAGOS, Temas.ORDENES_INVENTARIO, Temas.ORDENES_ENVIOS, Temas.RESPUESTAS, Temas.RESULTADOS)) {
                int deTema = 0;
                for (ConsumerRecord<String, String> r : Kf.leerTodo(tema)) {
                    if (deTema++ >= 20) break;                       // 6 temas x 20 = 120 copias
                    p.send(new ProducerRecord<>(tema, r.key(), r.value()));
                    copias++;
                }
            }
            p.flush();
        }
        Thread.sleep(15_000);
        mundo.esperarTerminados(60_000);
        Ev.nota("S5. " + copias + " mensajes duplicados a mano (copias exactas en 6 temas): cobros " + cobros + " -> " + mundo.n("pagos", "SELECT count(*) FROM cobros")
                + ", reservas " + reservas + " -> " + mundo.n("inventario", "SELECT count(*) FROM reservas") + ", despachos " + despachos + " -> " + mundo.n("envios", "SELECT count(*) FROM despachos")
                + ", puntos " + puntos + " -> " + mundo.n("fidelidad", "SELECT count(*) FROM puntos") + ", notificaciones " + notifs + " -> " + mundo.n("notificaciones", "SELECT count(*) FROM notificaciones")
                + ", suma de versiones de las sagas " + versiones + " -> " + mundo.n("orquestador", "SELECT sum(version) FROM sagas"));
        assertTrue(copias >= 100);
        assertEquals(cobros, mundo.n("pagos", "SELECT count(*) FROM cobros"));
        assertEquals(reservas, mundo.n("inventario", "SELECT count(*) FROM reservas"));
        assertEquals(despachos, mundo.n("envios", "SELECT count(*) FROM despachos"));
        assertEquals(puntos, mundo.n("fidelidad", "SELECT count(*) FROM puntos"));
        assertEquals(notifs, mundo.n("notificaciones", "SELECT count(*) FROM notificaciones"));
        assertEquals(versiones, mundo.n("orquestador", "SELECT sum(version) FROM sagas"));
        invariantes("duplicar cien mensajes");
    }

    @Test
    @Order(7)
    void mensajeIlegibleEnCadaTema() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos();
        List<String> temas = List.of(Temas.PEDIDOS, Temas.ORDENES_PAGOS, Temas.ORDENES_INVENTARIO, Temas.ORDENES_ENVIOS, Temas.RESPUESTAS, Temas.RESULTADOS);
        try (var p = productor()) {
            for (String t : temas) p.send(new ProducerRecord<>(t, "basura", "{ esto no es JSON"));
            p.flush();
        }
        carga("i", 16);
        mundo.esperarTerminados(120_000);
        int esperados = 8;                  // resultados lo leen tres grupos (pedidos, notificaciones, fidelidad); los otros cinco temas, uno
        Kf.esperar(() -> Kf.contar(Temas.FALLIDOS) == esperados, 60_000, "los ilegibles en compra.fallidos");
        Ev.nota("S6. un mensaje ilegible en cada uno de 6 temas: " + Kf.contar(Temas.FALLIDOS) + " registros en compra.fallidos (resultados lo leen 3 grupos); los 16 pedidos terminaron");
        invariantes("mensaje ilegible en cada tema");
    }

    @Test
    @Order(8)
    void dosInstanciasDeCadaServicio() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos();
        for (String s : Mundo7.SERVICIOS) mundo.arrancar(s);
        mundo.esperarListos(2);
        carga("x", 60);
        mundo.esperarTerminados(180_000);
        Kf.esperar(() -> mundo.n("notificaciones", "SELECT count(*) FROM notificaciones") == 60, 30_000, "notificaciones");
        long confirmados = mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado='CONFIRMADO'");
        Ev.nota("S7. dos instancias de cada uno de los siete servicios (14 procesos): 60 pedidos, " + confirmados + " confirmados (15 esperados: uno de cada cuatro), 60 notificaciones, "
                + mundo.n("fidelidad", "SELECT count(*) FROM puntos") + " registros de puntos");
        assertEquals(15, confirmados);
        assertEquals(15, mundo.n("fidelidad", "SELECT count(*) FROM puntos"));
        invariantes("dos instancias de cada servicio");
    }

    @Test
    @Order(9)
    void ventaDeCincoUnidadesAVeinteCompradores() throws Exception {
        mundo = new Mundo7(1000, 10);
        mundo.arrancarTodos();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        StringBuilder res = new StringBuilder();
        for (int k = 0; k < 10; k++) {
            String producto = "lim-" + k;
            List<Future<Integer>> fs = new ArrayList<>();
            for (int c = 0; c < 20; c++) {
                int comprador = c;
                fs.add(pool.submit(() -> Mundo7.pedir(P, producto + "-c" + comprador, producto)));
            }
            for (Future<Integer> f : fs) assertEquals(201, f.get());
            Kf.esperar(() -> mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado = 'PENDIENTE'") == 0, 120_000, "ronda " + producto);
            long confirmados = mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE id LIKE '" + producto + "-c%' AND estado = 'CONFIRMADO'");
            res.append(confirmados).append(' ');
            assertEquals(5, confirmados, "ronda " + k);
        }
        pool.shutdown();
        Ev.nota("S8. cinco unidades, veinte compradores a la vez, diez rondas (diez productos): confirmados por ronda = " + res + "; cancelados por falta de existencias en cada una: 15");
        assertEquals(0, mundo.n("inventario", "SELECT count(*) FROM stock WHERE disponibles <> 0 AND producto LIKE 'lim-%'"));
        invariantes("venta de cinco unidades");
    }

    @Test
    @Order(10)
    void compensacionDeInventarioFallaSiempre() throws Exception {
        mundo = new Mundo7(1000, 0);
        for (String s : Mundo7.SERVICIOS) {
            if (s.equals("inventario")) mundo.arrancar(s, "--tienda.falla-compensacion=true");
            else if (s.equals("orquestador")) mundo.arrancar(s, "--tienda.espera-ms=2000", "--tienda.intentos-de-compensacion=3");
            else mundo.arrancar(s);
        }
        mundo.esperarListos(1);
        for (int i = 0; i < 4; i++) assertEquals(201, Mundo7.pedir(P, "rev-" + i, "pago-rechazado"));         // reserva ok, pago rechazado, la liberación falla siempre
        Kf.esperar(() -> mundo.n("orquestador", "SELECT count(*) FROM sagas WHERE revision") == 4, 120_000, "4 casos en revisión");
        String metricas = Mundo7.cuerpo("GET", 47611, "/metricas");
        Ev.nota("S9. la compensación de Inventario falla siempre: tras 3 intentos los 4 casos pasan a revisión humana. /metricas del orquestador:\n" + metricas.lines().filter(l -> l.startsWith("casos_en_revision") || l.contains("LIBERANDO") || l.startsWith("sagas_vencidas")).reduce("", (a, b) -> a + "     " + b + "\n"));
        assertTrue(metricas.contains("casos_en_revision 4"));
        Ev.nota("   lista de no terminadas: " + Mundo7.cuerpo("GET", 47611, "/sagas/no-terminadas"));
        assertEquals(4, mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado = 'PENDIENTE'"));    // honesto: el pedido no se declara cancelado mientras la reserva sigue viva
        invariantes("compensación de Inventario rota");
    }

    // ---- Matar de verdad el proceso (no pausar el publicador) ----

    @Test
    @Order(11)
    void matarElProcesoDePedidosConFilasPendientes() throws Exception {
        mundo = new Mundo7(1000, 0);
        mundo.arrancarTodos();
        assertEquals(200, Mundo7.codigo("POST", P, "/admin/publicador/detener", null));        // las filas se quedan en la tabla de salida
        int aceptados = carga("kp", 12);
        long antes = mundo.n("pedidos", "SELECT count(*) FROM salida WHERE publicada IS NULL");
        mundo.matar("pedidos");                                                               // kill -9 del proceso, con 12 pedidos aceptados y 12 filas pendientes
        long trasLaMuerte = mundo.n("pedidos", "SELECT count(*) FROM salida WHERE publicada IS NULL");
        assertEquals(0, Mundo7.codigo("GET", P, "/metricas", null) == -1 ? 0 : 1, "el proceso de Pedidos está muerto");
        mundo.arrancar("pedidos");
        Kf.esperar(() -> Mundo7.codigo("GET", P, "/metricas", null) == 200, 60_000, "Pedidos de vuelta");
        mundo.esperarTerminados(180_000);
        Ev.nota("S4b. kill -9 al proceso de Pedidos con " + aceptados + " pedidos aceptados y " + antes + " filas pendientes (quedaron " + trasLaMuerte + " tras la muerte); al reiniciar salieron todas y los 12 pedidos terminaron");
        assertEquals(12, aceptados);
        assertEquals(12, antes);
        assertEquals(12, trasLaMuerte);
        invariantes("matar el proceso de Pedidos con filas pendientes");
    }

    @Test
    @Order(12)
    void muereElPublicadorEntrePublicarYMarcar() throws Exception {
        mundo = new Mundo7(1000, 0);
        for (String s : Mundo7.SERVICIOS) {
            if (s.equals("orquestador")) mundo.arrancar(s, "--tienda.muere-al-publicar=3");        // publica la 3.ª fila y muere ANTES de marcarla
            else mundo.arrancar(s);
        }
        mundo.esperarListos(1);
        carga("mp", 8);
        Process muerto = mundo.procesos.get("orquestador").get(0);
        assertTrue(muerto.waitFor(90, TimeUnit.SECONDS), "el orquestador debía morir al publicar");
        assertEquals(137, muerto.exitValue());
        long sinMarcar = mundo.n("orquestador", "SELECT count(*) FROM salida WHERE publicada IS NULL");
        mundo.matar("orquestador");
        mundo.arrancar("orquestador");                                                        // reinicio normal: vuelve a publicar la fila que ya había salido
        mundo.esperarListos(1);
        mundo.esperarTerminados(180_000);
        long confirmados = mundo.n("pedidos", "SELECT count(*) FROM pedidos WHERE estado = 'CONFIRMADO'");
        long cobros = mundo.n("pagos", "SELECT count(*) FROM cobros"), doble = mundo.n("pagos", "SELECT count(*) FROM (SELECT saga_id FROM cobros GROUP BY saga_id HAVING count(*) > 1) x");
        long mensajes = 0;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String tema : List.of(Temas.ORDENES_INVENTARIO, Temas.ORDENES_PAGOS, Temas.ORDENES_ENVIOS)) {
            for (ConsumerRecord<String, String> r : Kf.leerTodo(Kf.bootstrap(), tema, "read_committed")) {
                mensajes++;
                ids.add(r.value().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
            }
        }
        Ev.nota("S4c. el orquestador muere tras publicar la 3.ª fila y antes de marcarla (" + sinMarcar + " filas sin marcar al morir); al reiniciar se republica: " + mensajes + " órdenes en el tema para "
                + ids.size() + " ids distintos (duplicados " + (mensajes - ids.size()) + "); cobros " + cobros + " (confirmados " + confirmados + "), cobros dobles " + doble);
        assertTrue(mensajes > ids.size(), "la fila publicada y sin marcar salió dos veces: es el al-menos-una-vez");
        assertEquals(0, doble);
        assertEquals(confirmados, cobros - mundo.n("pagos", "SELECT count(*) FROM cobros WHERE reembolsado"), "un cobro vigente por pedido confirmado");
        invariantes("morir entre publicar y marcar");
    }
}
