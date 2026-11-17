package inventario.adaptadores.salida.kafka;

import inventario.aplicacion.Puertos.PublicadorDeResultados;
import inventario.dominio.ResultadoDeReserva;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;

/** Adaptador de salida: InventarioReservado o InventarioInsuficiente, con el id del pedido como clave. */
public class PublicadorKafka implements PublicadorDeResultados {

    private final KafkaTemplate<String, String> kafka;

    public PublicadorKafka(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publicar(String eventoId, String pedidoId, ResultadoDeReserva resultado) {
        String tipo = resultado == ResultadoDeReserva.RESERVADO ? "InventarioReservado" : "InventarioInsuficiente";
        String json = "{\"id\":\"res-" + eventoId + "\",\"tipo\":\"" + tipo + "\",\"version\":1,\"datos\":{\"pedidoId\":\"" + pedidoId + "\"}}";
        try {
            kafka.send("inventario.eventos", pedidoId, json).get(30, TimeUnit.SECONDS);   // si falla, el mensaje se reintenta
        } catch (Exception e) {
            throw new IllegalStateException("no se pudo publicar " + tipo + " de " + pedidoId, e);
        }
    }
}
