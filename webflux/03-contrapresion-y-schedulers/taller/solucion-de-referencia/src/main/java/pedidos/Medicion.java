package pedidos;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Pasos 2, 4, 5 y 6 del taller 03. */
public class Medicion {

    record Resultado(long totalMs, long pingMs, int confirmadas) {}

    /** Inventario y antifraude entregan su respuesta EN el bucle (como Netty entrega las respuestas HTTP). */
    static ConfirmarPedido servicio(Scheduler bucle, Pagos pagos, Dobles2.Almacen almacen) {
        Inventario inv = p -> Mono.just(new Reserva(p.id())).delayElement(Duration.ofMillis(20)).publishOn(bucle);
        Antifraude af = p -> Mono.just(true).delayElement(Duration.ofMillis(20)).publishOn(bucle);
        return new ConfirmarPedido(almacen, inv, af, pagos);
    }

    static Resultado medir(int n, Pagos pagos) throws Exception {
        Scheduler bucle = Schedulers.newParallel("bucle", 2);
        try {
            Dobles2.Almacen almacen = new Dobles2.Almacen(n);
            ConfirmarPedido s = servicio(bucle, pagos, almacen);
            AtomicInteger ok = new AtomicInteger();
            CountDownLatch fin = new CountDownLatch(n);
            long t0 = System.nanoTime();
            for (int i = 1; i <= n; i++) {
                s.confirmar("p" + i).subscribeOn(bucle).subscribe(p -> ok.incrementAndGet(), e -> { fin.countDown(); }, fin::countDown);
            }
            Thread.sleep(50);                         // que las confirmaciones ya estén en marcha
            long tp = System.nanoTime();
            Mono.fromSupplier(() -> "pong").subscribeOn(bucle).block(Duration.ofSeconds(60));
            long ping = (System.nanoTime() - tp) / 1_000_000;
            fin.await(120, TimeUnit.SECONDS);
            return new Resultado((System.nanoTime() - t0) / 1_000_000, ping, ok.get());
        } finally { bucle.dispose(); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== Paso 2: 20 confirmaciones + un ping, bucle de 2 hilos, Mono.just(pagos.cobrar(p))");
        Resultado a = medir(20, new PagosApurados());
        System.out.printf("   total %d ms, espera del ping %d ms, confirmadas %d%n", a.totalMs(), a.pingMs(), a.confirmadas());

        System.out.println("== Paso 4: lo mismo con fromCallable + boundedElastic");
        PagosCorregidos c = new PagosCorregidos();
        Resultado b = medir(20, c);
        System.out.printf("   total %d ms, espera del ping %d ms, confirmadas %d%n", b.totalMs(), b.pingMs(), b.confirmadas());

        System.out.println("== Paso 5: 500 confirmaciones con boundedElastic");
        PagosCorregidos c500 = new PagosCorregidos();
        Resultado r = medir(500, c500);
        System.out.printf("   total %d ms, ping %d ms, confirmadas %d, hilos boundedElastic-* usados: %d (tope por defecto: 10 x %d núcleos = %d)%n",
                r.totalMs(), r.pingMs(), r.confirmadas(), c500.hilos.size(), Runtime.getRuntime().availableProcessors(), 10 * Runtime.getRuntime().availableProcessors());
        tope();
        contrapresion();
        Schedulers.shutdownNow();
        System.exit(0);   // los Schedulers.newSingle de la demo son hilos no demonio
    }

    /** Qué pasa al alcanzar el tope de tareas en cola de un boundedElastic (versión pequeña del mismo scheduler). */
    static void tope() throws Exception {
        Scheduler pequeno = Schedulers.newBoundedElastic(2, 3, "pequeno");
        CountDownLatch libre = new CountDownLatch(1);
        int aceptadas = 0; String error = "";
        try {
            for (int i = 0; i < 10; i++) {
                Mono.fromCallable(() -> { libre.await(); return 1; }).subscribeOn(pequeno).subscribe(x -> {}, e -> {});
                aceptadas++;
            }
        } catch (Exception e) { error = e.getClass().getSimpleName() + ": " + e.getMessage(); }
        // la rechazada llega como señal de error, no como excepción: la capturamos
        AtomicLong rechazadas = new AtomicLong();
        for (int i = 0; i < 5; i++) {
            Mono.fromCallable(() -> 1).subscribeOn(pequeno).subscribe(x -> {}, e -> { rechazadas.incrementAndGet(); if (rechazadas.get() == 1) System.out.println("   señal de error: " + e.getClass().getSimpleName() + ": " + e.getMessage()); });
        }
        Thread.sleep(200);
        System.out.println("== Tope: bounded elastic con 2 hilos y 3 tareas en cola; rechazadas al saturar: " + rechazadas.get() + " " + error);
        libre.countDown(); pequeno.dispose();
    }

    // ---- Paso 6: contrapresión ----
    static void contrapresion() throws Exception {
        System.out.println("== Paso 6: avisos de existencias a ~500/s, procesar cada uno tarda 5 ms, durante 2 s");
        // 1. sin estrategia
        AtomicInteger p1 = new AtomicInteger();
        String err = null;
        try {
            Flux.interval(Duration.ofMillis(2)).take(Duration.ofSeconds(2)).publishOn(Schedulers.newSingle("proc"), 1)
                    .doOnNext(t -> { dormir(5); p1.incrementAndGet(); }).blockLast(Duration.ofSeconds(20));
        } catch (Exception e) { err = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()); }
        System.out.println("   sin estrategia: procesados " + p1 + ", error: " + err);

        // 2. buffer: no se pierde nada, la cola crece
        AtomicInteger p2 = new AtomicInteger(); AtomicLong ultimo2 = new AtomicLong(-1); AtomicInteger emit2 = new AtomicInteger();
        long t0 = System.nanoTime();
        Flux.interval(Duration.ofMillis(2)).take(Duration.ofSeconds(2)).doOnNext(t -> emit2.incrementAndGet())
                .onBackpressureBuffer().publishOn(Schedulers.newSingle("proc2"), 1)
                .doOnNext(t -> { dormir(5); p2.incrementAndGet(); ultimo2.set(t); }).blockLast(Duration.ofSeconds(60));
        System.out.printf("   onBackpressureBuffer: emitidos %d, procesados %d, terminó a los %d ms (la cola creció y se vació tarde)%n", emit2.get(), p2.get(), (System.nanoTime() - t0) / 1_000_000);

        // 3. latest: solo importa la última existencia de cada producto
        AtomicInteger p3 = new AtomicInteger(); AtomicLong ultimo3 = new AtomicLong(-1); AtomicLong ultimoEmitido = new AtomicLong(-1); AtomicInteger emit3 = new AtomicInteger();
        t0 = System.nanoTime();
        Flux.interval(Duration.ofMillis(2)).take(Duration.ofSeconds(2)).doOnNext(t -> { emit3.incrementAndGet(); ultimoEmitido.set(t); })
                .onBackpressureLatest().publishOn(Schedulers.newSingle("proc3"), 1)
                .doOnNext(t -> { dormir(5); p3.incrementAndGet(); ultimo3.set(t); }).blockLast(Duration.ofSeconds(60));
        System.out.printf("   onBackpressureLatest: emitidos %d, procesados %d (descartados %d), último emitido %d, último procesado %d, terminó a los %d ms%n",
                emit3.get(), p3.get(), emit3.get() - p3.get(), ultimoEmitido.get(), ultimo3.get(), (System.nanoTime() - t0) / 1_000_000);

        // 4. Con varios productos "el último valor" es POR PRODUCTO: 20 productos rotando, el aviso t es del producto t % 20 y su valor es t.
        //    El criterio de negocio: al final, el valor procesado de cada producto debe ser el último emitido de ese producto.
        for (boolean porProducto : new boolean[] {false, true}) {
            long[] emitido = new long[PRODUCTOS], procesado = new long[PRODUCTOS]; java.util.Arrays.fill(emitido, -1); java.util.Arrays.fill(procesado, -1);
            AtomicInteger total = new AtomicInteger(), procesados = new AtomicInteger();
            Flux<Long> avisos = Flux.interval(Duration.ofMillis(2)).take(Duration.ofSeconds(2)).doOnNext(t -> { emitido[(int) (t % PRODUCTOS)] = t; total.incrementAndGet(); });
            Scheduler proc = Schedulers.newSingle(porProducto ? "proc5" : "proc4");
            t0 = System.nanoTime();
            Flux<Long> procesar = porProducto
                    ? avisos.groupBy(t -> t % PRODUCTOS).flatMap(g -> g.onBackpressureLatest().publishOn(proc, 1), PRODUCTOS)     // un "último valor" por cada producto
                    : avisos.onBackpressureLatest().publishOn(proc, 1);                                                          // un único "último valor" para todos
            procesar.doOnNext(t -> { dormir(5); procesado[(int) (t % PRODUCTOS)] = t; procesados.incrementAndGet(); }).blockLast(Duration.ofSeconds(60));
            int obsoletos = 0; for (int i = 0; i < PRODUCTOS; i++) if (procesado[i] != emitido[i]) obsoletos++;
            System.out.printf("   %s: procesados %d de %d emitidos, productos cuyo valor final procesado NO es el último emitido: %d de %d, terminó a los %d ms%n",
                    porProducto ? "groupBy(producto) + onBackpressureLatest por grupo" : "onBackpressureLatest GLOBAL con 20 productos", procesados.get(), total.get(), obsoletos, PRODUCTOS, (System.nanoTime() - t0) / 1_000_000);
            proc.dispose();
        }
    }

    static final int PRODUCTOS = 20;

    static void dormir(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
