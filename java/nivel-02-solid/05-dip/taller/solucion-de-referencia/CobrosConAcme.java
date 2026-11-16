/** Adaptador: traduce el idioma del negocio al de ACME. Moneda y reintentos son detalles de aquí. */
public class CobrosConAcme implements Cobros {
    private final PasarelaPagosAcme pasarela;

    public CobrosConAcme(PasarelaPagosAcme pasarela) {
        this.pasarela = pasarela;
    }

    @Override
    public String cobrar(String usuario, long valor) {
        return pasarela.cobrarConTarjetaGuardada(usuario, valor, "COP", 3);
    }
}
