package seg;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioPedidos {
    private final Map<Long, Pedido> datos = Map.of(
            1L, new Pedido(1L, "ana", "Teclado"),
            2L, new Pedido(2L, "ana", "Monitor"),
            3L, new Pedido(3L, "luis", "Cable HDMI"));

    Optional<Pedido> buscar(Long id) { return Optional.ofNullable(datos.get(id)); }

    List<Pedido> deDueno(String dueno) {
        return datos.values().stream().filter(p -> p.dueno().equals(dueno)).sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }
}
