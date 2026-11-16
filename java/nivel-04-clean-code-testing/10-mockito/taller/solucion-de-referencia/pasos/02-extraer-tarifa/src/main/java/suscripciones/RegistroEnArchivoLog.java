package suscripciones;

import java.time.LocalDate;

/** Adaptador: el mismo formato de línea que escribía la versión original. */
class RegistroEnArchivoLog implements RegistroRenovaciones {

    private final String ruta;

    RegistroEnArchivoLog(String ruta) {
        this.ruta = ruta;
    }

    @Override
    public void registrar(LocalDate fecha, String suscriptor, long valorEnPesos, Comprobante comprobante) {
        System.out.println("[" + ruta + "] " + fecha + ";" + suscriptor + ";" + valorEnPesos + ";" + comprobante.codigo());
    }
}
