package seg;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
class ConsultaPedidos {
    private final RepositorioPedidos repositorio;

    ConsultaPedidos(RepositorioPedidos repositorio) { this.repositorio = repositorio; }

    @PreAuthorize("hasRole('ADMIN') or @propiedadPedidos.esDueno(#id, authentication.name)")
    PedidoResponse buscar(Long id) {
        return repositorio.buscar(id).map(PedidoResponse::de).orElseThrow(() -> new PedidoNoEncontrado(id));
    }

    @PreAuthorize("isAuthenticated()")
    List<PedidoResponse> misPedidos(String usuario) {
        return repositorio.deDueno(usuario).stream().map(PedidoResponse::de).toList();
    }

    @PreAuthorize("hasAnyRole('SOPORTE','ADMIN')")
    int totalPedidos() { return 3; }
}
