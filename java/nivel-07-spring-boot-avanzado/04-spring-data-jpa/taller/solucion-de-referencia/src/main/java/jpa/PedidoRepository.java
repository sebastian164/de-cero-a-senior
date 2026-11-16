package jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    // Ingenua: provoca N+1 al recorrer p.getCliente()
    List<Pedido> findByEstadoOrderById(EstadoPedido estado);

    // Corrección 1: JOIN FETCH (el fragmento del README)
    @Query("select p from Pedido p join fetch p.cliente where p.estado = :estado")
    List<Pedido> conClientePorEstado(EstadoPedido estado);

    // Corrección 2: @EntityGraph
    @EntityGraph(attributePaths = "cliente")
    List<Pedido> findConClienteByEstado(EstadoPedido estado);

    @EntityGraph(attributePaths = {"lineas", "cliente"})
    Optional<Pedido> findConDetalleById(Long id);

    // Taller 3: proyección a record
    @Query("""
            select new jpa.PedidoResumen(p.id, c.nombre, p.total)
            from Pedido p join p.cliente c
            where p.fecha >= :desde
            """)
    List<PedidoResumen> resumenDesde(LocalDate desde);

    List<Pedido> findByFechaGreaterThanEqual(LocalDate desde);

    // Taller 5: JOIN FETCH de una colección + paginación.
    // Hibernate 7.4 baja esta a una subconsulta paginada sobre pedido: ya no pagina en memoria.
    @Query(value = "select p from Pedido p join fetch p.lineas order by p.id",
           countQuery = "select count(p) from Pedido p")
    Page<Pedido> conLineasPaginadoEnSubconsulta(Pageable pagina);

    // Si se ordena primero por la colección no puede bajarla: trae todo y pagina en memoria (HHH90003004).
    @Query(value = "select p from Pedido p join fetch p.lineas l order by l.producto, p.id",
           countQuery = "select count(p) from Pedido p")
    Page<Pedido> conLineasPaginadoEnMemoria(Pageable pagina);

    // Taller 5, solución: paginar identificadores y luego traer el detalle de esa página
    @Query("select p.id from Pedido p order by p.id")
    Page<Long> idsPaginados(Pageable pagina);

    @Query("select distinct p from Pedido p join fetch p.lineas where p.id in :ids order by p.id")
    List<Pedido> conLineas(Collection<Long> ids);
}
