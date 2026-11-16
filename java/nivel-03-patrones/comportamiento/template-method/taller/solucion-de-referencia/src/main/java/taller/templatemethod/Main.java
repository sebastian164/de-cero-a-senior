package taller.templatemethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Main {

    public static void main(String[] args) {
        Auditoria auditoria = new Auditoria();
        Numerador numerador = new Numerador();
        Datos venta = new Datos("ACME", List.of(new Linea("Teclado", 2, 100_000), new Linea("Mouse", 1, 50_000)), null);

        System.out.println("== Herencia");
        System.out.println(new Factura(numerador, auditoria).generar(venta));
        System.out.println(new Cotizacion(numerador, auditoria).generar(venta));
        System.out.println(new NotaCredito(numerador, auditoria).generar(new Datos("ACME", venta.lineas(), "FAC-1")));

        System.out.println("== Composición");
        GeneradorCompuesto g = new GeneradorCompuesto(new Numerador(), new Auditoria());
        System.out.println(g.generar(TiposDocumento.FACTURA, venta));
        System.out.println(g.generar(TiposDocumento.NOTA_CREDITO, new Datos("ACME", venta.lineas(), "FAC-1")));
        System.out.println("Auditoría: " + auditoria.registros());
    }
}

record Linea(String producto, int cantidad, long precio) { }
record Datos(String cliente, List<Linea> lineas, String facturaOriginal) { }
record Totales(long subtotal, long impuesto, long total) { }

final class Numerador {
    private final Map<String, Integer> consecutivos = new HashMap<>();
    String siguiente(String prefijo) { return prefijo + "-" + consecutivos.merge(prefijo, 1, Integer::sum); }
}

final class Auditoria {
    private final List<String> registros = new ArrayList<>();
    void registrar(String r) { registros.add(r); }
    List<String> registros() { return List.copyOf(registros); }
}

// ===================== Versión 1: Template Method =====================

abstract class GeneradorDocumento {
    private final Numerador numerador;
    private final Auditoria auditoria;

    protected GeneradorDocumento(Numerador numerador, Auditoria auditoria) {
        this.numerador = numerador;
        this.auditoria = auditoria;
    }

    /** El esqueleto: final, nadie puede alterar el orden ni saltarse la auditoría. */
    final String generar(Datos datos) {
        validar(datos);                                         // fijo
        validarAdicional(datos);                                // gancho (no hace nada por defecto)
        Totales totales = calcularTotales(datos.lineas());      // abstracto
        String numero = numerador.siguiente(prefijo());          // fijo, con prefijo abstracto
        String texto = renderizar(numero, datos, totales);       // abstracto
        auditoria.registrar(numero + " " + datos.cliente() + " $" + totales.total());   // fijo
        return texto;
    }

    private void validar(Datos d) {
        if (d.cliente() == null || d.cliente().isBlank()) throw new IllegalArgumentException("Falta el cliente");
        if (d.lineas().isEmpty()) throw new IllegalArgumentException("El documento no tiene líneas");
    }

    protected void validarAdicional(Datos d) { }
    protected abstract Totales calcularTotales(List<Linea> lineas);
    protected abstract String prefijo();
    protected abstract String renderizar(String numero, Datos datos, Totales totales);

    static long subtotal(List<Linea> lineas) { return lineas.stream().mapToLong(l -> l.cantidad() * l.precio()).sum(); }
}

final class Factura extends GeneradorDocumento {
    Factura(Numerador n, Auditoria a) { super(n, a); }
    @Override protected Totales calcularTotales(List<Linea> l) { return Calculos.conIva(l); }
    @Override protected String prefijo() { return "FAC"; }
    @Override protected String renderizar(String n, Datos d, Totales t) { return "FACTURA " + n + " " + d.cliente() + " total $" + t.total(); }
}

final class Cotizacion extends GeneradorDocumento {
    Cotizacion(Numerador n, Auditoria a) { super(n, a); }
    @Override protected Totales calcularTotales(List<Linea> l) { return Calculos.conIvaYDescuento(l); }
    @Override protected String prefijo() { return "COT"; }
    @Override protected String renderizar(String n, Datos d, Totales t) {
        return "COTIZACIÓN " + n + " " + d.cliente() + " total estimado $" + t.total() + " (válida 30 días)";
    }
}

/** Paso 3: el paso extra entra por el gancho validarAdicional y en el render. */
final class NotaCredito extends GeneradorDocumento {
    NotaCredito(Numerador n, Auditoria a) { super(n, a); }
    @Override protected void validarAdicional(Datos d) {
        if (d.facturaOriginal() == null) throw new IllegalArgumentException("La nota crédito debe referenciar una factura");
    }
    @Override protected Totales calcularTotales(List<Linea> l) {
        Totales t = Calculos.conIva(l);
        return new Totales(-t.subtotal(), -t.impuesto(), -t.total());
    }
    @Override protected String prefijo() { return "NC"; }
    @Override protected String renderizar(String n, Datos d, Totales t) {
        return "NOTA CRÉDITO " + n + " " + d.cliente() + " sobre " + d.facturaOriginal() + " total $" + t.total();
    }
}

/** Cálculos puros, compartidos por ambas versiones: se prueban sin crear generadores. */
final class Calculos {
    private Calculos() { }
    static Totales conIva(List<Linea> l) {
        long s = GeneradorDocumento.subtotal(l);
        return new Totales(s, s * 19 / 100, s + s * 19 / 100);
    }
    static Totales conIvaYDescuento(List<Linea> l) {
        long s = GeneradorDocumento.subtotal(l) * 95 / 100;      // 5 % de descuento comercial
        return new Totales(s, s * 19 / 100, s + s * 19 / 100);
    }
}

// ===================== Versión 2: composición =====================

/** Los pasos variables como datos: funciones y un texto. */
record TipoDocumento(String prefijo,
                     Function<List<Linea>, Totales> totales,
                     Render render,
                     List<java.util.function.Consumer<Datos>> validacionesExtra) {
    interface Render { String aplicar(String numero, Datos datos, Totales totales); }
}

final class TiposDocumento {
    private TiposDocumento() { }

    static final TipoDocumento FACTURA = new TipoDocumento("FAC", Calculos::conIva,
            (n, d, t) -> "FACTURA " + n + " " + d.cliente() + " total $" + t.total(), List.of());

    static final TipoDocumento COTIZACION = new TipoDocumento("COT", Calculos::conIvaYDescuento,
            (n, d, t) -> "COTIZACIÓN " + n + " " + d.cliente() + " total estimado $" + t.total() + " (válida 30 días)", List.of());

    static final TipoDocumento NOTA_CREDITO = new TipoDocumento("NC",
            l -> { Totales t = Calculos.conIva(l); return new Totales(-t.subtotal(), -t.impuesto(), -t.total()); },
            (n, d, t) -> "NOTA CRÉDITO " + n + " " + d.cliente() + " sobre " + d.facturaOriginal() + " total $" + t.total(),
            List.of(d -> {
                if (d.facturaOriginal() == null) throw new IllegalArgumentException("La nota crédito debe referenciar una factura");
            }));
}

final class GeneradorCompuesto {
    private final Numerador numerador;
    private final Auditoria auditoria;
    GeneradorCompuesto(Numerador numerador, Auditoria auditoria) { this.numerador = numerador; this.auditoria = auditoria; }

    String generar(TipoDocumento tipo, Datos datos) {
        if (datos.cliente() == null || datos.cliente().isBlank()) throw new IllegalArgumentException("Falta el cliente");
        if (datos.lineas().isEmpty()) throw new IllegalArgumentException("El documento no tiene líneas");
        tipo.validacionesExtra().forEach(v -> v.accept(datos));
        Totales totales = tipo.totales().apply(datos.lineas());
        String numero = numerador.siguiente(tipo.prefijo());
        String texto = tipo.render().aplicar(numero, datos, totales);
        auditoria.registrar(numero + " " + datos.cliente() + " $" + totales.total());
        return texto;
    }
}
