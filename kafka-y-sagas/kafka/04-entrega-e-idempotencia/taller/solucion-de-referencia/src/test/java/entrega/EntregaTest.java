package entrega;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EntregaTest {

    static final String B = Kf.bootstrap();

    static Connection db() throws Exception {
        Connection c = DriverManager.getConnection(Kf.pgUrl(), "taller", "taller");
        c.setAutoCommit(true);
        return c;
    }

    @BeforeEach
    void preparar() throws Exception {
        try (Connection c = db(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS saldos, procesados");
            st.execute("CREATE TABLE saldos(cuenta text PRIMARY KEY, saldo bigint NOT NULL, version bigint NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE procesados(id text PRIMARY KEY)");
            for (int i = 1; i <= 5; i++) st.execute("INSERT INTO saldos(cuenta, saldo) VALUES ('cuenta-" + i + "', 0)");
        }
        Kf.tema("abonos", 3);
        Kf.tema("abonos.fallidos", 1);
    }

    static KafkaProducer<String, String> productor() {
        return new KafkaProducer<>(Map.of("bootstrap.servers", B, "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName(), "acks", "all", "enable.idempotence", "true"));
    }

    static void abonos(int n) {
        try (var p = productor()) {
            for (int i = 1; i <= n; i++) p.send(new ProducerRecord<>("abonos", "cuenta-" + (i % 5 + 1), "a" + i + ";10"));
            p.flush();
        }
    }

    static long saldoTotal() throws Exception {
        try (Connection c = db(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT sum(saldo) FROM saldos")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static long contarProcesados() throws Exception {
        try (Connection c = db(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM procesados")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Lanza el consumidor, y cada vez que muere lo vuelve a lanzar, hasta que termina limpio. Devuelve cuántas veces murió. */
    static int hastaTerminar(String modo, String grupo, int muereCada) throws Exception {
        int muertes = 0;
        for (int i = 0; i < 60; i++) {
            Process p = Kf.hijo(Abonos.class, modo, grupo, String.valueOf(muereCada));
            Kf.recoger(p);
            assertTrue(p.waitFor(120, TimeUnit.SECONDS));
            if (p.exitValue() == 0) return muertes;
            muertes++;
        }
        throw new AssertionError("el consumidor no terminó");
    }

    @Test
    @Order(1)
    void paso1_consumidorIngenuoSumaDeMas() throws Exception {
        abonos(100);
        int muertes = hastaTerminar("ingenuo", "g1", 30);
        Ev.nota("1. ingenuo: 100 abonos de 10, muerto " + muertes + " veces antes de confirmar; saldo " + saldoTotal() + " (esperado 1000)");
        assertTrue(saldoTotal() > 1000, "cada muerte repite el lote no confirmado");
    }

    @Test
    @Order(2)
    void paso2_idempotenteConLaMarcaEnLaMismaTransaccion() throws Exception {
        abonos(100);
        int muertes = hastaTerminar("idempotente", "g2", 30);
        Ev.nota("2. idempotente: muerto " + muertes + " veces; saldo " + saldoTotal() + ", mensajes marcados " + contarProcesados());
        assertEquals(1000, saldoTotal());
        assertEquals(100, contarProcesados());
        assertTrue(muertes >= 3);
    }

    @Test
    @Order(3)
    void paso3_separarLasTransaccionesRompeLaIdempotencia() throws Exception {
        abonos(100);
        int muertes = hastaTerminar("separadas", "g3", 30);
        Ev.nota("3. saldo y marca en transacciones separadas, caída entre las dos: muerto " + muertes + " veces; saldo " + saldoTotal() + " (esperado 1000), marcados " + contarProcesados());
        assertTrue(saldoTotal() > 1000, "el efecto se aplicó, la marca no llegó: la reentrega lo aplica otra vez");
    }

    @Test
    @Order(4)
    void paso4_mensajeConElEstadoResultanteYVersion() throws Exception {
        // cada cuenta recibe versiones 1..20 con saldo = 10 * versión, y se publican DESORDENADAS y repetidas
        List<String[]> mensajes = new ArrayList<>();
        for (int c = 1; c <= 5; c++) for (int v = 1; v <= 20; v++) mensajes.add(new String[] {"cuenta-" + c, "m" + c + "-" + v + ";" + (10 * v) + ";" + v});
        List<String[]> mezclados = new ArrayList<>(mensajes);
        mezclados.addAll(mensajes.subList(0, 30));                           // 30 repetidos
        Collections.shuffle(mezclados, new java.util.Random(7));
        try (var p = productor()) {
            for (String[] m : mezclados) p.send(new ProducerRecord<>("abonos", m[0], m[1]));
            p.flush();
        }
        int muertes = hastaTerminar("estado", "g4", 40);
        try (Connection c = db(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT min(saldo), max(saldo), min(version) FROM saldos")) {
            rs.next();
            Ev.nota("4. mensajes con estado y versión, " + mezclados.size() + " entregas desordenadas y repetidas, muerto " + muertes + " veces: saldo por cuenta " + rs.getLong(1) + ".." + rs.getLong(2) + ", versión mínima " + rs.getLong(3));
            assertEquals(200, rs.getLong(1));
            assertEquals(200, rs.getLong(2));
            assertEquals(20, rs.getLong(3));
        }
        assertEquals(1000, saldoTotal(), "sin tabla de procesados, el resultado es exacto: la operación es idempotente por naturaleza");
    }

    @Test
    @Order(5)
    void paso5_mensajeDefectuosoBloqueaLaParticion() throws Exception {
        try (var p = productor()) {
            for (int i = 1; i <= 60; i++) p.send(new ProducerRecord<>("abonos", "cuenta-" + (i % 5 + 1), i == 20 ? "a20;diez" : "a" + i + ";10"));
            p.flush();
        }
        int muertes = 0;
        for (int i = 0; i < 3; i++) {
            Process proceso = Kf.hijo(Abonos.class, "fragil", "g5", "0");
            Kf.recoger(proceso);
            proceso.waitFor(60, TimeUnit.SECONDS);
            if (proceso.exitValue() != 0) muertes++;
        }
        long hechos = contarProcesados();
        Ev.nota("5. mensaje a20 con monto ilegible: el consumidor murió " + muertes + " de 3 intentos; procesados " + hechos + " de 60, saldo " + saldoTotal());
        assertEquals(3, muertes);
        // Por partición: en la del mensaje defectuoso, nada a partir de él se procesó; de las demás, lo que falte es solo porque el proceso murió
        java.util.Set<String> hechosIds = new java.util.HashSet<>();
        try (Connection c = db(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM procesados")) {
            while (rs.next()) hechosIds.add(rs.getString(1));
        }
        var registros = Kf.leerTodo("abonos");
        var defectuoso = registros.stream().filter(r -> r.value().startsWith("a20;")).findFirst().orElseThrow();
        long detras = registros.stream().filter(r -> r.partition() == defectuoso.partition() && r.offset() >= defectuoso.offset()).count();
        long detrasProcesados = registros.stream().filter(r -> r.partition() == defectuoso.partition() && r.offset() >= defectuoso.offset())
                .filter(r -> hechosIds.contains(r.value().split(";")[0])).count();
        long sinProcesarEnLasOtras = registros.stream().filter(r -> r.partition() != defectuoso.partition()).filter(r -> !hechosIds.contains(r.value().split(";")[0])).count();
        Ev.nota("5. por partición: a20 está en la partición " + defectuoso.partition() + ", posición " + defectuoso.offset() + "; de los " + detras + " mensajes de esa partición desde a20 en adelante se procesaron " + detrasProcesados
                + "; en las otras particiones quedaron " + sinProcesarEnLasOtras + " sin procesar (el proceso murió: no es que sigan bloqueadas, es que nadie las lee)");
        assertEquals(0, detrasProcesados, "a20 y todo lo que va detrás en su partición quedó sin procesar");
        assertTrue(detras > 1, "había mensajes detrás de a20 esperando");
        assertTrue(hechos < 59);
    }

    @Test
    @Order(6)
    void pasos6y8_temaDeFallidosYReproceso() throws Exception {
        try (var p = productor()) {
            for (int i = 1; i <= 60; i++) {
                String v = i == 20 ? "a20;diez" : "a" + i + ";10";
                String cuenta = i == 33 ? "cuenta-9" : "cuenta-" + (i % 5 + 1);       // cuenta-9 no existe todavía
                p.send(new ProducerRecord<>("abonos", cuenta, v));
            }
            p.flush();
        }
        assertEquals(0, hastaTerminar("robusto", "g6", 0));
        var fallidos = Kf.leerTodo("abonos.fallidos");
        for (var r : fallidos) {
            StringBuilder cab = new StringBuilder();
            for (Header h : r.headers()) cab.append(h.key()).append('=').append(new String(h.value(), StandardCharsets.UTF_8)).append(' ');
            Ev.nota("6. fallido: " + r.key() + " " + r.value() + " | " + cab);
        }
        assertEquals(2, fallidos.size());
        assertEquals(58 * 10, saldoTotal(), "los 58 buenos se aplicaron: el consumo siguió");
        assertEquals(58, contarProcesados());
        assertTrue(fallidos.stream().allMatch(r -> r.headers().lastHeader("motivo") != null && r.headers().lastHeader("particion-origen") != null && r.headers().lastHeader("posicion-origen") != null));

        // Paso 8: la herramienta de reproceso. Se crea la cuenta que faltaba y se republica en el tema original.
        try (Connection c = db(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO saldos(cuenta, saldo) VALUES ('cuenta-9', 0)");
        }
        long finAntes = Kf.contar("abonos");
        try (var p = productor()) {
            for (ConsumerRecord<String, String> r : fallidos) {
                var d = new ProducerRecord<>(new String(r.headers().lastHeader("tema-origen").value()), r.key(), r.value());
                p.send(d, (m, e) -> Ev.nota("8. republicado " + r.value() + " en la posición " + m.offset() + " de la partición " + m.partition()
                        + " (su posición original era " + new String(r.headers().lastHeader("posicion-origen").value()) + "; el tema ya tenía " + finAntes + " mensajes)"));
            }
            p.flush();
        }
        assertEquals(0, hastaTerminar("robusto", "g6", 0));
        var otraVez = Kf.leerTodo("abonos.fallidos");
        Ev.nota("8. tras reprocesar: saldo " + saldoTotal() + " (esperado 590: a33 aplicado; a20 sigue ilegible), fallidos " + otraVez.size() + " (a20 volvió a caer: sin arreglarlo, el reproceso es un bucle)");
        assertEquals(590, saldoTotal());
        assertEquals(3, otraVez.size());
    }

    @Test
    @Order(7)
    void paso7_falloPasajeroNoLlenaElTemaDeFallidos() throws Exception {
        try (Connection bloqueo = db()) {
            bloqueo.setAutoCommit(false);
            try (Statement st = bloqueo.createStatement()) {
                st.execute("LOCK TABLE saldos IN EXCLUSIVE MODE");           // la base "rechaza" las escrituras: lock_timeout de 1 s en el consumidor
            }
            abonos(20);
            Process p = Kf.hijo(Abonos.class, "robusto", "g7", "0");
            StringBuffer salida = Kf.recoger(p);
            Thread.sleep(20_000);                                            // veinte segundos de base inaccesible
            Ev.nota("7. durante los 20 s: " + salida.toString().lines().limit(6).toList());
            assertTrue(salida.toString().contains("reintento"));
            assertTrue(salida.toString().contains("55P03"), "lock_timeout: SQLState 55P03, un fallo pasajero");
            bloqueo.rollback();                                              // la base vuelve
            assertTrue(p.waitFor(120, TimeUnit.SECONDS));
            assertEquals(0, p.exitValue());
        }
        long reintentos = 0;
        Ev.nota("7. tras volver la base: saldo " + saldoTotal() + " (esperado 200), marcados " + contarProcesados() + ", mensajes en fallidos " + Kf.contar("abonos.fallidos"));
        assertEquals(200, saldoTotal());
        assertEquals(0, Kf.contar("abonos.fallidos"), "un fallo pasajero no va al tema de fallidos");
    }

    @Test
    @Order(8)
    void paso9_transaccionesDeKafkaEntreDosTemas() throws Exception {
        Kf.tema("entrada-t", 1);
        Kf.tema("salida-t", 1);
        try (var p = productor()) {
            for (int i = 1; i <= 100; i++) p.send(new ProducerRecord<>("entrada-t", "k", "msg-" + i));
            p.flush();
        }
        Process muere = Kf.hijo(Canalizacion.class, "35");
        Kf.recoger(muere);
        muere.waitFor(60, TimeUnit.SECONDS);
        assertEquals(137, muere.exitValue());
        Process completa = Kf.hijo(Canalizacion.class, "0");
        Kf.recoger(completa);
        assertTrue(completa.waitFor(120, TimeUnit.SECONDS));
        var confirmados = Kf.leerConfirmados(B, "salida-t");
        var todos = Kf.leerTodo(B, "salida-t", "read_uncommitted");
        long distintosC = confirmados.stream().map(ConsumerRecord::value).distinct().count();
        long distintosT = todos.stream().map(ConsumerRecord::value).distinct().count();
        Ev.nota("9. read_committed: " + confirmados.size() + " mensajes, " + distintosC + " distintos. read_uncommitted: " + todos.size() + " mensajes, " + distintosT + " distintos (duplicados " + (todos.size() - distintosT) + ")");
        assertEquals(100, confirmados.size());
        assertEquals(100, distintosC);
        assertTrue(todos.size() > 100, "sin read_committed se ven también los de la transacción abortada");
    }
}
