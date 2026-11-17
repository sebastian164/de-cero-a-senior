package tienda;

/** Una orden o una respuesta. El id nace con el mensaje y se repite en cada reentrega. */
public record Mensaje(String id, String sagaId, String tipo) {
}
