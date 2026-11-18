package notificaciones;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NotificacionesTest {
    static Map<Notificacion.Canal, Proveedor[]> proveedores(double fallo, boolean conRespaldo, long latMicros, boolean idempotente) {
        Map<Notificacion.Canal, Proveedor[]> m = new EnumMap<>(Notificacion.Canal.class);
        long semilla = 1;
        for (var c : Notificacion.Canal.values()) {
            Proveedor p = new Proveedor(c + "-A", fallo, latMicros, idempotente, semilla++);
            m.put(c, conRespaldo ? new Proveedor[]{p, new Proveedor(c + "-B", 0.0, latMicros, idempotente, semilla++)} : new Proveedor[]{p});
        }
        return m;
    }

    static ExecutorService trabajadores(ServicioDeNotificaciones s, int n, java.util.function.Predicate<Notificacion> muere) {
        // n trabajadores POR CANAL: cada canal tiene su propia cola y su propio grupo
        ExecutorService ex = Executors.newFixedThreadPool(n * Notificacion.Canal.values().length);
        for (var canal : Notificacion.Canal.values()) for (int i = 0; i < n; i++) ex.submit(s.trabajador(canal, muere));
        return ex;
    }

    static void terminar(ServicioDeNotificaciones s, ExecutorService ex, long maxSegundos) throws Exception {
        long limite = System.nanoTime() + maxSegundos * 1_000_000_000L;
        while (s.pendientes() > 0 && System.nanoTime() < limite) Thread.sleep(10);
        s.cerrar();
        ex.shutdown();
        assertTrue(ex.awaitTermination(30, TimeUnit.SECONDS));
    }

    static long percentilMs(List<ServicioDeNotificaciones.Entrega> l, Notificacion.Prioridad p, double q) {
        long[] v = l.stream().filter(e -> e.prioridad() == p).mapToLong(ServicioDeNotificaciones.Entrega::latenciaNanos).sorted().toArray();
        return v[(int) Math.min(v.length - 1, Math.floor(q * v.length))] / 1_000_000;
    }

    @Test
    void laIdempotenciaEvitaEncolarDosVeces() throws Exception {
        var prov = proveedores(0, false, 0, true);
        var s = new ServicioDeNotificaciones(prov, 1000, 5);
        var ex = trabajadores(s, 2, n -> false);
        assertFalse(s.enviar("pedido-77-confirmado", Notificacion.Canal.EMAIL, Notificacion.Prioridad.ALTA, "a@x").duplicada());
        assertTrue(s.enviar("pedido-77-confirmado", Notificacion.Canal.EMAIL, Notificacion.Prioridad.ALTA, "a@x").duplicada());
        assertTrue(s.enviar("pedido-77-confirmado", Notificacion.Canal.EMAIL, Notificacion.Prioridad.ALTA, "a@x").duplicada());
        terminar(s, ex, 10);
        assertEquals(1, prov.get(Notificacion.Canal.EMAIL)[0].entregasVisibles());
    }

    @Test
    void reintentosConRetrocesoYColaDeMensajesFallidos() throws Exception {
        // 30 % de fallo al azar: casi todo se entrega gracias a los reintentos
        var s = new ServicioDeNotificaciones(proveedores(0.3, false, 0, true), 1000, 2);
        var ex = trabajadores(s, 8, n -> false);
        for (int i = 0; i < 2000; i++) s.enviar("a" + i, Notificacion.Canal.SMS, Notificacion.Prioridad.NORMAL, "x");
        terminar(s, ex, 30);
        System.out.printf("%n[reintentos] 2000 enviadas, 30%% de fallo: entregadas=%d, reintentos=%d, en cola de fallidas=%d%n", s.entregas().size(), s.reintentos(), s.muertas().size());
        assertEquals(2000, s.entregas().size() + s.muertas().size());
        assertTrue(s.reintentos() > 400);
        assertTrue(s.muertas().size() < 20, "con 5 intentos al 30 % se pierde ~0,24 %");

        // proveedor siempre caído y sin respaldo: tras 5 intentos todo va a la cola de fallidas
        var s2 = new ServicioDeNotificaciones(proveedores(1.0, false, 0, true), 1000, 1);
        var ex2 = trabajadores(s2, 4, n -> false);
        for (int i = 0; i < 100; i++) s2.enviar("b" + i, Notificacion.Canal.PUSH, Notificacion.Prioridad.NORMAL, "x");
        terminar(s2, ex2, 30);
        assertEquals(100, s2.muertas().size());
        assertEquals(0, s2.entregas().size());
        assertEquals(100 * (ServicioDeNotificaciones.MAX_INTENTOS - 1), s2.reintentos());
    }

    @Test
    void campanaDeCienMilNoRetrasaLoDeAltaPrioridad() throws Exception {
        var prov = proveedores(0.05, true, 1000, true); // 1 ms por envío, 5 % de fallo del primario
        var s = new ServicioDeNotificaciones(prov, 5000, 10);
        var ex = trabajadores(s, 22, n -> false); // 22 por canal = 66 en total
        var canales = Notificacion.Canal.values();
        for (int i = 0; i < 100_000; i++) s.enviar("camp-" + i, canales[i % 3], Notificacion.Prioridad.NORMAL, "x");
        for (int i = 0; i < 300; i++) { // durante la campaña llegan notificaciones de alta prioridad (códigos de acceso)
            s.enviar("otp-" + i, canales[i % 3], Notificacion.Prioridad.ALTA, "x");
            Thread.sleep(5);
        }
        terminar(s, ex, 180);
        var e = s.entregas();
        long altaP99 = percentilMs(e, Notificacion.Prioridad.ALTA, 0.99), altaP50 = percentilMs(e, Notificacion.Prioridad.ALTA, 0.5);
        long normP50 = percentilMs(e, Notificacion.Prioridad.NORMAL, 0.5), normP99 = percentilMs(e, Notificacion.Prioridad.NORMAL, 0.99);
        System.out.printf("%n[campaña] entregadas=%d (normales %d, altas %d), fallidas=%d%n[campaña] ALTA: p50=%d ms p99=%d ms | NORMAL: p50=%d ms p99=%d ms%n",
                e.size(), e.stream().filter(x -> x.prioridad() == Notificacion.Prioridad.NORMAL).count(),
                e.stream().filter(x -> x.prioridad() == Notificacion.Prioridad.ALTA).count(), s.muertas().size(), altaP50, altaP99, normP50, normP99);
        assertEquals(100_300, e.size() + s.muertas().size());
        assertEquals(300, e.stream().filter(x -> x.prioridad() == Notificacion.Prioridad.ALTA).count());
        assertTrue(altaP99 * 5 < normP50, "las de alta prioridad no esperan detrás de la campaña");
        assertTrue(altaP99 < 250, "p99 de alta prioridad: " + altaP99 + " ms");
    }

    @Test
    void caeUnProveedorYSeConmutaAlDeRespaldo() throws Exception {
        var prov = proveedores(0, true, 200, true);
        var s = new ServicioDeNotificaciones(prov, 5000, 5);
        var ex = trabajadores(s, 16, n -> false);
        prov.get(Notificacion.Canal.SMS)[0].caer(true);
        for (int i = 0; i < 3000; i++) s.enviar("sms-" + i, Notificacion.Canal.SMS, Notificacion.Prioridad.NORMAL, "x");
        terminar(s, ex, 30);
        Proveedor a = prov.get(Notificacion.Canal.SMS)[0], b = prov.get(Notificacion.Canal.SMS)[1];
        System.out.printf("%n[conmutación] primario caído: intentos al primario=%d (el cortacircuitos lo aísla), entregas por el respaldo=%d de 3000, fallidas=%d%n",
                a.intentos(), b.distintasRecibidas(), s.muertas().size());
        assertEquals(0, s.muertas().size());
        assertEquals(3000, b.distintasRecibidas());
        assertEquals(0, a.distintasRecibidas());
        assertTrue(a.intentos() < 1500, "sin cortacircuitos serían 3000 intentos");
    }

    @Test
    void matarUnTrabajadorAMitadDeEnvioNoPierdeNadaYSeCuentanLosDuplicados() throws Exception {
        var prov = proveedores(0, false, 500, false);   // este proveedor NO respeta la clave de idempotencia
        var s = new ServicioDeNotificaciones(prov, 150, 5);
        AtomicInteger muertes = new AtomicInteger();
        // los primeros 8 trabajadores que envían una notificación mueren justo después del envío, antes de confirmar
        var ex = trabajadores(s, 32, n -> {
            int m;
            do { m = muertes.get(); if (m >= 8) return false; } while (!muertes.compareAndSet(m, m + 1));
            return true;
        });
        for (int i = 0; i < 1000; i++) s.enviar("k" + i, Notificacion.Canal.EMAIL, Notificacion.Prioridad.NORMAL, "x");
        terminar(s, ex, 30);
        Proveedor p = prov.get(Notificacion.Canal.EMAIL)[0];
        System.out.printf("%n[trabajador muerto] trabajadores muertos=%d, notificaciones distintas recibidas=%d de 1000, duplicados recibidos=%d, entregas visibles (proveedor sin idempotencia)=%d%n",
                muertes.get(), p.distintasRecibidas(), p.duplicadosRecibidos(), p.entregasVisibles());
        assertEquals(8, muertes.get());
        assertEquals(1000, p.distintasRecibidas(), "no se pierde nada");
        assertEquals(8, p.duplicadosRecibidos(), "cada notificación en vuelo al morir el trabajador se reenvía una vez");
        assertEquals(1008, p.entregasVisibles());
    }

    @Test
    void unProveedorLentoNoRetrasaLosOtrosCanales() throws Exception {
        Map<Notificacion.Canal, Proveedor[]> m = new EnumMap<>(Notificacion.Canal.class);
        m.put(Notificacion.Canal.SMS, new Proveedor[]{new Proveedor("SMS-A", 0, 50_000, true, 1)});    // 50 ms por envío
        m.put(Notificacion.Canal.EMAIL, new Proveedor[]{new Proveedor("EMAIL-A", 0, 1_000, true, 2)}); // 1 ms por envío
        m.put(Notificacion.Canal.PUSH, new Proveedor[]{new Proveedor("PUSH-A", 0, 1_000, true, 3)});
        var s = new ServicioDeNotificaciones(m, 5000, 5);
        var ex = trabajadores(s, 8, n -> false);
        for (int i = 0; i < 400; i++) s.enviar("sms-" + i, Notificacion.Canal.SMS, Notificacion.Prioridad.NORMAL, "x"); // 400 x 50 ms / 8 = 2,5 s de trabajo
        for (int i = 0; i < 50; i++) s.enviar("mail-" + i, Notificacion.Canal.EMAIL, Notificacion.Prioridad.NORMAL, "x");
        long limite = System.nanoTime() + 10_000_000_000L;
        while (s.entregas().stream().filter(e -> e.canal() == Notificacion.Canal.EMAIL).count() < 50 && System.nanoTime() < limite) Thread.sleep(5);
        long correosListos = s.entregas().stream().filter(e -> e.canal() == Notificacion.Canal.EMAIL).count();
        long smsListos = s.entregas().stream().filter(e -> e.canal() == Notificacion.Canal.SMS).count();
        long[] v = s.entregas().stream().filter(e -> e.canal() == Notificacion.Canal.EMAIL).mapToLong(ServicioDeNotificaciones.Entrega::latenciaNanos).sorted().toArray();
        long p99 = v[(int) Math.min(v.length - 1, Math.floor(0.99 * v.length))] / 1_000_000;
        System.out.printf("%n[aislamiento] 400 SMS de 50 ms en curso (%d entregados) y 50 correos de 1 ms: correos entregados=%d, p99 de EMAIL=%d ms%n", smsListos, correosListos, p99);
        assertEquals(50, correosListos);
        assertTrue(smsListos < 400, "los correos terminaron mientras la cola de SMS seguía ocupada");
        assertTrue(p99 < 100, "p99 de EMAIL: " + p99 + " ms (con una sola cola compartida eran ~2.500 ms)");
        terminar(s, ex, 30);
    }

    @Test
    void elLimitadorNoSuperaLaCuotaDelProveedor() throws Exception {
        java.util.concurrent.ConcurrentLinkedQueue<Long> llegadas = new java.util.concurrent.ConcurrentLinkedQueue<>();
        CanalDeEnvio proveedor = new CanalDeEnvio() {
            public String nombre() { return "SMS-cuota"; }
            public void enviar(Notificacion n) { llegadas.add(System.nanoTime()); }
        };
        int cuota = 500, rafaga = 50, total = 1500;
        Map<Notificacion.Canal, CanalDeEnvio[]> m = new EnumMap<>(Notificacion.Canal.class);
        for (var c : Notificacion.Canal.values()) m.put(c, new CanalDeEnvio[]{c == Notificacion.Canal.SMS ? new CanalConLimite(proveedor, new LimitadorDeCubeta(cuota, rafaga)) : proveedor});
        var s = new ServicioDeNotificaciones(m, 5000, 5);
        var ex = trabajadores(s, 16, n -> false); // 16 trabajadores podrían enviar mucho más de 500/s si nadie los frenara
        long t0 = System.nanoTime();
        for (int i = 0; i < total; i++) s.enviar("q-" + i, Notificacion.Canal.SMS, Notificacion.Prioridad.NORMAL, "x");
        terminar(s, ex, 30);
        double segundos = (System.nanoTime() - t0) / 1e9;
        long[] t = llegadas.stream().mapToLong(Long::longValue).sorted().toArray();
        assertEquals(total, t.length);
        int maxEnUnSegundo = 0;
        for (int i = 0, j = 0; i < t.length; i++) { while (t[i] - t[j] > 1_000_000_000L) j++; maxEnUnSegundo = Math.max(maxEnUnSegundo, i - j + 1); }
        System.out.printf("%n[límite de tasa] cuota %d/s con ráfaga %d: %d envíos en %.2f s (≈%.0f/s); máximo en cualquier ventana de 1 s=%d%n", cuota, rafaga, total, segundos, total / segundos, maxEnUnSegundo);
        assertTrue(maxEnUnSegundo <= cuota + rafaga, "ventana de 1 s: " + maxEnUnSegundo);
        assertTrue(segundos >= (total - rafaga) / (double) cuota - 0.1, "sin el límite tardaría una fracción de segundo; tardó " + segundos);
    }

    @Test
    void elCortacircuitosSemiabiertoCierraCuandoElPrimarioSeRecupera() throws Exception {
        var prov = proveedores(0, true, 200, true);
        var s = new ServicioDeNotificaciones(prov, 5000, 5);
        var ex = trabajadores(s, 4, n -> false);
        Proveedor a = prov.get(Notificacion.Canal.PUSH)[0];
        a.caer(true);
        for (int i = 0; i < 200; i++) s.enviar("p1-" + i, Notificacion.Canal.PUSH, Notificacion.Prioridad.NORMAL, "x");
        while (s.pendientes() > 0) Thread.sleep(5);
        long intentosCaido = a.intentos();
        a.caer(false);
        Thread.sleep(400); // vence el plazo del circuito abierto: la siguiente notificación es la sonda
        for (int i = 0; i < 200; i++) s.enviar("p2-" + i, Notificacion.Canal.PUSH, Notificacion.Prioridad.NORMAL, "x");
        terminar(s, ex, 30);
        System.out.printf("%n[semiabierto] intentos al primario caído=%d de 200; tras recuperarse recibió %d de 200%n", intentosCaido, a.distintasRecibidas());
        assertTrue(intentosCaido < 100);
        assertEquals(0, s.muertas().size());
        assertTrue(a.distintasRecibidas() >= 190, "el primario volvió a recibir casi todo: " + a.distintasRecibidas());
    }
}
