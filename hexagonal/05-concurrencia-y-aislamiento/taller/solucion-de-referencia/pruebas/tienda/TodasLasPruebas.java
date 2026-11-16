package tienda;

/** Ejecuta todas las pruebas. Termina con código 1 si alguna falla. */
public final class TodasLasPruebas {

    private TodasLasPruebas() {
    }

    public static void main(String[] args) {
        PruebasDelDominio.ejecutar();
        PruebasDeLaAplicacion.ejecutar();
        PruebasDeTransacciones.ejecutar();
        PruebasDeConcurrencia.ejecutar();
        PruebasDeArquitectura.ejecutar();
        System.out.println();
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
        System.exit(Comprobar.fallos() == 0 ? 0 : 1);
    }
}
