package inventario.aplicacion;

/** Puerto de entrada: atender "pedido creado". Lo llama Kafka, HTTP o una prueba. */
public interface ReservarPorPedido {

    record Orden(String eventoId, String pedidoId, String producto, int cantidad) {
    }

    enum Resultado { RESERVADO, SIN_EXISTENCIAS, YA_PROCESADO }

    Resultado reservar(Orden orden);
}
