package inventario.adaptadores.salida.jdbc;

import inventario.aplicacion.Puertos.EventosVistos;
import inventario.aplicacion.Puertos.RepositorioDeExistencias;
import inventario.dominio.Existencias;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class InventarioJdbc implements RepositorioDeExistencias, EventosVistos {

    private final UnidadJdbc unidad;

    public InventarioJdbc(UnidadJdbc unidad) {
        this.unidad = unidad;
    }

    @Override
    public Optional<Existencias> buscar(String producto) {
        try (PreparedStatement ps = unidad.conexion().prepareStatement("SELECT disponibles FROM stock WHERE producto = ? FOR UPDATE")) {
            ps.setString(1, producto);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Existencias(producto, rs.getInt(1))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new BaseNoDisponible(e);
        }
    }

    @Override
    public void guardar(Existencias e) {
        try (PreparedStatement ps = unidad.conexion().prepareStatement("UPDATE stock SET disponibles = ? WHERE producto = ?")) {
            ps.setInt(1, e.disponibles());
            ps.setString(2, e.producto());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BaseNoDisponible(ex);
        }
    }

    @Override
    public void registrarReserva(String pedidoId, String producto, int cantidad) {
        try (PreparedStatement ps = unidad.conexion().prepareStatement("INSERT INTO reservas VALUES (?, ?, ?)")) {
            ps.setString(1, pedidoId);
            ps.setString(2, producto);
            ps.setInt(3, cantidad);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BaseNoDisponible(ex);
        }
    }

    @Override
    public boolean registrarSiEsNuevo(String eventoId) {
        try (PreparedStatement ps = unidad.conexion().prepareStatement("INSERT INTO vistos(evento_id) VALUES (?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, eventoId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new BaseNoDisponible(ex);
        }
    }

    /** La base no respondió: un fallo pasajero que el adaptador de entrada sabe reintentar. */
    public static final class BaseNoDisponible extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BaseNoDisponible(SQLException causa) {
            super(causa.getMessage(), causa);
        }
    }
}
