/** Punto de extensión: un cupón nuevo es una clase (o una instancia) nueva. */
public interface Cupon {
    String codigo();
    long descuento(Desglose desglose);
}
