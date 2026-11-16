package combinaciones;

import java.util.List;

/** Paso 1: una clase por combinación. 3 reportes x 3 formatos = 9 clases. */
public final class Combinaciones {
    public static final List<Class<?>> CLASES = List.of(
            ReporteVentasPdf.class, ReporteVentasExcel.class, ReporteVentasPantalla.class,
            ReporteInventarioPdf.class, ReporteInventarioExcel.class, ReporteInventarioPantalla.class,
            ReporteCarteraPdf.class, ReporteCarteraExcel.class, ReporteCarteraPantalla.class);
    public static String ejemplo() { return new ReporteVentasPdf().generar(); }
    private Combinaciones() { }
}

abstract class ReporteBase {
    public abstract String generar();
    public void imprimir() { System.out.println(generar()); }
}

// El formato se repite en tres clases de cada columna; los datos se repiten en tres de cada fila.
class ReporteVentasPdf extends ReporteBase { public String generar() { return "[PDF] Ventas\n[PDF] Teclado | 12"; } }
class ReporteVentasExcel extends ReporteBase { public String generar() { return "[XLSX] hoja 'Ventas'\n[XLSX] Teclado\t12"; } }
class ReporteVentasPantalla extends ReporteBase { public String generar() { return "== Ventas ==\nTeclado  12"; } }
class ReporteInventarioPdf extends ReporteBase { public String generar() { return "[PDF] Inventario\n[PDF] Teclado | 4 en bodega"; } }
class ReporteInventarioExcel extends ReporteBase { public String generar() { return "[XLSX] hoja 'Inventario'\n[XLSX] Teclado\t4 en bodega"; } }
class ReporteInventarioPantalla extends ReporteBase { public String generar() { return "== Inventario ==\nTeclado  4 en bodega"; } }
class ReporteCarteraPdf extends ReporteBase { public String generar() { return "[PDF] Cartera\n[PDF] Ana | vencida"; } }
class ReporteCarteraExcel extends ReporteBase { public String generar() { return "[XLSX] hoja 'Cartera'\n[XLSX] Ana\tvencida"; } }
class ReporteCarteraPantalla extends ReporteBase { public String generar() { return "== Cartera ==\nAna  vencida"; } }
