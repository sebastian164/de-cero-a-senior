import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        Factura factura = new Factura("F-001", "Ferretería El Tornillo", "compras@eltornillo.example");
        factura.agregarItem(new Item("Martillo", 2, 35_000));
        factura.agregarItem(new Item("Caja de puntillas", 5, 8_000));

        CalculadoraImpuestos impuestos = new CalculadoraImpuestos();
        ServicioFacturacion servicio = new ServicioFacturacion(
                new FormateadorFactura(impuestos),
                new ArchivoFacturas(Path.of("salida")),
                new NotificadorCorreo());

        servicio.emitir(factura);
    }
}
