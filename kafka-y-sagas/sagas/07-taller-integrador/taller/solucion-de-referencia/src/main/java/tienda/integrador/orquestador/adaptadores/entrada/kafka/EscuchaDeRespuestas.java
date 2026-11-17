package tienda.integrador.orquestador.adaptadores.entrada.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tienda.integrador.orquestador.aplicacion.Puertos.AtenderRespuesta;
import tienda.integrador.orquestador.aplicacion.Puertos.AtenderRespuesta.Respuesta;
import tienda.integrador.plataforma.Json;
import tienda.integrador.plataforma.Muerte;

/** Adaptador de entrada: traduce la respuesta de un participante y llama al puerto. La posición se confirma cuando el puerto retorna. */
public final class EscuchaDeRespuestas {
    private final AtenderRespuesta puerto;
    private final long muereTras;

    public EscuchaDeRespuestas(AtenderRespuesta puerto, long muereTras) {
        this.puerto = puerto;
        this.muereTras = muereTras;
    }

    @KafkaListener(topics = "compra.respuestas", groupId = "orquestador", concurrency = "${tienda.concurrencia:3}")
    void alRecibir(ConsumerRecord<String, String> registro) {
        JsonNode n = Json.leer(registro.value());
        String saga = Json.texto(n, "sagaId");
        puerto.atender(new Respuesta(Json.texto(n, "id"), saga, n.path("correlacion").asText(saga), Json.texto(n, "tipo")));
        Muerte.siToca(muereTras);
    }
}
