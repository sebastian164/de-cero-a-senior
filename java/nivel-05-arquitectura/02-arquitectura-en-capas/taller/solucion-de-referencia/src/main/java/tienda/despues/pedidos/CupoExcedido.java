package tienda.despues.pedidos;

public class CupoExcedido extends RuntimeException {
    public CupoExcedido(long disponible, long requerido) {
        super("Cupo excedido: disponible " + disponible + ", requerido " + requerido);
    }
}
