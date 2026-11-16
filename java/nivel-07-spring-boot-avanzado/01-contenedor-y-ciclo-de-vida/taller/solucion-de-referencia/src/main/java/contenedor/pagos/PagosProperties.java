package contenedor.pagos;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pagos")
public record PagosProperties(String proveedor, String claveApi) { }
