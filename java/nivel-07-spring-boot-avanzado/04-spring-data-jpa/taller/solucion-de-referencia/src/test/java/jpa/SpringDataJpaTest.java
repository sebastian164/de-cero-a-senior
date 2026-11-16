package jpa;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class SpringDataJpaTest {

    @Autowired PedidoRepository pedidos;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void datos() {
        Datos.cargar(em, tx);
    }

    /** Recorre los pedidos tocando el cliente, como haría un mapeo a DTO. */
    private List<String> nombres(List<Pedido> lista) {
        return lista.stream().map(p -> p.getCliente().getNombre()).toList();
    }

    // ---- Taller 1: N+1 y tres correcciones ----

    @Test
    void ingenuoHace1MasNConsultas() {
        tx.executeWithoutResult(s -> {
            List<Pedido> lista = pedidos.findByEstadoOrderById(EstadoPedido.PENDIENTE);
            assertThat(nombres(lista)).hasSize(40);
        });
        assertThat(RegistroSql.selects()).isEqualTo(1 + 40);
    }

    @Test
    void joinFetchHaceUnaConsulta() {
        tx.executeWithoutResult(s -> assertThat(nombres(pedidos.conClientePorEstado(EstadoPedido.PENDIENTE))).hasSize(40));
        assertThat(RegistroSql.selects()).isEqualTo(1);
        assertThat(RegistroSql.sentencias().getFirst()).contains("join cliente");
    }

    @Test
    void entityGraphHaceUnaConsulta() {
        tx.executeWithoutResult(s -> assertThat(nombres(pedidos.findConClienteByEstado(EstadoPedido.PENDIENTE))).hasSize(40));
        assertThat(RegistroSql.selects()).isEqualTo(1);
        assertThat(RegistroSql.sentencias().getFirst()).contains("join cliente");
    }

    // La tercera corrección (carga por lotes) necesita otra propiedad: CargaPorLotesTest.

    // ---- Taller 3: proyección a record ----

    @Test
    void proyeccionSeleccionaSoloTresColumnasYNoGestionaEntidades() {
        List<PedidoResumen> resumen = pedidos.resumenDesde(Datos.HOY.minusDays(10));
        String sqlProyeccion = RegistroSql.sentencias().getLast();
        RegistroSql.limpiar();
        List<String> conEntidades = tx.execute(s ->
                nombres(pedidos.findByFechaGreaterThanEqual(Datos.HOY.minusDays(10))));
        List<String> sqlEntidades = RegistroSql.sentencias();

        assertThat(resumen).hasSize(10).allSatisfy(r -> assertThat(r.cliente()).startsWith("cliente-"));
        assertThat(conEntidades).hasSize(10);
        System.out.println("PROYECCION: " + sqlProyeccion);
        System.out.println("ENTIDADES (" + sqlEntidades.size() + "): " + sqlEntidades.getFirst());
        // proyección: una consulta con id, nombre y total; entidades: todas las columnas y 1 + 10 consultas
        assertThat(sqlProyeccion).contains("p1_0.id", "c1_0.nombre", "p1_0.total").doesNotContain("estado", "ciudad");
        assertThat(sqlEntidades).hasSize(11);
        assertThat(sqlEntidades.getFirst()).contains("estado", "fecha", "cliente_id");
    }

    // ---- Taller 4: Specification con cuatro criterios opcionales ----

    @Test
    void filtroDinamico() {
        assertThat(pedidos.findAll(PedidoFiltros.con(null, null, null, null))).hasSize(50);
        assertThat(pedidos.findAll(PedidoFiltros.con(null, EstadoPedido.ENVIADO, null, null))).hasSize(10);
        assertThat(pedidos.findAll(PedidoFiltros.con(null, EstadoPedido.PENDIENTE, 30_000L, null))).hasSize(11);
        assertThat(pedidos.findAll(PedidoFiltros.con(null, null, null, Datos.HOY.minusDays(5)))).hasSize(5);
        // "cliente-1" coincide con 1, 10..19 => 11; además pendientes, total >= 12000 y de los últimos 15 días => 12..15
        assertThat(pedidos.findAll(PedidoFiltros.con("cliente-1", EstadoPedido.PENDIENTE, 12_000L, Datos.HOY.minusDays(15))))
                .extracting(Pedido::getTotal).containsExactlyInAnyOrder(12_000L, 13_000L, 14_000L, 15_000L);
    }

    // ---- Taller 5: paginación en memoria con JOIN FETCH ----

    @Test
    void joinFetchConPaginacionPaginaEnMemoria(CapturedOutput salida) {
        Page<Pedido> pagina = tx.execute(s -> pedidos.conLineasPaginadoEnMemoria(PageRequest.of(0, 5)));
        assertThat(pagina.getContent()).hasSize(5);
        assertThat(salida.getAll()).contains("HHH90003004");
        // el SQL no tiene LIMIT/OFFSET: trae las 100 filas (50 pedidos x 2 líneas) y recorta en memoria
        assertThat(RegistroSql.sentencias()).filteredOn(q -> q.contains("join linea_pedido"))
                .singleElement().satisfies(q -> assertThat(q).doesNotContain("offset", "fetch first", "limit"));
        System.out.println("EN MEMORIA: " + RegistroSql.sentencias());
    }

    @Test
    void hibernate74BajaElJoinFetchSimpleAUnaSubconsulta(CapturedOutput salida) {
        Page<Pedido> pagina = tx.execute(s -> pedidos.conLineasPaginadoEnSubconsulta(PageRequest.of(0, 5)));
        assertThat(pagina.getContent()).hasSize(5);
        assertThat(salida.getAll()).doesNotContain("HHH90003004");
        assertThat(RegistroSql.sentencias()).filteredOn(q -> q.contains("join linea_pedido"))
                .singleElement().satisfies(q -> assertThat(q).contains("from (select", "fetch first ? rows only"));
    }

    @Test
    void resueltoPaginandoIdentificadores(CapturedOutput salida) {
        List<Pedido> pagina = tx.execute(s -> {
            Page<Long> ids = pedidos.idsPaginados(PageRequest.of(1, 5));
            List<Pedido> detalle = pedidos.conLineas(ids.getContent());
            detalle.forEach(p -> assertThat(p.getLineas()).hasSize(2));
            return detalle;
        });
        assertThat(pagina).hasSize(5);
        assertThat(salida.getAll()).doesNotContain("HHH90003004");
        assertThat(RegistroSql.sentencias()).anySatisfy(q -> assertThat(q).contains("offset"));
    }

    // ---- Taller 6: detección de cambios sin save ----

    @Test
    void detectaCambiosSinSave() {
        Long id = pedidos.findByEstadoOrderById(EstadoPedido.PENDIENTE).getFirst().getId();
        RegistroSql.limpiar();
        tx.executeWithoutResult(s -> {
            Pedido gestionado = em.find(Pedido.class, id);
            gestionado.setTotal(999_999L);           // sin pedidos.save(...)
        });
        assertThat(RegistroSql.updates()).isEqualTo(1);
        assertThat(pedidos.findById(id).orElseThrow().getTotal()).isEqualTo(999_999L);

        // fuera de una transacción la entidad está separada: el cambio no se guarda
        Pedido separado = pedidos.findById(id).orElseThrow();
        RegistroSql.limpiar();
        separado.setTotal(1L);
        tx.executeWithoutResult(s -> { });
        assertThat(RegistroSql.updates()).isZero();
        assertThat(pedidos.findById(id).orElseThrow().getTotal()).isEqualTo(999_999L);
    }
}
