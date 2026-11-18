package pedidos.aplicacion.puertos;

import reactor.core.publisher.Mono;
import tienda.dominio.Existencias;

/** Las existencias propias, en la base de datos del servicio. */
public interface Almacen {
    Mono<Existencias> existenciasDe(String producto);     // vacío si no hay registro; toma la fila hasta terminar la unidad de trabajo
    Mono<Void> guardar(Existencias existencias);
}
