package pedidos.aplicacion.puertos;

import reactor.core.publisher.Flux;

public interface Eventos {
    void emitir(String pedidoId, String estado);
    void terminar(String pedidoId);
    Flux<String> flujo(String pedidoId);
}
