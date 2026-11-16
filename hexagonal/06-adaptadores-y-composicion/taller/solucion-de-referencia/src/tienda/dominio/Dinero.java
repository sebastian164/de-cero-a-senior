package tienda.dominio;

/** Un valor en centavos. Inmutable y nunca negativo. Un pedido solo necesita sumar y multiplicar por una cantidad. */
public record Dinero(long centavos) {

    public static final Dinero CERO = new Dinero(0);

    public Dinero {
        if (centavos < 0) {
            throw new ReglaDeNegocioViolada("un valor no puede ser negativo");
        }
    }

    public static Dinero pesos(long pesos) {
        return new Dinero(Math.multiplyExact(pesos, 100L));
    }

    public Dinero mas(Dinero otro) {
        return new Dinero(Math.addExact(centavos, otro.centavos));
    }

    public Dinero por(int cantidad) {
        return new Dinero(Math.multiplyExact(centavos, (long) cantidad));
    }

    public boolean esMayorQue(Dinero otro) {
        return centavos > otro.centavos;
    }
}
