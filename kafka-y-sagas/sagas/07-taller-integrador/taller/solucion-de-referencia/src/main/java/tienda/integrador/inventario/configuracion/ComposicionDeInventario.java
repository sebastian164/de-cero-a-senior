package tienda.integrador.inventario.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.inventario.adaptadores.entrada.kafka.EscuchaDeOrdenes;
import tienda.integrador.inventario.adaptadores.salida.jdbc.InventarioJdbc;
import tienda.integrador.inventario.aplicacion.ServicioDeInventario;

/** Raíz de composición de Inventario: el único sitio que crea sus adaptadores y los conecta con el servicio de aplicación. */
@Configuration
@ConditionalOnProperty(name = "tienda.servicio", havingValue = "inventario")
class ComposicionDeInventario {

    @Bean
    InventarioJdbc inventarioJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        return new InventarioJdbc(jdbc, tx);
    }

    @Bean
    ServicioDeInventario servicioDeInventario(InventarioJdbc jdbc, @Value("${tienda.falla-compensacion:false}") boolean falla) {
        return new ServicioDeInventario(jdbc, jdbc, jdbc, jdbc, falla);
    }

    @Bean
    EscuchaDeOrdenes escuchaDeOrdenes(ServicioDeInventario servicio, @Value("${tienda.muere-tras:0}") long muereTras) {
        return new EscuchaDeOrdenes(servicio, muereTras);
    }
}
