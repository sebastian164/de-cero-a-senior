import java.util.List;

public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        List<Cupon> cupones = List.of(new CuponPorcentaje("VERANO10", 10), new CuponValorFijo("BIENVENIDA", 20_000),
                new CuponEnvioGratis("ENVIOGRATIS"));
        CalculadoraPrecio c = new CalculadoraPrecio(List.of(new RecargoEnvio(), new RecargoEmpaqueFragil()), cupones);
        // Los mismos dos resultados del código de inicio.
        igual("inicio 1", 123_000L, c.precioFinal(new Pedido(100_000, Categoria.TECNOLOGIA, "BOGOTA", true, "VERANO10")));
        igual("inicio 2", 120_000L, c.precioFinal(new Pedido(100_000, Categoria.ALIMENTOS, "TUNJA", false, null)));
        igual("bienvenida", 107_000L, c.precioFinal(new Pedido(100_000, Categoria.TECNOLOGIA, "BOGOTA", false, "BIENVENIDA")));
        igual("envío gratis Tunja", 105_000L, c.precioFinal(new Pedido(100_000, Categoria.ALIMENTOS, "TUNJA", false, "ENVIOGRATIS")));
        igual("envío gratis Bogotá", 125_000L, c.precioFinal(new Pedido(100_000, Categoria.TECNOLOGIA, "BOGOTA", true, "ENVIOGRATIS")));
        igual("sin impuesto", 108_000L, c.precioFinal(new Pedido(100_000, Categoria.OTRA, "BOGOTA", false, null)));
        igual("descuento no deja precio negativo", 0L, c.precioFinal(new Pedido(0, Categoria.OTRA, "BOGOTA", false, "BIENVENIDA")));
        // Recargo nuevo sin tocar CalculadoraPrecio.
        CalculadoraPrecio conSeguro = new CalculadoraPrecio(
                List.of(new RecargoEnvio(), new RecargoEmpaqueFragil(), new RecargoSeguro()), cupones);
        igual("recargo seguro", 1_000_000L + 190_000 + 8_000 + 10_000,
                conSeguro.precioFinal(new Pedido(1_000_000, Categoria.TECNOLOGIA, "BOGOTA", false, null)));
        total++;
        try { c.precioFinal(new Pedido(1, Categoria.OTRA, "X", false, "NOEXISTE")); fallos++; System.out.println("FALLA cupón desconocido"); }
        catch (IllegalArgumentException e) { }
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }
}
