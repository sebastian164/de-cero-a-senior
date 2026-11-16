package tienda.antes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Antes: cinco @Value sueltos, sin validación. Un "9" en reintentos pasa sin aviso. */
@Component
public class PagosConValue {
    @Value("${pagos.clave-api}") public String claveApi;
    @Value("${pagos.tiempo-limite}") public Duration tiempoLimite;
    @Value("${pagos.reintentos}") public int reintentos;
    @Value("${pagos.moneda}") public String moneda;
    @Value("${pagos.url-pasarela}") public String urlPasarela;
}
