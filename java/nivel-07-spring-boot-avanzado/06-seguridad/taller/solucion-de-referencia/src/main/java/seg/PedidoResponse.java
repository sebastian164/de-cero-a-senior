package seg;

/** DTO: nunca se expone el campo interno "dueno" ni la entidad. */
public record PedidoResponse(Long id, String descripcion) {
    static PedidoResponse de(Pedido p) {
        return new PedidoResponse(p.id(), p.descripcion());
    }
}
