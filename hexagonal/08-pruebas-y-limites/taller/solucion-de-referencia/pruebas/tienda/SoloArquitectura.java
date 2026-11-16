package tienda;

/** Solo la prueba de arquitectura, para romperla a propósito (paso 3). */
public final class SoloArquitectura {

    private SoloArquitectura() {
    }

    public static void main(String[] args) {
        PruebasDeArquitectura.ejecutar();
        System.exit(Comprobar.fallos() == 0 ? 0 : 1);
    }
}
