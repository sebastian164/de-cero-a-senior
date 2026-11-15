public class Main {

    public static void main(String[] args) {
        ServicioCobros servicio = new ServicioCobros(new AdaptadorPagoRapido(new SdkPagoRapido()));
        servicio.cobrar("ana", 50_000);
        try {
            servicio.cobrar("moroso", 50_000);
        } catch (PagoRechazadoException e) {
            System.out.println("Rechazado: " + e.getMessage());
        }
    }
}

// ----- Lo que el negocio define -----

interface PasarelaPagos {
    void cobrar(String cliente, long pesos);
}

class PagoRechazadoException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    PagoRechazadoException(String mensaje) { super(mensaje); }
}

class ServicioCobros {
    private final PasarelaPagos pasarela;
    ServicioCobros(PasarelaPagos pasarela) { this.pasarela = pasarela; }

    void cobrar(String cliente, long pesos) {
        pasarela.cobrar(cliente, pesos);
        System.out.println("Cobro registrado: " + cliente + " $" + pesos);
    }
}

// ----- Lo que no podemos modificar -----

class SdkPagoRapido {
    /** Recibe centavos; devuelve 0 si todo salió bien y un código de error si no. */
    int ejecutarTransaccion(String idCliente, long centavos, String moneda, String tipo) {
        return idCliente.equals("moroso") ? 51 : 0;
    }
}

// ----- El adaptador: traduce en ambos sentidos -----

class AdaptadorPagoRapido implements PasarelaPagos {

    private final SdkPagoRapido sdk;

    AdaptadorPagoRapido(SdkPagoRapido sdk) {
        this.sdk = sdk;
    }

    @Override
    public void cobrar(String cliente, long pesos) {
        int codigo = sdk.ejecutarTransaccion(cliente, pesos * 100, "COP", "SALE");
        if (codigo != 0) {
            throw new PagoRechazadoException(traducir(codigo));
        }
    }

    private String traducir(int codigo) {
        return codigo == 51 ? "Fondos insuficientes" : "Error de la pasarela (" + codigo + ")";
    }
}
