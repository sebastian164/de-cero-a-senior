public interface Transporte {
    String nombre();
    long costo(Envio envio);
    int tiempoEstimadoHoras(int distanciaKm);
    boolean puedeLlevar(Envio envio);
}
