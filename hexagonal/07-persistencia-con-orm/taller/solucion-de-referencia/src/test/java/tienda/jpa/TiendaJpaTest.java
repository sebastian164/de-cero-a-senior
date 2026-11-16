package tienda.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tienda.InventarioConInterferencia;
import tienda.PedidosQueFallan;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.VistaDePedido;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Resultado;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;
import tienda.aplicacion.puertos.salida.Reloj;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.ReglaDeNegocioViolada;

/** El núcleo de la sesión 06 sobre AlmacenJpa y PostgreSQL 17: todo o nada, idempotencia, concurrencia y N+1. */
@SpringBootTest(classes = AplicacionTienda.class)
class TiendaJpaTest extends ConPostgres {

    @Autowired
    ServicioDePedidos tienda;

    @Autowired
    AlmacenJpa almacen;

    @Autowired
    CatalogoJpa catalogo;

    @Autowired
    UnidadDeTrabajo unidadDeTrabajo;

    @Autowired
    Reloj reloj;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory fabrica;

    @Autowired
    PlatformTransactionManager gestor;

    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager entidades;

    @Test
    void crearConfirmarYCancelarConDosLineas() {
        existencias("teclado", 10);
        existencias("raton", 3);
        VistaDePedido creado = tienda.crear(solicitud("ana", "teclado:2", "raton:1")).pedido();
        assertEquals(36_000_000L, creado.totalCentavos());
        assertEquals(List.of(8, 2), List.of(unidades("teclado"), unidades("raton")));
        assertEquals(2, jdbc.queryForObject("select count(*) from linea_de_pedido where pedido_id = ?", Integer.class, creado.id()));
        assertEquals("CONFIRMADO", tienda.confirmar(creado.id()).estado());
        assertThrows(ReglaDeNegocioViolada.class, () -> tienda.confirmar(creado.id()));
        assertEquals("CANCELADO", tienda.cancelar(creado.id()).estado());
        assertEquals(List.of(10, 3), List.of(unidades("teclado"), unidades("raton")));
        assertEquals(2L, jdbc.queryForObject("select version from pedido where id = ?", Long.class, creado.id()));
    }

    @Test
    void unFalloAlGuardarElPedidoNoDejaExistenciasDescontadas() {
        existencias("teclado", 10);
        PedidosQueFallan pedidos = new PedidosQueFallan(almacen);
        ServicioDePedidos conFallo = new ServicioDePedidos(pedidos, catalogo, almacen, almacen, unidadDeTrabajo, reloj, almacen);
        String clave = UUID.randomUUID().toString();
        pedidos.averiar();
        assertThrows(IllegalStateException.class, () -> conFallo.crear(new Solicitud(clave, "falla", List.of(new Linea("teclado", 2)))));
        assertEquals(10, unidades("teclado"));
        assertEquals(0, jdbc.queryForObject("select count(*) from peticion_procesada where clave = ?", Integer.class, clave));
        pedidos.reparar();
        assertTrue(conFallo.crear(new Solicitud(clave, "falla", List.of(new Linea("teclado", 2)))).nuevo());
        assertEquals(8, unidades("teclado"));
    }

    @Test
    void laMismaClaveEnOchoHilosCreaUnSoloPedido() {
        existencias("raton", 3);
        String clave = UUID.randomUUID().toString();
        String cliente = "misma-clave-" + clave;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        AtomicInteger nuevos = new AtomicInteger();
        Map<String, Integer> otros = new ConcurrentHashMap<>();
        try (ExecutorService grupo = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                grupo.submit(() -> {
                    try {
                        Resultado resultado = tienda.crear(new Solicitud(clave, cliente, List.of(new Linea("raton", 1))));
                        ids.add(resultado.pedido().id());
                        if (resultado.nuevo()) {
                            nuevos.incrementAndGet();
                        }
                    } catch (ConflictoDeConcurrencia rendido) {
                        otros.merge("rendido", 1, Integer::sum);
                    } catch (RuntimeException error) {
                        otros.merge(error.getClass().getName(), 1, Integer::sum);
                    }
                });
            }
        }
        assertEquals(1, ids.size());
        assertEquals(1, nuevos.get());
        assertTrue(otros.keySet().stream().allMatch("rendido"::equals), "solo conflictos: " + otros);
        assertEquals(1, tienda.deCliente(cliente).size());
        assertEquals(2, unidades("raton"));
    }

    @Test
    void elConflictoDeVersionLlegaAlCasoDeUsoQueReintentaYNoVendeLaUnidadDosVeces() {
        existencias("monitor", 1);
        InventarioConInterferencia inventario = new InventarioConInterferencia(almacen);
        ServicioDePedidos conInterferencia = new ServicioDePedidos(almacen, catalogo, inventario, almacen, unidadDeTrabajo, reloj, almacen);
        String sufijo = UUID.randomUUID().toString();
        inventario.interferir(1, () -> conInterferencia.crear(new Solicitud("otro-" + sufijo, "luis-" + sufijo, List.of(new Linea("monitor", 1)))));
        assertThrows(ReglaDeNegocioViolada.class,
                () -> conInterferencia.crear(new Solicitud("ana-" + sufijo, "ana-" + sufijo, List.of(new Linea("monitor", 1)))));
        assertEquals(2, inventario.lecturas(), "hubo reintento");
        assertEquals(0, unidades("monitor"));
        assertEquals(List.of(0, 1), List.of(tienda.deCliente("ana-" + sufijo).size(), tienda.deCliente("luis-" + sufijo).size()));
    }

    @Test
    void muchosHilosNoVendenMasDeLoQueHay() {
        existencias("teclado", 60);
        String prefijo = "hilo-" + UUID.randomUUID() + "-";
        AtomicInteger creados = new AtomicInteger();
        AtomicInteger sinExistencias = new AtomicInteger();
        AtomicInteger rendidos = new AtomicInteger();
        Map<String, Integer> otros = new ConcurrentHashMap<>();
        try (ExecutorService grupo = Executors.newFixedThreadPool(8)) {
            for (int h = 0; h < 8; h++) {
                int hilo = h;
                grupo.submit(() -> {
                    for (int i = 0; i < 15; i++) {
                        try {
                            tienda.crear(new Solicitud(prefijo + hilo + "-" + i, prefijo + hilo, List.of(new Linea("teclado", 1 + (hilo + i) % 3))));
                            creados.incrementAndGet();
                        } catch (ConflictoDeConcurrencia conflicto) {
                            rendidos.incrementAndGet();
                        } catch (ReglaDeNegocioViolada sinUnidades) {
                            sinExistencias.incrementAndGet();
                        } catch (RuntimeException error) {
                            otros.merge(error.getClass().getName(), 1, Integer::sum);
                        }
                    }
                });
            }
        }
        List<VistaDePedido> todos = new ArrayList<>();
        for (int h = 0; h < 8; h++) {
            todos.addAll(tienda.deCliente(prefijo + h));
        }
        int vendidas = todos.stream().mapToInt(pedido -> pedido.lineas().get(0).cantidad()).sum();
        System.out.println("JPA tienda: " + creados + " creados, " + sinExistencias + " sin existencias, " + rendidos + " rendidos, otros " + otros);
        assertEquals(Map.of(), otros);
        assertEquals(60, vendidas + unidades("teclado"));
        assertEquals(creados.get(), todos.size());
        assertEquals(120, creados.get() + sinExistencias.get() + rendidos.get());
    }

    @Test
    void listarPedidosConSusLineasEsUnaSolaConsulta() {
        existencias("teclado", 100);
        existencias("raton", 100);
        String cliente = "n1-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            tienda.crear(new Solicitud(cliente + "-" + i, cliente, List.of(new Linea("teclado", 1), new Linea("raton", 1))));
        }
        Statistics estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        tienda.porId(tienda.deCliente(cliente).get(0).id());
        long unoConDeCliente = estadisticas.getPrepareStatementCount();

        estadisticas.clear();
        List<VistaDePedido> diez = tienda.deCliente(cliente);
        long conJoinFetch = estadisticas.getPrepareStatementCount();

        estadisticas.clear();
        // La primera versión del adaptador: sin "join fetch". Cada pedido trae sus líneas al recorrerlas.
        int lineasIngenuas = new TransactionTemplate(gestor).execute(estado -> entidades
                .createQuery("select p from PedidoJpa p where p.cliente = :cliente", PedidoJpa.class)
                .setParameter("cliente", cliente).getResultList().stream()
                .map(PedidoJpa::aDominio).mapToInt(pedido -> pedido.lineas().size()).sum());
        long sinJoinFetch = estadisticas.getPrepareStatementCount();

        System.out.println("Consultas: buscar uno 1 (más 1 de listar) = " + unoConDeCliente + "; listar 10 con join fetch = " + conJoinFetch
                + "; listar 10 sin join fetch = " + sinJoinFetch);
        assertEquals(10, diez.size());
        assertEquals(20, lineasIngenuas);
        assertEquals(2, unoConDeCliente, "una para listar y una para buscar");
        assertEquals(1, conJoinFetch);
        assertEquals(11, sinJoinFetch, "1 + N");
    }

    private void existencias(String producto, int unidades) {
        jdbc.update("update existencias set unidades = ?, version = version + 1 where producto = ?", unidades, producto);
    }

    private int unidades(String producto) {
        return jdbc.queryForObject("select unidades from existencias where producto = ?", Integer.class, producto);
    }

    private static Solicitud solicitud(String cliente, String... lineas) {
        return new Solicitud(UUID.randomUUID().toString(), cliente, java.util.Arrays.stream(lineas)
                .map(texto -> new Linea(texto.split(":")[0], Integer.parseInt(texto.split(":")[1]))).toList());
    }
}
