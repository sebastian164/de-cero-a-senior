package productores;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Pasos 6 y 7: publica eventos PedidoCreado en JSON al tema, con el cliente como clave, y registra para cada envío
 * partición y posición, o el error. Es el adaptador de salida: el resto del servicio solo ve publicar().
 */
public final class ProductorPedidos implements AutoCloseable {

    public final KafkaProducer<String, String> productor;
    private final String tema;
    public final List<String> registro = new CopyOnWriteArrayList<>();      // una línea por envío, confirmado o fallido
    public final AtomicLong confirmados = new AtomicLong();
    public final AtomicLong fallidos = new AtomicLong();
    public final List<String> errores = new CopyOnWriteArrayList<>();

    /** Configuración segura por defecto: acks=all e idempotencia. Los argumentos extra la sobrescriben. */
    public static Properties configuracion(String bootstrap, String... extra) {
        Properties c = new Properties();
        c.put("bootstrap.servers", bootstrap);
        c.put("key.serializer", StringSerializer.class.getName());
        c.put("value.serializer", StringSerializer.class.getName());
        c.put("acks", "all");
        c.put("enable.idempotence", "true");
        c.put("delivery.timeout.ms", "120000");
        for (int i = 0; i < extra.length; i += 2) c.put(extra[i], extra[i + 1]);
        return c;
    }

    public ProductorPedidos(String bootstrap, String tema, String... extra) {
        this.productor = new KafkaProducer<>(configuracion(bootstrap, extra));
        this.tema = tema;
    }

    public static String json(long numero, String clienteId) {
        return "{\"tipo\":\"PedidoCreado\",\"version\":1,\"pedidoId\":\"p-" + numero + "\",\"clienteId\":\"" + clienteId + "\",\"total\":" + (numero % 100 + 1) * 100 + "}";
    }

    public void publicar(long numero, String clienteId) {
        productor.send(new ProducerRecord<>(tema, clienteId, json(numero, clienteId)), (meta, error) -> {
            if (error != null) {
                fallidos.incrementAndGet();
                String linea = "ERROR pedido p-" + numero + ": " + error;
                errores.add(linea);
                registro.add(linea);
            } else {
                confirmados.incrementAndGet();
                registro.add("OK pedido p-" + numero + " partición " + meta.partition() + " posición " + meta.offset());
            }
        });
    }

    public void vaciar() {
        productor.flush();
    }

    /** Como vaciar(), pero acotado: devuelve false si tras ms milisegundos aún quedaba algo sin resolver. */
    public boolean vaciarHasta(long ms) {
        Thread hilo = new Thread(productor::flush, "vaciar");
        hilo.setDaemon(true);
        hilo.start();
        try {
            hilo.join(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return !hilo.isAlive();
    }

    /** El cierre ordenado espera lo pendiente, pero no para siempre: 30 s como máximo. */
    @Override
    public void close() {
        productor.close(java.time.Duration.ofSeconds(30));
    }
}
