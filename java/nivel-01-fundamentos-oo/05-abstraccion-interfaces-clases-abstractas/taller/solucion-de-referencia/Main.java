import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        Caja caja = new Caja();
        List<MedioDePago> medios = List.of(
                new TarjetaCredito("4111111111111111"),
                new TarjetaDebito("5500000000000004"),
                new Efectivo(100_000),
                new Transferencia("Banco Andino", Set.of("Banco Andino", "Banco del Valle")),
                new BilleteraDigital("3001234567"));
        for (MedioDePago medio : medios) {
            caja.cobrar(medio, 50_000);
        }
    }
}
