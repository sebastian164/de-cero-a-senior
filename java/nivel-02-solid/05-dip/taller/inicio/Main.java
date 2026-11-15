import java.time.LocalDate;

public class Main {

    public static void main(String[] args) {
        ServicioSuscripciones servicio = new ServicioSuscripciones();
        System.out.println(servicio.renovar("ana", 3));
    }
}

class ServicioSuscripciones {

    private final PasarelaPagosAcme pasarela = new PasarelaPagosAcme("clave-secreta-prod");
    private final ArchivoLog log = new ArchivoLog("/var/log/suscripciones.log");

    String renovar(String usuario, int meses) {
        long valor = meses * 30_000L;
        if (meses >= 12) {
            valor = valor * 80 / 100;
        }
        String codigo = pasarela.cobrarConTarjetaGuardada(usuario, valor, "COP", 3);
        LocalDate vence = LocalDate.now().plusMonths(meses);
        log.escribirLinea(LocalDate.now() + ";" + usuario + ";" + valor + ";" + codigo);
        return "Renovado hasta " + vence + " (" + codigo + ")";
    }
}

class PasarelaPagosAcme {
    private final String clave;
    PasarelaPagosAcme(String clave) { this.clave = clave; }
    String cobrarConTarjetaGuardada(String usuario, long valor, String moneda, int reintentos) {
        System.out.println("[ACME " + clave.length() + "] cobro a " + usuario + " por " + valor + " " + moneda);
        return "ACME-" + Math.abs(usuario.hashCode());
    }
}

class ArchivoLog {
    private final String ruta;
    ArchivoLog(String ruta) { this.ruta = ruta; }
    void escribirLinea(String linea) { System.out.println("[" + ruta + "] " + linea); }
}
