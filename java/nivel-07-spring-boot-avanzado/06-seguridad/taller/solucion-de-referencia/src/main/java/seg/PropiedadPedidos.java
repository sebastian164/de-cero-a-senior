package seg;

import org.springframework.stereotype.Component;

/** Autorización por recurso: ¿este pedido es de este usuario? Un pedido que no existe no es de nadie. */
@Component("propiedadPedidos")
public class PropiedadPedidos {
    private final RepositorioPedidos repositorio;

    PropiedadPedidos(RepositorioPedidos repositorio) { this.repositorio = repositorio; }

    public boolean esDueno(Long id, String usuario) {
        return repositorio.buscar(id).map(p -> p.dueno().equals(usuario)).orElse(false);
    }
}
