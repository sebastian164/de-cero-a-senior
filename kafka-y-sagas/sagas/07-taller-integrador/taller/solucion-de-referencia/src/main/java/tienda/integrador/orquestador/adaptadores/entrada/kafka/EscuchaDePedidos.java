package tienda.integrador.orquestador.adaptadores.entrada.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tienda.integrador.orquestador.aplicacion.Puertos.IniciarSaga;
import tienda.integrador.orquestador.aplicacion.Puertos.IniciarSaga.Inicio;
import tienda.integrador.plataforma.Json;
import tienda.integrador.plataforma.MensajeIlegible;

/** Adaptador de entrada: el evento PedidoCreado de Pedidos inicia una saga (idempotente por id de pedido). */
public final class EscuchaDePedidos {
    private final IniciarSaga puerto;

    public EscuchaDePedidos(IniciarSaga puerto) {
        this.puerto = puerto;
    }

    @KafkaListener(topics = "compra.pedidos", groupId = "orquestador-pedidos", concurrency = "${tienda.concurrencia:3}")
    void alRecibir(ConsumerRecord<String, String> registro) {
        JsonNode n = Json.leer(registro.value());
        JsonNode d = n.get("datos");
        if (d == null || !d.has("pedidoId")) throw new MensajeIlegible("PedidoCreado sin datos: " + registro.value(), null);
        String pedido = d.get("pedidoId").asText();
        puerto.iniciar(new Inicio(pedido, n.path("correlacion").asText(pedido), d.get("centavos").asLong(), d.get("producto").asText(), d.get("cantidad").asInt(), d.get("ciudad").asText()));
    }
}
