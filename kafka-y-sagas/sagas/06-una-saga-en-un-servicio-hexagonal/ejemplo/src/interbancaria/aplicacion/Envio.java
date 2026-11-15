package interbancaria.aplicacion;

/** Un envío a otro banco y en qué punto va. Es lo que permite retomarlo después de una caída. */
public final class Envio {

    public enum Estado {
        DEBITADO,        // el dinero salió de la cuenta; aún no se sabe si llegó
        COMPLETADO,      // el otro banco lo aceptó
        DEVUELTO         // el otro banco lo rechazó y el dinero volvió a la cuenta
    }

    private final String id;
    private final String cuentaOrigen;
    private final String bancoDestino;
    private final String cuentaDestino;
    private final long centavos;
    private Estado estado;

    public Envio(String id, String cuentaOrigen, String bancoDestino, String cuentaDestino, long centavos, Estado estado) {
        this.id = id;
        this.cuentaOrigen = cuentaOrigen;
        this.bancoDestino = bancoDestino;
        this.cuentaDestino = cuentaDestino;
        this.centavos = centavos;
        this.estado = estado;
    }

    public void completar() {
        exigirAbierto();
        estado = Estado.COMPLETADO;
    }

    public void devolver() {
        exigirAbierto();
        estado = Estado.DEVUELTO;
    }

    /** Un envío cerrado no cambia: ni se completa lo devuelto ni se devuelve lo completado. */
    private void exigirAbierto() {
        if (estado != Estado.DEBITADO) {
            throw new IllegalStateException("el envío " + id + " ya está " + estado);
        }
    }

    public Envio copia() {
        return new Envio(id, cuentaOrigen, bancoDestino, cuentaDestino, centavos, estado);
    }

    public String id() {
        return id;
    }

    public String cuentaOrigen() {
        return cuentaOrigen;
    }

    public String bancoDestino() {
        return bancoDestino;
    }

    public String cuentaDestino() {
        return cuentaDestino;
    }

    public long centavos() {
        return centavos;
    }

    public Estado estado() {
        return estado;
    }
}
