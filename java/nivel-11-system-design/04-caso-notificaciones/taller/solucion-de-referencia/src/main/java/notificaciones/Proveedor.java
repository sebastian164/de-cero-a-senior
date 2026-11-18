package notificaciones;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Proveedor simulado: latencia fija, fallos al azar, y se puede "tumbar". Registra cuántas veces recibió cada notificación. */
public class Proveedor implements CanalDeEnvio {
    public static class FalloDelProveedor extends CanalDeEnvio.FalloDelCanal {
        FalloDelProveedor(String m) { super(m); }
    }
    private final String nombre;
    private final double tasaDeFallo;
    private final long latenciaMicros;
    private final boolean respetaIdempotencia;
    private volatile boolean caido;
    private final Random azar;
    private final Map<String, AtomicInteger> recibidas = new ConcurrentHashMap<>();
    private final AtomicLong intentos = new AtomicLong();

    public Proveedor(String nombre, double tasaDeFallo, long latenciaMicros, boolean respetaIdempotencia, long semilla) {
        this.nombre = nombre; this.tasaDeFallo = tasaDeFallo; this.latenciaMicros = latenciaMicros;
        this.respetaIdempotencia = respetaIdempotencia; this.azar = new Random(semilla);
    }

    @Override
    public void enviar(Notificacion n) {
        intentos.incrementAndGet();
        if (latenciaMicros > 0) java.util.concurrent.locks.LockSupport.parkNanos(latenciaMicros * 1000);
        if (caido) throw new FalloDelProveedor(nombre + " caído");
        boolean falla; synchronized (azar) { falla = azar.nextDouble() < tasaDeFallo; }
        if (falla) throw new FalloDelProveedor(nombre + " falló al azar");
        recibidas.computeIfAbsent(n.claveIdempotencia(), k -> new AtomicInteger()).incrementAndGet();
    }

    public void caer(boolean c) { caido = c; }
    @Override
    public String nombre() { return nombre; }
    public long intentos() { return intentos.get(); }
    public int distintasRecibidas() { return recibidas.size(); }
    /** Entregas con efecto visible: si el proveedor respeta la clave de idempotencia, un duplicado no llega al usuario. */
    public long entregasVisibles() {
        return respetaIdempotencia ? recibidas.size() : recibidas.values().stream().mapToLong(AtomicInteger::get).sum();
    }
    public long duplicadosRecibidos() { return recibidas.values().stream().mapToLong(a -> Math.max(0, a.get() - 1)).sum(); }
    public boolean recibio(String clave) { return recibidas.containsKey(clave); }
}
