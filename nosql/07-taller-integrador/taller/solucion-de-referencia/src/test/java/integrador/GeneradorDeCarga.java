package integrador;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.bson.Document;

/**
 * La carga continua de fondo del simulacro: crea pedidos, los consulta, los pagina, los confirma y cancela, por las dos instancias y
 * sin parar mientras se ejecutan las pruebas. Usa sus propios productos (G1 a G20) y sus propios clientes.
 */
final class GeneradorDeCarga implements AutoCloseable {
    final AtomicLong creados = new AtomicLong(), consultas = new AtomicLong(), paginas = new AtomicLong(), confirmados = new AtomicLong(), cancelados = new AtomicLong(),
            rechazos = new AtomicLong(), reintentos = new AtomicLong(), errores5xx = new AtomicLong(), cortes = new AtomicLong();
    private final AtomicBoolean activo = new AtomicBoolean(true), pausado = new AtomicBoolean();
    private final List<Thread> hilos = new ArrayList<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    GeneradorDeCarga(int[] puertos, int hilosN) {
        for (int h = 0; h < hilosN; h++) {
            int numero = h;
            Thread t = new Thread(() -> bucle(puertos, numero), "carga-" + h);
            t.setDaemon(true);
            hilos.add(t);
            t.start();
        }
    }

    void pausar() { pausado.set(true); try { Thread.sleep(600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    void reanudar() { pausado.set(false); }

    private void bucle(int[] puertos, int numero) {
        Random r = new Random(numero);
        long n = 0;
        String ultimo = null;
        while (activo.get()) {
            try {
                Thread.sleep(80);
                if (pausado.get()) continue;
                int puerto = puertos[r.nextInt(puertos.length)];
                String cliente = "bg-" + numero + "-" + (n % 5);
                n++;
                // un cliente decente reintenta con la MISMA clave si la conexión se corta o el servicio responde 409 (en curso) o 503: ahí está la idempotencia
                String clave = "bg-" + numero + "-" + System.nanoTime() + "-" + n, cuerpo = "{\"cliente\":\"" + cliente + "\",\"lineas\":[{\"producto\":\"G" + (1 + r.nextInt(20)) + "\",\"cantidad\":" + (1 + r.nextInt(3)) + "}]}";
                HttpResponse<String> crear = null;
                for (int intento = 0; intento < 60 && activo.get(); intento++) {
                    crear = enviar("POST", puertos[(r.nextInt(puertos.length) + intento) % puertos.length], "/pedidos", clave, cuerpo);
                    if (crear != null && crear.statusCode() != 409 && crear.statusCode() < 500) break;
                    reintentos.incrementAndGet();
                    Thread.sleep(500);
                }
                if (crear != null && (crear.statusCode() == 201 || crear.statusCode() == 200)) { creados.incrementAndGet(); ultimo = Document.parse(crear.body()).getString("id"); }
                if (ultimo != null) {
                    contar(enviar("GET", puerto, "/pedidos/" + ultimo, null, null), consultas);
                    contar(enviar("GET", puerto, "/clientes/" + cliente + "/pedidos?n=3", null, null), paginas);
                    contar(enviar("GET", puerto, "/productos/G" + (1 + r.nextInt(20)), null, null), consultas);
                    if (n % 3 == 0) contar(enviar("POST", puerto, "/pedidos/" + ultimo + "/confirmar", null, ""), confirmados);
                    else if (n % 3 == 1) contar(enviar("POST", puerto, "/pedidos/" + ultimo + "/cancelar", null, ""), cancelados);
                }
            } catch (InterruptedException e) { return; } catch (RuntimeException e) { cortes.incrementAndGet(); }
        }
    }

    private void contar(HttpResponse<String> r, AtomicLong ok) {
        if (r == null) return;
        if (r.statusCode() >= 500) errores5xx.incrementAndGet();
        else if (r.statusCode() < 300) ok.incrementAndGet();
        else rechazos.incrementAndGet();
    }

    private HttpResponse<String> enviar(String metodo, int puerto, String ruta, String clave, String cuerpo) {
        try {
            var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).timeout(Duration.ofSeconds(10));
            if (clave != null) b.header("Idempotency-Key", clave);
            b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) { cortes.incrementAndGet(); return null; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
    }

    String resumen() {
        return "carga de fondo: %d pedidos creados, %d consultas, %d páginas, %d confirmados, %d cancelados, %d rechazos 4xx, %d respuestas 5xx, %d cortes de conexión, %d reintentos de creación con la misma clave"
                .formatted(creados.get(), consultas.get(), paginas.get(), confirmados.get(), cancelados.get(), rechazos.get(), errores5xx.get(), cortes.get(), reintentos.get());
    }

    @Override public void close() { activo.set(false); hilos.forEach(Thread::interrupt); }
}
