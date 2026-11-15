package banco.dominio;

/** Un valor en centavos. Inmutable y nunca negativo. */
public record Dinero(long centavos) {

    public static final Dinero CERO = new Dinero(0);

    public Dinero {
        if (centavos < 0) {
            throw new ReglaDeNegocioViolada("un valor no puede ser negativo");
        }
    }

    public static Dinero pesos(long pesos) {
        return new Dinero(pesos * 100);
    }

    public Dinero mas(Dinero otro) {
        return new Dinero(centavos + otro.centavos);
    }

    public Dinero menos(Dinero otro) {
        return new Dinero(centavos - otro.centavos);            // si quedara negativo, el constructor lo rechaza
    }

    public boolean esMayorQue(Dinero otro) {
        return centavos > otro.centavos;
    }

    public boolean esCero() {
        return centavos == 0;
    }
}
