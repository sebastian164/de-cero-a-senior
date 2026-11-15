public class ComisionPorMil implements PoliticaComision {

    private final long porMil;

    public ComisionPorMil(long porMil) {
        if (porMil < 0) {
            throw new IllegalArgumentException("La comisión no puede ser negativa");
        }
        this.porMil = porMil;
    }

    @Override
    public long calcular(long monto) {
        return monto * porMil / 1000;
    }
}
