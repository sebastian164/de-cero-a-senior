package tienda.adaptadores.salida.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.dominio.Dinero;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.LineaDePedido;
import tienda.dominio.Pedido;

/** Paso 2: RepositorioDePedidos con JDBC. No pide conexiones: usa la de la transacción en curso. Control optimista con la columna version. */
public final class PedidosJdbc implements RepositorioDePedidos {

    private static final String LECTURA = "select p.id, p.cliente, p.estado, p.creado_en, p.version, l.producto, l.cantidad, l.precio_unitario_centavos "
            + "from pedido p left join linea_de_pedido l on l.pedido_id = p.id ";

    private final UnidadDeTrabajoJdbc transacciones;

    public PedidosJdbc(UnidadDeTrabajoJdbc transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public String siguienteId() {
        return "pedido-" + UUID.randomUUID();
    }

    @Override
    public void guardar(Pedido pedido) {
        String clave = "pedido:" + pedido.id();
        transacciones.conConexion(conexion -> {
            Long leida = transacciones.versiones().get(clave);
            String sql = "update pedido set estado = ?, version = version + 1 where id = ?" + (leida == null ? "" : " and version = ?") + " returning version";
            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
                sentencia.setString(1, pedido.estado().name());
                sentencia.setString(2, pedido.id());
                if (leida != null) {
                    sentencia.setLong(3, leida);
                }
                try (ResultSet fila = sentencia.executeQuery()) {
                    if (fila.next()) {
                        transacciones.versiones().put(clave, fila.getLong(1));
                        return null;
                    }
                }
            }
            if (leida != null) {
                throw new ConflictoDeConcurrencia("el pedido " + pedido.id());   // otro lo cambió entre que se leyó y se guardó
            }
            insertar(conexion, pedido);
            transacciones.versiones().put(clave, 0L);
            return null;
        });
    }

    private static void insertar(java.sql.Connection conexion, Pedido pedido) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "insert into pedido (id, cliente, estado, creado_en, version) values (?, ?, ?, ?, 0)")) {
            sentencia.setString(1, pedido.id());
            sentencia.setString(2, pedido.cliente());
            sentencia.setString(3, pedido.estado().name());
            sentencia.setObject(4, OffsetDateTime.ofInstant(pedido.creadoEn(), ZoneOffset.UTC));
            sentencia.executeUpdate();
        }
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "insert into linea_de_pedido (pedido_id, posicion, producto, cantidad, precio_unitario_centavos) values (?, ?, ?, ?, ?)")) {
            int posicion = 0;
            for (LineaDePedido linea : pedido.lineas()) {
                sentencia.setString(1, pedido.id());
                sentencia.setInt(2, posicion++);
                sentencia.setString(3, linea.producto());
                sentencia.setInt(4, linea.cantidad());
                sentencia.setLong(5, linea.precioUnitario().centavos());
                sentencia.addBatch();
            }
            sentencia.executeBatch();
        }
    }

    @Override
    public Optional<Pedido> buscar(String pedidoId) {
        return transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(LECTURA + "where p.id = ? order by l.posicion")) {
                sentencia.setString(1, pedidoId);
                try (ResultSet filas = sentencia.executeQuery()) {
                    return armar(filas).stream().findFirst();
                }
            }
        });
    }

    @Override
    public List<Pedido> deCliente(String cliente) {
        return transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(LECTURA + "where p.cliente = ? order by p.creado_en, p.id, l.posicion")) {
                sentencia.setString(1, cliente);
                try (ResultSet filas = sentencia.executeQuery()) {
                    return armar(filas);
                }
            }
        });
    }

    /** Una fila por línea: se agrupan por pedido, en el orden en que llegan. Recuerda la versión de cada pedido leído. */
    private List<Pedido> armar(ResultSet filas) throws SQLException {
        Map<String, List<LineaDePedido>> lineas = new LinkedHashMap<>();
        Map<String, Object[]> cabeceras = new LinkedHashMap<>();
        while (filas.next()) {
            String id = filas.getString("id");
            cabeceras.computeIfAbsent(id, x -> {
                try {
                    return new Object[] {filas.getString("cliente"), EstadoDePedido.valueOf(filas.getString("estado")),
                            filas.getObject("creado_en", OffsetDateTime.class).toInstant(), filas.getLong("version")};
                } catch (SQLException error) {
                    throw new IllegalStateException(error);
                }
            });
            List<LineaDePedido> deEste = lineas.computeIfAbsent(id, x -> new ArrayList<>());
            String producto = filas.getString("producto");
            if (producto != null) {
                deEste.add(new LineaDePedido(producto, filas.getInt("cantidad"), new Dinero(filas.getLong("precio_unitario_centavos"))));
            }
        }
        List<Pedido> pedidos = new ArrayList<>();
        cabeceras.forEach((id, c) -> {
            transacciones.versiones().put("pedido:" + id, (Long) c[3]);
            pedidos.add(Pedido.existente(id, (String) c[0], lineas.get(id), (EstadoDePedido) c[1], (Instant) c[2]));
        });
        return pedidos;
    }
}
