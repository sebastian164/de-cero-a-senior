package tienda;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;

/** Ejecuta todas las pruebas y mide cada grupo (paso 4). Termina con código 1 si alguna falla. */
public final class TodasLasPruebas {

    private static final List<String> TABLA = new ArrayList<>();

    private TodasLasPruebas() {
    }

    public static void main(String[] args) {
        medir("Dominio", "dominio", () -> {
            PruebasDelDominio.ejecutar();
            PruebasDelDominio.aprobacion();
        });
        medir("Aplicación", "aplicación", PruebasDeLaAplicacion::ejecutar);
        medir("Transacciones", "aplicación", PruebasDeTransacciones::ejecutar);
        medir("Concurrencia", "aplicación", PruebasDeConcurrencia::ejecutar);
        medir("Adaptadores de entrada", "adaptador de entrada", PruebasDeAdaptadores::ejecutar);
        medir("Contratos con memoria", "adaptador de salida", TodasLasPruebas::contratos);
        medir("Arquitectura", "arquitectura", PruebasDeArquitectura::ejecutar);

        System.out.println();
        System.out.println("| Grupo | Capa | Comprobaciones | Milisegundos |");
        System.out.println("|---|---|---|---|");
        TABLA.forEach(System.out::println);
        System.out.println();
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
        System.exit(Comprobar.fallos() == 0 ? 0 : 1);
    }

    private static void contratos() {
        System.out.println("Contrato del repositorio de pedidos, con memoria:");
        Comprobar.igual("AlmacenEnMemoria lo cumple entero", List.of(), ContratoDelRepositorioDePedidos.incumplimientos(() -> {
            AlmacenEnMemoria almacen = new AlmacenEnMemoria();
            return new ContratoDelRepositorioDePedidos.Escenario(almacen, almacen);
        }));
        System.out.println("Contrato del inventario, con memoria:");
        Comprobar.igual("AlmacenEnMemoria lo cumple entero", List.of(), ContratoDelInventario.incumplimientos(() -> {
            AlmacenEnMemoria almacen = new AlmacenEnMemoria();
            return new ContratoDelInventario.Escenario(almacen, almacen, "teclado");
        }));
        Comprobar.igual("el inventario ingenuo incumple justo la cláusula de concurrencia y la de revertir",
                List.of("lo guardado en una transacción revertida no queda",
                        "guardar lo que se leyó antes de que otro lo cambiara falla con conflicto",
                        "tras el conflicto queda solo el cambio de quien confirmó primero"),
                ContratoDelInventario.incumplimientos(() -> new ContratoDelInventario.Escenario(new InventarioIngenuo(), new SinTransaccion(), "teclado")));
    }

    private static void medir(String grupo, String capa, Runnable pruebas) {
        int antes = Comprobar.total();
        long inicio = System.nanoTime();
        pruebas.run();
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
        TABLA.add(String.format(Locale.ROOT, "| %s | %s | %d | %d |", grupo, capa, Comprobar.total() - antes, milisegundos));
    }
}
