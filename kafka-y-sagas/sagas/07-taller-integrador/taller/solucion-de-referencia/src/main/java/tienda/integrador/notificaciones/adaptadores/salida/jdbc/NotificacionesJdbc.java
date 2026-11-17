package tienda.integrador.notificaciones.adaptadores.salida.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import tienda.integrador.notificaciones.aplicacion.Puertos.RegistroDeNotificaciones;

/** Adaptador de salida de Notificaciones: su base (esquema s07_notificaciones). */
public final class NotificacionesJdbc implements RegistroDeNotificaciones {

    private final JdbcTemplate jdbc;

    public NotificacionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("CREATE TABLE IF NOT EXISTS notificaciones(evento_id text PRIMARY KEY, pedido_id text, mensaje text)");
    }

    @Override
    public boolean registrar(String eventoId, String pedidoId, String mensaje) {
        return jdbc.update("INSERT INTO notificaciones(evento_id, pedido_id, mensaje) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", eventoId, pedidoId, mensaje) == 1;
    }
}
