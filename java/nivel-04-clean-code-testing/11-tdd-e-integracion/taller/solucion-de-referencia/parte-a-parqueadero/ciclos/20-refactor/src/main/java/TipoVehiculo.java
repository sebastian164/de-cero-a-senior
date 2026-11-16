public enum TipoVehiculo {
    CARRO(5_000, 3_000, 30_000),
    MOTO(2_000, 1_000, 12_000),
    BICICLETA(0, 0, 0);

    final long primeraHora;
    final long horaAdicional;
    final long tarifaDeDia;

    TipoVehiculo(long primeraHora, long horaAdicional, long tarifaDeDia) {
        this.primeraHora = primeraHora;
        this.horaAdicional = horaAdicional;
        this.tarifaDeDia = tarifaDeDia;
    }
}
