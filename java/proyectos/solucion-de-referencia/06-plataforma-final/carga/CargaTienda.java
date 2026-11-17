import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Prueba de carga mínima: java CargaTienda.java <url> <hilos> <pedidos> <skus>. Imprime p50/p95/p99, errores y pedidos/s. */
public class CargaTienda {
    public static void main(String[] a) throws Exception {
        String url = a[0]; int hilos = Integer.parseInt(a[1]), total = Integer.parseInt(a[2]), skus = Integer.parseInt(a[3]);
        HttpClient http = HttpClient.newBuilder().executor(Executors.newFixedThreadPool(hilos)).build();
        for (int s = 0; s < skus; s++) post(http, url + "/productos", "{\"sku\":\"CARGA" + s + "\",\"nombre\":\"p\",\"precio\":10.00,\"stock\":1000000}");
        List<Long> tiempos = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errores = new AtomicInteger(), confirmados = new AtomicInteger(), siguiente = new AtomicInteger();
        long t0 = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        List<Future<?>> fs = new ArrayList<>();
        for (int h = 0; h < hilos; h++) fs.add(pool.submit(() -> {
            int i;
            while ((i = siguiente.getAndIncrement()) < total) {
                long ini = System.nanoTime();
                try {
                    var r = post(http, url + "/pedidos", "{\"cliente\":\"c" + (i % 500) + "\",\"sku\":\"CARGA" + (i % skus) + "\",\"cantidad\":1}");
                    if (r.statusCode() == 200 && r.body().contains("CONFIRMADO")) confirmados.incrementAndGet(); else errores.incrementAndGet();
                } catch (Exception e) { errores.incrementAndGet(); }
                tiempos.add((System.nanoTime() - ini) / 1_000_000);
            }
        }));
        for (var f : fs) f.get();
        double seg = (System.nanoTime() - t0) / 1e9;
        pool.shutdown(); System.exit(0 * resumen(tiempos, confirmados.get(), errores.get(), seg, hilos, skus));
    }

    static int resumen(List<Long> t, int ok, int err, double seg, int hilos, int skus) {
        Collections.sort(t);
        System.out.printf("hilos=%d skus=%d pedidos=%d confirmados=%d errores=%d  %.0f pedidos/s  p50=%d ms p95=%d ms p99=%d ms  reintentos_de_conexion_del_cliente=%d%n",
                hilos, skus, t.size(), ok, err, t.size() / seg, t.get(t.size() / 2), t.get((int) (t.size() * 0.95)), t.get((int) (t.size() * 0.99)), REINTENTOS_DEL_CLIENTE.get());
        return 0;
    }

    /** Reintentos de CONEXIÓN del cliente: el sistema operativo se quedó sin puertos efímeros (BindException); la petición ni salió. */
    static final AtomicInteger REINTENTOS_DEL_CLIENTE = new AtomicInteger();

    static HttpResponse<String> post(HttpClient http, String url, String cuerpo) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        for (int intento = 0; ; intento++) {
            try {
                return http.send(peticion, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.ConnectException e) {
                // Solo se reintenta un fallo al CONECTAR por falta de puertos locales: la petición nunca llegó al servidor, no hay duplicados.
                if (!(e.getCause() instanceof java.net.BindException) || intento >= 8) throw e;
                REINTENTOS_DEL_CLIENTE.incrementAndGet();
                Thread.sleep(250L * (intento + 1));
            }
        }
    }
}
