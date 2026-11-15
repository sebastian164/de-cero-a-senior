import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Factura> facturas = List.of(
                new Factura(1, 45, false),
                new Factura(2, 10, true),
                new Factura(3, 95, false));
        System.out.println(numerosDeFacturasEnCobroJuridico(facturas));
    }

    private static final int DIAS_PARA_COBRO_JURIDICO = 30;

    static List<Integer> numerosDeFacturasEnCobroJuridico(List<Factura> facturas) {
        List<Integer> numeros = new ArrayList<>();
        for (Factura factura : facturas) {
            if (factura.requiereCobroJuridico(DIAS_PARA_COBRO_JURIDICO)) {
                numeros.add(factura.numero());
            }
        }
        return numeros;
    }
}

record Factura(int numero, int diasDeMora, boolean tieneAcuerdoDePago) {

    boolean requiereCobroJuridico(int diasLimite) {
        return diasDeMora > diasLimite && !tieneAcuerdoDePago;
    }
}
