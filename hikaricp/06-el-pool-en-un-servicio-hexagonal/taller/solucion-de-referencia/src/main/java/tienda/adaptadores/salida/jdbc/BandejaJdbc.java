package tienda.adaptadores.salida.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;

/**
 * Paso 8, el arreglo: publicar es insertar en la bandeja de salida, dentro de la misma transacción y en milisegundos.
 * La entrega al servicio lento la hace después un mensajero, sin ninguna conexión en la mano mientras espera.
 */
public final class BandejaJdbc implements PublicadorDeEventos {

    private final UnidadDeTrabajoJdbc transacciones;

    public BandejaJdbc(UnidadDeTrabajoJdbc transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public void publicar(EventoDePedido evento) {
        transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(
                    "insert into evento_pendiente (id, tipo, pedido_id, cliente, ocurrido_en) values (?, ?, ?, ?, ?)")) {
                sentencia.setString(1, evento.id());
                sentencia.setString(2, evento.tipo());
                sentencia.setString(3, evento.pedidoId());
                sentencia.setString(4, evento.cliente());
                sentencia.setObject(5, OffsetDateTime.ofInstant(evento.ocurridoEn(), ZoneOffset.UTC));
                return sentencia.executeUpdate();
            }
        });
    }

    /**
     * Lee un lote en una transacción corta, lo entrega SIN conexión y borra SOLO lo que se entregó, en otra transacción corta.
     * La entrega devuelve los ids que salieron bien: lo que falló se queda en la bandeja para la vuelta siguiente (al menos una vez).
     * Devuelve cuántos eventos se entregaron y se borraron.
     */
    public int entregarPendientes(int lote, Function<List<EventoDePedido>, Set<String>> entrega) {
        List<EventoDePedido> pendientes = transacciones.ejecutar(() -> transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(
                    "select id, tipo, pedido_id, cliente, ocurrido_en from evento_pendiente order by ocurrido_en, id limit ?")) {
                sentencia.setInt(1, lote);
                List<EventoDePedido> leidos = new ArrayList<>();
                try (ResultSet fila = sentencia.executeQuery()) {
                    while (fila.next()) {
                        leidos.add(new EventoDePedido(fila.getString("id"), fila.getString("tipo"), fila.getString("pedido_id"),
                                fila.getString("cliente"), fila.getObject("ocurrido_en", OffsetDateTime.class).toInstant()));
                    }
                }
                return leidos;
            }
        }));
        if (pendientes.isEmpty()) {
            return 0;
        }
        Set<String> entregados = entrega.apply(pendientes);       // lo lento, fuera de cualquier transacción
        if (entregados.isEmpty()) {
            return 0;
        }
        return transacciones.ejecutar(() -> transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement("delete from evento_pendiente where id = any (?)")) {
                sentencia.setArray(1, conexion.createArrayOf("varchar", entregados.toArray()));
                return sentencia.executeUpdate();
            }
        }));
    }

    public long pendientes() {
        return transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement("select count(*) from evento_pendiente");
                 ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        });
    }
}
