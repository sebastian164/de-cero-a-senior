package suscripciones;

import java.time.Clock;

/** Raíz de composición: el único lugar que conoce las clases concretas. */
public class Main {

    public static void main(String[] args) {
        ServicioSuscripciones servicio = new ServicioSuscripciones(
                new CobrosConAcme(new PasarelaPagosAcme("clave-secreta-prod")),
                new RegistroEnArchivoLog("/var/log/suscripciones.log"),
                Clock.systemDefaultZone());
        System.out.println(servicio.renovar("ana", 3));
    }
}
