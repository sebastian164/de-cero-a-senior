package suscripciones;

/** Adaptador: traduce el idioma del negocio (Cobro) al de la pasarela (moneda, reintentos). */
class CobrosConAcme implements Cobros {

    private static final String MONEDA = "COP";
    private static final int REINTENTOS = 3;

    private final PasarelaPagosAcme pasarela;

    CobrosConAcme(PasarelaPagosAcme pasarela) {
        this.pasarela = pasarela;
    }

    @Override
    public Comprobante cobrar(Cobro cobro) {
        return new Comprobante(pasarela.cobrarConTarjetaGuardada(cobro.suscriptor(), cobro.valorEnPesos(), MONEDA, REINTENTOS));
    }
}
