import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Raíz de composición: el único lugar que cambia al agregar un cupón o un recargo.
        CalculadoraPrecio calculadora = new CalculadoraPrecio(
                List.of(new RecargoEnvio(), new RecargoEmpaqueFragil()),
                List.of(new CuponPorcentaje("VERANO10", 10), new CuponValorFijo("BIENVENIDA", 20_000),
                        new CuponEnvioGratis("ENVIOGRATIS")));
        System.out.println(calculadora.precioFinal(new Pedido(100_000, Categoria.TECNOLOGIA, "BOGOTA", true, "VERANO10")));
        System.out.println(calculadora.precioFinal(new Pedido(100_000, Categoria.ALIMENTOS, "TUNJA", false, null)));
        System.out.println(calculadora.precioFinal(new Pedido(100_000, Categoria.ALIMENTOS, "TUNJA", false, "ENVIOGRATIS")));
    }
}
