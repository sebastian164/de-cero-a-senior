package tienda.adaptadores.salida.kafka;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.entrada.ConsultarPedido;

/**
 * El destino de la bandeja de salida del servicio de pedidos: el "mensajero" que la entrega a Kafka. El servicio ya escribía sus eventos
 * en la bandeja; esto es solo un adaptador de salida nuevo. No toca el dominio ni la aplicación.
 *
 * Clave del mensaje: el id del pedido. Un pedido, una partición: los eventos de un mismo pedido (creado, aprobado, confirmado) llegan en el
 * orden en que se escribieron. Entre pedidos distintos no se promete orden, y no hace falta.
 *
 * El evento de la aplicación es delgado (id, tipo, pedido, cliente). Para que Inventario pueda reservar hace falta saber qué y cuánto:
 * el adaptador lo completa consultando el puerto de entrada ConsultarPedido, y publica un mensaje por línea del pedido.
 */
public final class MensajeroKafka implements AutoCloseable {

    public static final String TEMA = "pedidos.creados";
    private final KafkaProducer<String, String> productor;
    private final ConsultarPedido consulta;
    private final boolean conCanal;
    private final AtomicBoolean seguir = new AtomicBoolean(true);
    private Thread hilo;

    public MensajeroKafka(String bootstrap, ConsultarPedido consulta, boolean conCanal) {
        this.consulta = consulta;
        this.conCanal = conCanal;
        productor = new KafkaProducer<>(Map.of("bootstrap.servers", bootstrap, "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName(), "acks", "all", "enable.idempotence", "true",
                "max.block.ms", "3000", "delivery.timeout.ms", "6000", "request.timeout.ms", "3000"));
    }

    /** El Consumer que la bandeja llama por cada evento pendiente. Si lanza, el evento se queda en la bandeja. */
    public void entregar(EventoDePedido evento) {
        if (!evento.tipo().equals("PedidoCreado")) return;               // a Inventario solo le interesan los pedidos nuevos
        var pedido = consulta.porId(evento.pedidoId()).orElseThrow();
        for (var linea : pedido.lineas()) {
            String valor = "{\"id\":\"" + evento.id() + "#" + linea.producto() + "\",\"tipo\":\"PedidoCreado\",\"version\":" + (conCanal ? 2 : 1)
                    + ",\"datos\":{\"pedidoId\":\"" + evento.pedidoId() + "\",\"producto\":\"" + linea.producto() + "\",\"cantidad\":" + linea.cantidad()
                    + (conCanal ? ",\"canal\":\"web\"" : "") + "}}";
            try {
                productor.send(new ProducerRecord<>(TEMA, evento.pedidoId(), valor)).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Kafka no respondió: el evento sigue en la bandeja", e);
            }
        }
    }

    /** Una vuelta: la bandeja entrega sus pendientes al mensajero. Devuelve cuántos salieron. */
    public int unaVuelta(ToIntFunction<Consumer<EventoDePedido>> bandeja) {
        try {
            return bandeja.applyAsInt(this::entregar);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public void arrancar(ToIntFunction<Consumer<EventoDePedido>> bandeja, long intervaloMs) {
        hilo = new Thread(() -> {
            while (seguir.get()) {
                try {
                    if (unaVuelta(bandeja) == 0) Thread.sleep(intervaloMs);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    @Override
    public void close() throws InterruptedException {
        seguir.set(false);
        if (hilo != null) { hilo.interrupt(); hilo.join(10_000); }
        productor.close(java.time.Duration.ofSeconds(2));
    }
}
