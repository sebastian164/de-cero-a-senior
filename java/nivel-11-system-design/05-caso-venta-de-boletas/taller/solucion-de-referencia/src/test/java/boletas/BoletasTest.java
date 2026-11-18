package boletas;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Requiere PostgreSQL (BOLETAS_HOST, por defecto localhost:5432). Crea la base 'boletas'. */
class BoletasTest {
    static HikariDataSource ds;
    static final String HOST = System.getenv().getOrDefault("BOLETAS_HOST", "localhost:5432");

    @BeforeAll
    static void conectar() throws Exception {
        HikariConfig admin = new HikariConfig();
        admin.setJdbcUrl("jdbc:postgresql://" + HOST + "/postgres"); admin.setUsername("postgres"); admin.setPassword("local");
        admin.setMaximumPoolSize(1);
        try (HikariDataSource a = new HikariDataSource(admin); Connection c = a.getConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT 1 FROM pg_database WHERE datname='boletas'")) {
            if (!r.next()) s.execute("CREATE DATABASE boletas");
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://" + HOST + "/boletas"); cfg.setUsername("postgres"); cfg.setPassword("local");
        cfg.setMaximumPoolSize(40);
        ds = new HikariDataSource(cfg);
    }

    @AfterAll
    static void cerrar() { ds.close(); }

    /** 1000 hilos compiten por 100 asientos: cada hilo recorre los asientos desde un punto al azar hasta conseguir uno. */
    static int competir(Reservas.Estrategia e, Reservas reservas, int hilos, int asientos) throws Exception {
        AtomicInteger exitos = new AtomicInteger();
        try (ExecutorService ex = Executors.newFixedThreadPool(hilos)) {
            List<Future<?>> fs = new ArrayList<>();
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            for (int h = 0; h < hilos; h++) {
                final int usuario = h;
                fs.add(ex.submit(() -> {
                    salida.await();
                    int inicio = java.util.concurrent.ThreadLocalRandom.current().nextInt(asientos);
                    for (int i = 0; i < asientos; i++) {
                        if (reservas.reservar(e, usuario, 1, 1 + (inicio + i) % asientos, 600) != Reservas.SIN_RESERVA) { exitos.incrementAndGet(); break; }
                    }
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> f : fs) f.get();
        }
        return exitos.get();
    }

    @Test
    void milHilosCienAsientosSeReservanExactamenteCien() throws Exception {
        Esquema.recrear(ds, 100);
        var reservas = new Reservas(ds);
        long t0 = System.nanoTime();
        int exitos = competir(Reservas.Estrategia.CONDICIONAL, reservas, 1000, 100);
        System.out.printf("%n[condicional] 1000 hilos, 100 asientos: reservas exitosas=%d, asientos RESERVADO=%d, tiempo=%d ms%n", exitos, reservas.contar("RESERVADO"), (System.nanoTime() - t0) / 1_000_000);
        assertEquals(100, exitos);
        assertEquals(100, reservas.contar("RESERVADO"));
        assertEquals(100, reservas.contarReservas(), "una fila de reserva por asiento tomado: los intentos fallidos no dejan reservas huérfanas");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT count(DISTINCT reserva_id) FROM asientos WHERE estado='RESERVADO'")) {
            r.next();
            assertEquals(100, r.getLong(1), "ningún asiento comparte reserva y ninguno se vendió dos veces");
        }
    }

    @Test
    void comparaLasTresEstrategias() throws Exception {
        System.out.printf("%n%-12s %-9s %-10s %-22s%n", "estrategia", "exitos", "ms", "reintentos de lectura");
        for (var e : Reservas.Estrategia.values()) {
            Esquema.recrear(ds, 100);
            var reservas = new Reservas(ds);
            long t0 = System.nanoTime();
            int exitos = competir(e, reservas, 1000, 100);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("%-12s %-9d %-10d %-22d%n", e, exitos, ms, reservas.reintentos.get());
            assertEquals(100, exitos, "todas las estrategias son correctas: " + e);
            assertEquals(100, reservas.contar("RESERVADO"));
            assertEquals(100, reservas.contarReservas(), "sin reservas huérfanas: " + e);
            if (e != Reservas.Estrategia.OPTIMISTA) assertEquals(0, reservas.reintentos.get());
        }
    }

    @Test
    void unaReservaVencidaDejaElAsientoDisponible() throws Exception {
        Esquema.recrear(ds, 5);
        var reservas = new Reservas(ds);
        long a = reservas.reservar(Reservas.Estrategia.CONDICIONAL, 1, 1, 3, 1);       // vence en 1 s
        assertNotEquals(Reservas.SIN_RESERVA, a);
        assertEquals(Reservas.SIN_RESERVA, reservas.reservar(Reservas.Estrategia.CONDICIONAL, 2, 1, 3, 600)); // aún ocupado
        assertEquals(1, reservas.contarReservas(), "el intento fallido no dejó ninguna reserva");
        Thread.sleep(1300);
        long b = reservas.reservar(Reservas.Estrategia.CONDICIONAL, 2, 1, 3, 600);
        assertNotEquals(Reservas.SIN_RESERVA, b, "tras vencer, otra persona lo toma sin esperar al proceso de limpieza");
        assertEquals("RESERVADO:" + b, reservas.estadoDe(1, 3));

        // y el proceso periódico libera los vencidos y marca EXPIRADA la reserva
        long c = reservas.reservar(Reservas.Estrategia.PESIMISTA, 3, 1, 4, 1);
        assertNotEquals(Reservas.SIN_RESERVA, c);
        Thread.sleep(1300);
        assertEquals(1, reservas.liberarVencidos());
        assertEquals("LIBRE:-", reservas.estadoDe(1, 4));
        assertEquals("EXPIRADA", reservas.estadoDeReserva(c));
        assertEquals("EXPIRADA", reservas.estadoDeReserva(a));
        assertEquals("ACTIVA", reservas.estadoDeReserva(b));
    }

    /** La transacción del README (sección 5): varios asientos, ROLLBACK si faltan filas, INSERT de la reserva. */
    @Test
    void laTransaccionDelReadmeReservaVariosAsientosOCancelaTodo() throws Exception {
        Esquema.recrear(ds, 10);
        var reservas = new Reservas(ds);
        long r1 = reservas.reservarVariosComoEnElReadme(1, 1, new Integer[]{1, 2, 3}, 600);
        assertNotEquals(Reservas.SIN_RESERVA, r1);
        assertEquals(Reservas.SIN_RESERVA, reservas.reservarVariosComoEnElReadme(2, 1, new Integer[]{3, 4, 5}, 600), "el 3 ya está reservado: se cancela todo (409)");
        assertEquals("LIBRE:-", reservas.estadoDe(1, 4), "los asientos 4 y 5 no quedaron tomados por el intento fallido");
        assertEquals("RESERVADO:" + r1, reservas.estadoDe(1, 3));
        assertEquals(1, reservas.contarReservas(), "solo la reserva exitosa tiene fila");
        // la versión con vencimiento se comporta igual
        long r3 = reservas.reservarVarios(3, 1, new Integer[]{6, 7}, 600);
        assertNotEquals(Reservas.SIN_RESERVA, r3);
        assertEquals(Reservas.SIN_RESERVA, reservas.reservarVarios(4, 1, new Integer[]{7, 8}, 600));
        assertEquals("LIBRE:-", reservas.estadoDe(1, 8));
        assertEquals(2, reservas.contarReservas());
    }

    /** El SQL que ejecuta el código es el del README (se lee el README y se compara, normalizando :parametros y espacios). */
    @Test
    void elSqlDeLaTransaccionEsElDelReadme() throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        java.nio.file.Path readme = null;
        for (; dir != null; dir = dir.getParent()) {
            var candidato = dir.resolve("java/nivel-11-system-design/05-caso-venta-de-boletas/README.md");
            if (java.nio.file.Files.exists(candidato)) { readme = candidato; break; }
        }
        assertNotNull(readme, "no se encontró el README de la sesión");
        String md = java.nio.file.Files.readString(readme);
        var m = java.util.regex.Pattern.compile("(?s)```sql\\s*BEGIN;\\s*(UPDATE.*?);").matcher(md);
        assertTrue(m.find(), "bloque SQL del README");
        java.util.function.UnaryOperator<String> normalizar = t -> t.replaceAll(":[a-z]+", "?").replaceAll("\\s+", " ").trim();
        assertEquals(normalizar.apply(m.group(1)), normalizar.apply(Reservas.SQL_README));
    }

    @Test
    void pagoAprobadoTrasLaExpiracion() throws Exception {
        Esquema.recrear(ds, 5);
        var reservas = new Reservas(ds); var pagos = new Pagos(ds);
        var CON = Reservas.Estrategia.CONDICIONAL;

        // Caso A: la reserva venció pero nadie tomó el asiento: el pago tardío se confirma.
        long a = reservas.reservar(CON, 1, 1, 1, 1);
        assertNotEquals(Reservas.SIN_RESERVA, a);
        Thread.sleep(1300);
        assertEquals("CONFIRMADO", pagos.confirmar(a, 1, 1, "pago-A", true));
        assertEquals("VENDIDO:" + a, reservas.estadoDe(1, 1));
        assertEquals("CONFIRMADA", reservas.estadoDeReserva(a));
        assertEquals("CONFIRMADO", pagos.confirmar(a, 1, 1, "pago-A", true), "la misma clave no repite el efecto");

        // Caso B: venció y otra persona tomó el asiento: el pago tardío se reembolsa y el asiento de la otra persona queda intacto.
        long b = reservas.reservar(CON, 2, 1, 2, 1);
        Thread.sleep(1300);
        long otra = reservas.reservar(CON, 3, 1, 2, 600);
        assertNotEquals(Reservas.SIN_RESERVA, otra);
        assertEquals("REEMBOLSADO", pagos.confirmar(b, 1, 2, "pago-B", true));
        assertEquals("REEMBOLSADA", reservas.estadoDeReserva(b));
        assertEquals("RESERVADO:" + otra, reservas.estadoDe(1, 2));

        // Caso C: al iniciar el pago se extiende la reserva y no vence mientras la pasarela responde.
        long c = reservas.reservar(CON, 4, 1, 3, 1);
        pagos.iniciarPago(c, 1, 3, 120);
        Thread.sleep(1300);
        assertEquals(Reservas.SIN_RESERVA, reservas.reservar(CON, 5, 1, 3, 600), "extendida: nadie más puede tomarlo");
        assertEquals("CONFIRMADO", pagos.confirmar(c, 1, 3, "pago-C", true));
        assertEquals("CONFIRMADA", reservas.estadoDeReserva(c));

        // Caso D: la pasarela rechaza: el asiento no se vende y la reserva sigue como estaba.
        assertEquals("RECHAZADO", pagos.confirmar(otra, 1, 2, "pago-D", false));
        assertEquals("RESERVADO:" + otra, reservas.estadoDe(1, 2));
        assertEquals("ACTIVA", reservas.estadoDeReserva(otra));
    }

    /** Un DataSource cuya conexión falla al preparar la sentencia que contiene el texto dado (simula un fallo a mitad de la operación). */
    static javax.sql.DataSource fallandoEn(String texto) {
        return (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(BoletasTest.class.getClassLoader(), new Class<?>[]{javax.sql.DataSource.class},
            (o, m, a) -> {
                Object r;
                try { r = m.invoke(ds, a); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                if (!m.getName().equals("getConnection")) return r;
                Connection real = (Connection) r;
                return java.lang.reflect.Proxy.newProxyInstance(BoletasTest.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (o2, m2, a2) -> {
                        if (m2.getName().equals("prepareStatement") && ((String) a2[0]).contains(texto)) throw new java.sql.SQLException("fallo simulado");
                        try { return m2.invoke(real, a2); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                    });
            });
    }

    @Test
    void iniciarPagoEsAtomico() throws Exception {
        Esquema.recrear(ds, 5);
        var reservas = new Reservas(ds);
        long r = reservas.reservar(Reservas.Estrategia.CONDICIONAL, 1, 1, 1, 30);
        String plazos = "SELECT (SELECT reservado_hasta FROM asientos WHERE evento_id=1 AND asiento_id=1)::text || '|' || (SELECT expira_en FROM reservas WHERE id=" + r + ")::text";
        java.util.function.Supplier<String> leer = () -> {
            try (Connection c = ds.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(plazos)) { rs.next(); return rs.getString(1); }
            catch (java.sql.SQLException e) { throw new RuntimeException(e); }
        };
        String antes = leer.get();
        assertThrows(java.sql.SQLException.class, () -> new Pagos(fallandoEn("UPDATE reservas")).iniciarPago(r, 1, 1, 600));
        assertEquals(antes, leer.get(), "si falla la segunda sentencia, la primera se deshace: los dos plazos siguen alineados");
        new Pagos(ds).iniciarPago(r, 1, 1, 600);
        assertNotEquals(antes, leer.get(), "sin fallo, los dos plazos se extienden");
    }

    @Test
    void salaDeEsperaAdmiteNPorSegundoEnOrdenDeLlegada() throws Exception {
        int porSegundo = 200, usuarios = 800;
        var sala = new SalaDeEspera(porSegundo);
        for (long u = 1; u <= usuarios; u++) sala.entrar(u);
        long limite = System.nanoTime() + 15_000_000_000L;
        while (sala.admitidos().size() < usuarios && System.nanoTime() < limite) Thread.sleep(20);
        sala.cerrar();
        var adm = sala.admitidos();
        assertEquals(usuarios, adm.size());
        long t0 = adm.get(0).admitidoNanos();
        int[] porVentana = new int[8];
        for (var a : adm) porVentana[(int) Math.min(7, (a.admitidoNanos() - t0) / 1_000_000_000L)]++;
        System.out.printf("%n[sala de espera] %d usuarios llegan a la vez, se admiten %d/s. Admitidos por segundo: %s. Primero admitido: usuario %d (posición 1); último: usuario %d%n",
                usuarios, porSegundo, java.util.Arrays.toString(java.util.Arrays.copyOf(porVentana, 5)), adm.get(0).usuario(), adm.get(usuarios - 1).usuario());
        for (int i = 0; i < 4; i++) assertTrue(porVentana[i] <= porSegundo * 1.05 + 2, "ventana " + i + ": " + porVentana[i]);
        for (int i = 1; i < adm.size(); i++) assertTrue(adm.get(i).posicionAlLlegar() > adm.get(i - 1).posicionAlLlegar(), "orden de llegada");
        long duracionS = (adm.get(usuarios - 1).admitidoNanos() - t0) / 1_000_000_000L;
        assertTrue(duracionS >= 3, "800 usuarios a 200/s tardan unos 4 s, no menos: " + duracionS);
    }
}
