package tienda;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import tienda.config.PagosProperties;

/** Arranca la aplicación sin servidor web e imprime la configuración efectiva. */
public class MostrarConfig {
    public static void main(String[] args) {
        try (var ctx = new SpringApplicationBuilder(TiendaApplication.class).web(WebApplicationType.NONE).run(args)) {
            var p = ctx.getBean(PagosProperties.class);
            System.out.println("CONFIG reintentos=" + p.reintentos() + " tiempoLimite=" + p.tiempoLimite() + " clave=" + p.claveApi());
        }
    }
}
