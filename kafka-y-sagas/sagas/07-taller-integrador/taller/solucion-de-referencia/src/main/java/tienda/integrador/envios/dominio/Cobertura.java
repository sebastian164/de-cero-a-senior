package tienda.integrador.envios.dominio;

/** La regla de negocio de Envíos: a esta ciudad no llega ninguna transportadora. */
public final class Cobertura {

    public static final String CIUDAD_IMPOSIBLE = "Atlantis";

    private Cobertura() {
    }

    public static boolean llega(String ciudad) {
        return !CIUDAD_IMPOSIBLE.equals(ciudad);
    }
}
