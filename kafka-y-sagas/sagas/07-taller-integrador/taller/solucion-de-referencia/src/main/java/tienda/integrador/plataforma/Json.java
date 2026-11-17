package tienda.integrador.plataforma;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Lectura común de los mensajes: todos llevan "id". Lo que no se pueda leer es MensajeIlegible. Solo la usan adaptadores. */
public final class Json {
    private static final ObjectMapper JSON = new ObjectMapper();

    private Json() {
    }

    public static JsonNode leer(String json) {
        try {
            JsonNode n = JSON.readTree(json);
            if (n == null || !n.has("id")) throw new MensajeIlegible("sin id: " + json, null);
            return n;
        } catch (JsonProcessingException e) {
            throw new MensajeIlegible("ilegible: " + json, e);
        }
    }

    public static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        if (v == null || v.isNull()) throw new MensajeIlegible("falta " + campo + ": " + n, null);
        return v.asText();
    }
}
