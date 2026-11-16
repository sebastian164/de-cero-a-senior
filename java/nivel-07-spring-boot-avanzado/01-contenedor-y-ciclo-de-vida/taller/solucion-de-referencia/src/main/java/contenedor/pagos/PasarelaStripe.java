package contenedor.pagos;

public class PasarelaStripe implements PasarelaPagos {
    private final String claveApi;
    public PasarelaStripe(String claveApi) { this.claveApi = claveApi; }
    @Override public String nombre() { return "stripe(" + claveApi + ")"; }
}
