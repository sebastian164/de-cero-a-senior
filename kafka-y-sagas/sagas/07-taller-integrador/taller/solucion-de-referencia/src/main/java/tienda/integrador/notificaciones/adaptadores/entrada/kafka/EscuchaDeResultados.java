package tienda.integrador.notificaciones.adaptadores.entrada.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tienda.integrador.notificaciones.aplicacion.Puertos.ReaccionarAlResultado;
import tienda.integrador.notificaciones.aplicacion.Puertos.ReaccionarAlResultado.Resultado;
import tienda.integrador.plataforma.Json;

/** Adaptador de entrada: lee compra.resultados con su propio grupo; ninguna falla suya puede cambiar el destino de la compra. */
public final class EscuchaDeResultados {
    private final ReaccionarAlResultado puerto;

    public EscuchaDeResultados(ReaccionarAlResultado puerto) {
        this.puerto = puerto;
    }

    @KafkaListener(topics = "compra.resultados", groupId = "notificaciones", concurrency = "${tienda.concurrencia:3}")
    void alRecibir(ConsumerRecord<String, String> registro) {
        JsonNode n = Json.leer(registro.value());
        puerto.reaccionar(new Resultado(Json.texto(n, "id"), Json.texto(n, "sagaId"), n.path("correlacion").asText(), Json.texto(n, "tipo").equals("SagaCompletada"),
                n.path("datos").path("motivo").asText("")));
    }
}
