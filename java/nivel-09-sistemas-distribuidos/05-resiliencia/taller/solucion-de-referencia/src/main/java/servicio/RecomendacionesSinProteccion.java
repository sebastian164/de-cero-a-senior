package servicio;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** La misma dependencia SIN mamparo ni cortacircuitos, con un tiempo límite generoso (6 s). Es el "antes". */
@Service
public class RecomendacionesSinProteccion {

    private final RestClient cliente;

    RecomendacionesSinProteccion(@Qualifier("recomendacionesLento") RestClient cliente) { this.cliente = cliente; }

    public Recomendaciones.Resultado recomendar(long id) {
        cliente.get().uri("/datos/{id}", id).retrieve().body(Map.class);
        return new Recomendaciones.Resultado(List.of("recomendado-para-" + id), false);
    }
}
