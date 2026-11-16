package jpa;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Taller 4: filtro dinámico con cuatro criterios opcionales. Un criterio nulo no filtra. */
public final class PedidoFiltros {

    private PedidoFiltros() { }

    public static Specification<Pedido> con(String cliente, EstadoPedido estado, Long totalMinimo, LocalDate desde) {
        return Specification.allOf(
                deCliente(cliente), enEstado(estado), conTotalDesde(totalMinimo), desdeFecha(desde));
    }

    static Specification<Pedido> deCliente(String nombre) {
        return (raiz, consulta, cb) -> nombre == null ? null
                : cb.like(cb.lower(raiz.get("cliente").get("nombre")), "%" + nombre.toLowerCase() + "%");
    }

    static Specification<Pedido> enEstado(EstadoPedido estado) {
        return (raiz, consulta, cb) -> estado == null ? null : cb.equal(raiz.get("estado"), estado);
    }

    static Specification<Pedido> conTotalDesde(Long minimo) {
        return (raiz, consulta, cb) -> minimo == null ? null : cb.ge(raiz.get("total"), minimo);
    }

    static Specification<Pedido> desdeFecha(LocalDate desde) {
        return (raiz, consulta, cb) -> desde == null ? null : cb.greaterThanOrEqualTo(raiz.get("fecha"), desde);
    }
}
