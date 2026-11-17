package outbox;

import comun.Kf;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Procesos hijos de las pruebas: los que tienen que morir de verdad. */
public final class Escenarios {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "doble-escritura-muere" -> {                       // guarda y muere antes de publicar
                var p = new KafkaProducer<String, String>(Map.of("bootstrap.servers", Kf.bootstrap(), "key.serializer", StringSerializer.class.getName(), "value.serializer", StringSerializer.class.getName()));
                Pedidos.crearConDobleEscritura(args[1], p, true);
            }
            case "publicador-muere" -> {                            // publica y muere antes de marcar
                try (var pub = new Publicador(Kf.bootstrap(), Publicador.Reclamo.CORRECTO, 100, false)) {
                    pub.morirAntesDeMarcar = true;
                    pub.unaVuelta();
                }
            }
            case "publicador" -> {                                   // un publicador que sigue vivo hasta que lo maten
                try (var pub = new Publicador(Kf.bootstrap(), Publicador.Reclamo.valueOf(args[1]), Integer.parseInt(args[2]), false)) {
                    pub.arrancar(100);
                    Thread.sleep(Long.MAX_VALUE);
                }
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
