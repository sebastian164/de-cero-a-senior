package banco;

import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import java.util.List;

/** Ejecuta todas las pruebas del proyecto. Termina con código 1 si alguna falla. */
public final class TodasLasPruebas {

    private TodasLasPruebas() {
    }

    public static void main(String[] args) {
        PruebasDelDominio.ejecutar();
        PruebasDeLaAplicacion.ejecutar();
        PruebasDeTransacciones.ejecutar();
        PruebasDeConcurrencia.ejecutar();
        PruebasDeAdaptadores.ejecutar();

        System.out.println("Contrato del repositorio:");
        Comprobar.igual("el almacén en memoria lo cumple entero", List.of(),
                ContratoDelRepositorio.incumplimientos(AlmacenEnMemoria::new));
        Comprobar.igual("el repositorio ingenuo incumple justo la cláusula de concurrencia",
                List.of("guardar con una versión vieja falla con conflicto"),
                ContratoDelRepositorio.incumplimientos(RepositorioIngenuo::new));

        PruebasDeArquitectura.ejecutar();

        System.out.println();
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
        System.exit(Comprobar.fallos() == 0 ? 0 : 1);
    }
}
