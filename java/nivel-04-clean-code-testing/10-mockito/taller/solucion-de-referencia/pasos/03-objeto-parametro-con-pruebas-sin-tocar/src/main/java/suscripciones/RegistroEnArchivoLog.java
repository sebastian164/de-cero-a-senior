package suscripciones;

/** Adaptador: el mismo formato de línea que escribía la versión original. */
class RegistroEnArchivoLog implements RegistroRenovaciones {

    private final String ruta;

    RegistroEnArchivoLog(String ruta) {
        this.ruta = ruta;
    }

    @Override
    public void registrar(Renovacion r) {
        System.out.println("[" + ruta + "] " + r.fecha() + ";" + r.suscriptor() + ";" + r.valorEnPesos() + ";" + r.comprobante().codigo());
    }
}
