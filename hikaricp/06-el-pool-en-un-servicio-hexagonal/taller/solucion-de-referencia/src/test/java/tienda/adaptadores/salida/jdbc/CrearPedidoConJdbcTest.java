package tienda.adaptadores.salida.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tienda.adaptadores.salida.http.NotificadorHttp;
import tienda.adaptadores.salida.sistema.RelojDelSistema;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Resultado;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;
import tienda.composicion.TiendaConPool;
import tienda.dominio.ReglaDeNegocioViolada;

/** El caso de uso "crear pedido" del módulo hexagonal, sin cambios, sobre los adaptadores JDBC y PostgreSQL real, y la bandeja de salida. */
class CrearPedidoConJdbcTest {

    private static HikariDataSource pool;
    private static UnidadDeTrabajoJdbc transacciones;
    private static BandejaJdbc bandeja;
    private static String producto;

    @BeforeAll
    static void preparar() throws Exception {
        pool = ConPostgresTest.nuevoPool(6, 2_000);
        try (Connection conexion = pool.getConnection(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute(Files.readString(Path.of(System.getProperty("schema"))));
            producto = "taller-" + UUID.randomUUID();
            sentencia.execute("insert into producto (id, precio_centavos) values ('" + producto + "', 1000)");
            sentencia.execute("insert into existencias (producto, unidades, version) values ('" + producto + "', 100, 0)");
        }
        transacciones = new UnidadDeTrabajoJdbc(pool);
        bandeja = new BandejaJdbc(transacciones);
    }

    @AfterAll
    static void cerrar() {
        pool.close();
    }

    private ServicioDePedidos servicio(PublicadorDeEventos eventos) {
        return new ServicioDePedidos(new PedidosJdbc(transacciones), new CatalogoJdbc(transacciones), new InventarioJdbc(transacciones),
                new PeticionesJdbc(transacciones), transacciones, new RelojDelSistema(), eventos);
    }

    private long unidades() throws Exception {
        try (Connection c = pool.getConnection(); PreparedStatement s = c.prepareStatement("select unidades from existencias where producto = ?")) {
            s.setString(1, producto);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    private long pendientesDe(String pedidoId) throws Exception {
        try (Connection c = pool.getConnection(); PreparedStatement s = c.prepareStatement("select count(*) from evento_pendiente where pedido_id = ?")) {
            s.setString(1, pedidoId);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    @Test
    void crearUnPedidoReservaGuardaYDejaElEventoEnLaBandejaEnLaMismaTransaccion() throws Exception {
        long antes = unidades();
        Resultado r = servicio(bandeja).crear(new Solicitud("clave-" + UUID.randomUUID(), "cliente-a", List.of(new Linea(producto, 2))));
        assertTrue(r.nuevo());
        assertEquals(antes - 2, unidades());
        assertEquals(2000, r.pedido().totalCentavos());
        assertEquals(1, pendientesDe(r.pedido().id()));
    }

    @Test
    void repetirLaClaveDevuelveElMismoPedidoSinReservarOtraVez() throws Exception {
        ServicioDePedidos servicio = servicio(bandeja);
        String clave = "clave-" + UUID.randomUUID();
        Resultado primero = servicio.crear(new Solicitud(clave, "cliente-b", List.of(new Linea(producto, 1))));
        long despues = unidades();
        Resultado segundo = servicio.crear(new Solicitud(clave, "cliente-b", List.of(new Linea(producto, 1))));
        assertFalse(segundo.nuevo());
        assertEquals(primero.pedido().id(), segundo.pedido().id());
        assertEquals(despues, unidades());
    }

    @Test
    void siElServicioDeEventosFallaDentroDeLaUnidadDeTrabajoSeRevierteTodo() throws Exception {
        long antes = unidades();
        PublicadorDeEventos roto = evento -> {
            throw new IllegalStateException("notificaciones no responde");
        };
        assertThrows(IllegalStateException.class,
                () -> servicio(roto).crear(new Solicitud("clave-" + UUID.randomUUID(), "cliente-c", List.of(new Linea(producto, 1)))));
        assertEquals(antes, unidades());
    }

    @Test
    void siNoHayExistenciasNoQuedaNada() throws Exception {
        long antes = unidades();
        assertThrows(ReglaDeNegocioViolada.class,
                () -> servicio(bandeja).crear(new Solicitud("clave-" + UUID.randomUUID(), "cliente-d", List.of(new Linea(producto, 1000)))));
        assertEquals(antes, unidades());
    }

    @Test
    void siLaEntregaFallaElEventoSeQuedaEnLaBandejaYSaleCuandoElServicioVuelve() throws Exception {
        // limpia lo que dejaron otras pruebas, para contar solo los de esta
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            s.execute("delete from evento_pendiente");
        }
        ServicioDePedidos servicio = servicio(bandeja);
        for (int i = 0; i < 3; i++) {
            servicio.crear(new Solicitud("clave-" + UUID.randomUUID(), "cliente-e", List.of(new Linea(producto, 1))));
        }
        assertEquals(3, bandeja.pendientes());

        AtomicBoolean caido = new AtomicBoolean(true);
        AtomicLong recibidas = new AtomicLong();
        HttpServer externo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        externo.createContext("/notificar", intercambio -> {
            int estado = caido.get() ? 503 : 200;
            if (estado == 200) {
                recibidas.incrementAndGet();
            }
            intercambio.sendResponseHeaders(estado, -1);
            intercambio.close();
        });
        externo.start();
        try {
            NotificadorHttp notificador = new NotificadorHttp("http://127.0.0.1:" + externo.getAddress().getPort());
            AtomicLong entregados = new AtomicLong();
            AtomicLong fallidos = new AtomicLong();

            int conElServicioCaido = bandeja.entregarPendientes(500, lote -> TiendaConPool.entregar(lote, notificador, entregados, fallidos));
            assertEquals(0, conElServicioCaido);
            assertEquals(3, bandeja.pendientes(), "con el servicio caído no se borra nada");
            assertEquals(3, fallidos.get());

            caido.set(false);
            int alVolver = bandeja.entregarPendientes(500, lote -> TiendaConPool.entregar(lote, notificador, entregados, fallidos));
            assertEquals(3, alVolver);
            assertEquals(0, bandeja.pendientes());
            assertEquals(3, recibidas.get(), "el servicio recibió los tres, ni uno menos");
            assertEquals(3, entregados.get());
        } finally {
            externo.stop(0);
        }
    }

    @Test
    void siElMensajeroCaeEntreEntregarYBorrarSeReenviaConElMismoId() throws Exception {
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            s.execute("delete from evento_pendiente");
        }
        ServicioDePedidos servicio = servicio(bandeja);
        for (int i = 0; i < 3; i++) {
            servicio.crear(new Solicitud("clave-" + UUID.randomUUID(), "cliente-f", List.of(new Linea(producto, 1))));
        }
        java.util.List<String> recibidos = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        HttpServer externo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        externo.createContext("/notificar", intercambio -> {
            recibidos.add(intercambio.getRequestURI().getQuery());
            intercambio.sendResponseHeaders(200, -1);
            intercambio.close();
        });
        externo.start();
        try {
            NotificadorHttp notificador = new NotificadorHttp("http://127.0.0.1:" + externo.getAddress().getPort());
            AtomicLong entregados = new AtomicLong();
            AtomicLong fallidos = new AtomicLong();

            assertThrows(IllegalStateException.class, () -> bandeja.entregarPendientes(500, lote -> {
                TiendaConPool.entregar(lote, notificador, entregados, fallidos);
                throw new IllegalStateException("cae antes de borrar");
            }));
            assertEquals(3, recibidos.size(), "el servicio ya los recibió");
            assertEquals(3, bandeja.pendientes(), "pero como el mensajero cayó antes de borrar, siguen pendientes");

            assertEquals(3, bandeja.entregarPendientes(500, lote -> TiendaConPool.entregar(lote, notificador, entregados, fallidos)));
            assertEquals(0, bandeja.pendientes());
            assertEquals(6, recibidos.size(), "al menos una vez: se reenvían los tres");
            assertEquals(3, new java.util.HashSet<>(recibidos).size(), "con el mismo id: el receptor puede deduplicar");
        } finally {
            externo.stop(0);
        }
    }
}
