package notificaciones;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Núcleo: API (enviar), idempotencia por clave, una cola con prioridades y un grupo de trabajadores por canal
 * (un canal lento no retrasa a los demás), reintentos con retroceso exponencial y jitter, cola de mensajes fallidos
 * (DLQ) y cortacircuitos con estado semiabierto que conmuta al proveedor de respaldo. Los proveedores entran como
 * {@link CanalDeEnvio}; el límite de tasa se agrega envolviéndolos con {@link CanalConLimite}.
 */
public class ServicioDeNotificaciones {
    public record Resultado(String id, boolean duplicada) { }
    public record Entrega(String id, Notificacion.Canal canal, Notificacion.Prioridad prioridad, long latenciaNanos, String proveedor) { }

    public static final int MAX_INTENTOS = 5;
    private static final int FALLOS_PARA_ABRIR = 5;
    private static final long ABIERTO_NANOS = 300_000_000L;

    private final Map<Notificacion.Canal, ? extends CanalDeEnvio[]> canales; // [0] primario, [1] respaldo (opcional)
    private final Map<Notificacion.Canal, ColaConArrendamiento> colas = new EnumMap<>(Notificacion.Canal.class);
    private final Map<String, String> clavesVistas = new ConcurrentHashMap<>();
    private final List<Entrega> entregas = new CopyOnWriteArrayList<>();
    private final List<Notificacion> muertas = new CopyOnWriteArrayList<>();
    private final Map<Notificacion.Canal, AtomicInteger> fallosSeguidos = new EnumMap<>(Notificacion.Canal.class);
    private final Map<Notificacion.Canal, AtomicLong> abiertoHastaNanos = new EnumMap<>(Notificacion.Canal.class); // 0 = cerrado
    private final AtomicLong enviadasPorRespaldo = new AtomicLong();
    private final AtomicLong reintentos = new AtomicLong();
    private final java.util.Random azar = new java.util.Random(7);
    private final long baseRetrasoMs;

    public ServicioDeNotificaciones(Map<Notificacion.Canal, ? extends CanalDeEnvio[]> canales, long arrendamientoMs, long baseRetrasoMs) {
        this.canales = canales; this.baseRetrasoMs = baseRetrasoMs;
        for (var c : Notificacion.Canal.values()) {
            colas.put(c, new ColaConArrendamiento(arrendamientoMs));
            fallosSeguidos.put(c, new AtomicInteger());
            abiertoHastaNanos.put(c, new AtomicLong());
        }
    }

    /** POST /v1/notificaciones. Con la misma clave de idempotencia no se encola otra vez. */
    public Resultado enviar(String clave, Notificacion.Canal canal, Notificacion.Prioridad prioridad, String destino) {
        String id = "n-" + clave;
        if (clavesVistas.putIfAbsent(clave, id) != null) return new Resultado(id, true);
        colas.get(canal).publicar(new Notificacion(id, clave, canal, prioridad, destino, System.nanoTime()));
        return new Resultado(id, false);
    }

    /** Un trabajador del canal indicado. Si 'muereAntesDelAck' devuelve true tras enviar, el hilo termina sin confirmar (simula kill -9). */
    public Runnable trabajador(Notificacion.Canal canal, java.util.function.Predicate<Notificacion> muereAntesDelAck) {
        ColaConArrendamiento cola = colas.get(canal);
        return () -> {
            try {
                while (true) {
                    var a = cola.recibir();
                    if (a == null) return;
                    Notificacion n = a.notificacion();
                    String usado;
                    try {
                        usado = despachar(n);
                    } catch (RuntimeException e) {
                        if (a.intento() >= MAX_INTENTOS) { muertas.add(n); cola.ack(a.token()); continue; }
                        reintentos.incrementAndGet();
                        long retraso = (long) (baseRetrasoMs * Math.pow(2, a.intento() - 1));
                        synchronized (azar) { retraso += azar.nextInt((int) Math.max(1, retraso / 2)); } // jitter
                        cola.reintentar(n, a.intento() + 1, retraso);
                        cola.ack(a.token());
                        continue;
                    }
                    if (muereAntesDelAck.test(n)) return;       // el proceso muere: no hay ack, el arrendamiento vencerá
                    entregas.add(new Entrega(n.id(), n.canal(), n.prioridad(), System.nanoTime() - n.creadaNanos(), usado));
                    cola.ack(a.token());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * Cortacircuitos del canal. Cerrado: usa el primario y cuenta fallos seguidos; al quinto abre 300 ms y usa el respaldo.
     * Abierto: va directo al respaldo. Semiabierto (vencido el plazo): UN solo envío de prueba al primario; si sale bien
     * se cierra, si falla se reabre. Sin respaldo no hay a dónde conmutar: el fallo se propaga para reintentar.
     */
    private String despachar(Notificacion n) {
        CanalDeEnvio[] p = canales.get(n.canal());
        if (p.length < 2) { p[0].enviar(n); return p[0].nombre(); }
        AtomicLong hasta = abiertoHastaNanos.get(n.canal());
        AtomicInteger fallos = fallosSeguidos.get(n.canal());
        long ahora = System.nanoTime();
        long h = hasta.get();
        boolean sonda = h != 0 && h <= ahora && hasta.compareAndSet(h, ahora + ABIERTO_NANOS); // elige a una sola sonda
        if (h == 0 || sonda) {
            try {
                p[0].enviar(n);
                fallos.set(0); hasta.set(0);
                return p[0].nombre();
            } catch (CanalDeEnvio.FalloDelCanal e) {
                if (sonda || fallos.incrementAndGet() >= FALLOS_PARA_ABRIR) hasta.set(System.nanoTime() + ABIERTO_NANOS);
            }
        }
        p[1].enviar(n); // si el respaldo falla, la excepción dispara el reintento
        enviadasPorRespaldo.incrementAndGet();
        return p[1].nombre();
    }

    public void cerrar() { colas.values().forEach(ColaConArrendamiento::cerrar); }
    public int pendientes() { return colas.values().stream().mapToInt(ColaConArrendamiento::pendientes).sum(); }
    public List<Entrega> entregas() { return entregas; }
    public List<Notificacion> muertas() { return muertas; }
    public long reintentos() { return reintentos.get(); }
    public long enviadasPorRespaldo() { return enviadasPorRespaldo.get(); }
    public Optional<Entrega> entrega(String id) { return entregas.stream().filter(e -> e.id().equals(id)).findFirst(); }
}
