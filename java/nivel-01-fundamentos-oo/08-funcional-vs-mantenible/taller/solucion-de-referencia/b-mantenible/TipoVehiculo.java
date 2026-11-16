public enum TipoVehiculo {
    CARRO(20, new Tarifa(5_000, 3_000, 30_000)),
    MOTO(30, new Tarifa(2_000, 1_000, 12_000)),
    BICICLETA(15, Tarifa.GRATIS);

    private final int cupo;
    private final Tarifa tarifa;

    TipoVehiculo(int cupo, Tarifa tarifa) {
        this.cupo = cupo;
        this.tarifa = tarifa;
    }

    public int cupo() { return cupo; }
    public Tarifa tarifa() { return tarifa; }
}
