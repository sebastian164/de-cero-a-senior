package nosql.aplicacion;

/** Lo que el exterior ve de un producto: datos simples, sin comportamiento. Es lo que se guarda en una caché. */
public record VistaDeProducto(String producto, long precioCentavos, int existencias) {
}
