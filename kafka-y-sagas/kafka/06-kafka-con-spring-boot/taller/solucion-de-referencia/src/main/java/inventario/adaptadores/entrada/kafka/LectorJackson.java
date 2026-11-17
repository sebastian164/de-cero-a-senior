package inventario.adaptadores.entrada.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import inventario.aplicacion.EventoInvalido;

/** Lector tolerante: ignora campos que no conoce; exige que existan los que usa. */
public final class LectorJackson implements LectorDeEventos {

    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    @Override
    public <T> T leer(String json, Class<T> tipo) {
        try {
            T valor = mapper.readValue(json, tipo);
            if (valor == null) throw new EventoInvalido("mensaje vacío");
            if (valor instanceof inventario.dominio.PedidoConfirmado p && (p.id() == null || p.pedidoId() == null || p.lineas() == null || p.lineas().isEmpty())) {
                throw new EventoInvalido("faltan campos obligatorios en " + json);
            }
            return valor;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new EventoInvalido("JSON ilegible: " + e.getOriginalMessage(), e);
        }
    }
}
