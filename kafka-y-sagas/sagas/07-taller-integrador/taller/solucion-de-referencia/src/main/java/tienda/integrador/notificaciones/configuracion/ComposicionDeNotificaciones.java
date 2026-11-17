package tienda.integrador.notificaciones.configuracion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tienda.integrador.notificaciones.adaptadores.entrada.kafka.EscuchaDeResultados;
import tienda.integrador.notificaciones.adaptadores.salida.jdbc.NotificacionesJdbc;
import tienda.integrador.notificaciones.aplicacion.ServicioDeNotificaciones;

/** Raíz de composición de Notificaciones. */
@Configuration
@ConditionalOnProperty(name = "tienda.servicio", havingValue = "notificaciones")
class ComposicionDeNotificaciones {

    @Bean
    NotificacionesJdbc notificacionesJdbc(JdbcTemplate jdbc) {
        return new NotificacionesJdbc(jdbc);
    }

    @Bean
    ServicioDeNotificaciones servicioDeNotificaciones(NotificacionesJdbc jdbc) {
        return new ServicioDeNotificaciones(jdbc);
    }

    @Bean
    EscuchaDeResultados escuchaDeResultados(ServicioDeNotificaciones servicio) {
        return new EscuchaDeResultados(servicio);
    }
}
