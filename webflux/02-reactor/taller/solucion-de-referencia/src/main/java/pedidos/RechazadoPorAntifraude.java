package pedidos;
public class RechazadoPorAntifraude extends RuntimeException {
    public RechazadoPorAntifraude(String id) { super("antifraude rechazó el pedido " + id); }
}
