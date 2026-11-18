package pedidos.aplicacion.reactiva.puertos;

import reactor.core.publisher.Mono;
import tienda.dominio.Existencias;

public interface Inventario {
    Mono<Existencias> existenciasDe(String producto);     // vacío si no hay registro; toma la fila hasta terminar la unidad de trabajo
    Mono<Void> guardar(Existencias existencias);
}
