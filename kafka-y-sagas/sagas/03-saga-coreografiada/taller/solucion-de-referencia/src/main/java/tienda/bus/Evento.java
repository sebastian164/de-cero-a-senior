package tienda.bus;

/** Un hecho publicado. correlacion = la compra; profundidad = cuántos eventos lo causaron en cadena. */
public record Evento(String id, String tipo, String correlacion, String causa, int profundidad) {
}
