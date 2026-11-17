package tienda.integrador.pedidos.adaptadores.entrada.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tienda.integrador.pedidos.aplicacion.Puertos.RegistrarResultadoDeSaga;
import tienda.integrador.pedidos.aplicacion.Puertos.RegistrarResultadoDeSaga.Resultado;
import tienda.integrador.plataforma.Json;

/** Adaptador de entrada: el resultado de la saga (SagaCompletada o SagaCancelada) actualiza el pedido. Solo traduce y llama al puerto. */
public final class EscuchaDeResultados {
    private final RegistrarResultadoDeSaga puerto;

    public EscuchaDeResultados(RegistrarResultadoDeSaga puerto) {
        this.puerto = puerto;
    }

    @KafkaListener(topics = "compra.resultados", groupId = "pedidos", concurrency = "${tienda.concurrencia:3}")
    void alRecibir(ConsumerRecord<String, String> registro) {
        JsonNode n = Json.leer(registro.value());
        puerto.registrar(new Resultado(Json.texto(n, "id"), Json.texto(n, "sagaId"), n.path("correlacion").asText(), Json.texto(n, "tipo").equals("SagaCompletada"),
                n.path("datos").path("motivo").asText("")));
    }
}
