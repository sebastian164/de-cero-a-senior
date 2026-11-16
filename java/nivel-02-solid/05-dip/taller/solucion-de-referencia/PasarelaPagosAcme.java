/** Clase del proveedor: se deja tal cual estaba en el inicio. */
class PasarelaPagosAcme {
    private final String clave;
    PasarelaPagosAcme(String clave) { this.clave = clave; }
    String cobrarConTarjetaGuardada(String usuario, long valor, String moneda, int reintentos) {
        System.out.println("[ACME " + clave.length() + "] cobro a " + usuario + " por " + valor + " " + moneda);
        return "ACME-" + Math.abs(usuario.hashCode());
    }
}
