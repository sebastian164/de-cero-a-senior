package tienda.integrador.inventario.adaptadores.entrada.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tienda.integrador.inventario.aplicacion.Puertos.AtenderOrden;
import tienda.integrador.inventario.aplicacion.Puertos.AtenderOrden.Orden;
import tienda.integrador.plataforma.Json;
import tienda.integrador.plataforma.Muerte;

/** Adaptador de entrada: traduce la orden del orquestador y llama al puerto. La posición se confirma cuando el puerto retorna. */
public final class EscuchaDeOrdenes {
    private final AtenderOrden puerto;
    private final long muereTras;

    public EscuchaDeOrdenes(AtenderOrden puerto, long muereTras) {
        this.puerto = puerto;
        this.muereTras = muereTras;
    }

    @KafkaListener(topics = "compra.ordenes.inventario", groupId = "inventario", concurrency = "${tienda.concurrencia:3}")
    void alRecibir(ConsumerRecord<String, String> registro) {
        JsonNode n = Json.leer(registro.value());
        JsonNode d = n.get("datos");
        if (d == null) throw new tienda.integrador.plataforma.MensajeIlegible("orden sin datos: " + registro.value(), null);
        String saga = Json.texto(n, "sagaId");
        puerto.atender(new Orden(Json.texto(n, "id"), saga, n.path("correlacion").asText(saga), Json.texto(n, "tipo"), d.path("centavos").asLong(), d.path("producto").asText(),
                d.path("cantidad").asInt(), d.path("ciudad").asText()));
        Muerte.siToca(muereTras);
    }
}
