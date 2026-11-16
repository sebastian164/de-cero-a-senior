public final class Camion implements Transporte {
    public String nombre() { return "CAMION"; }
    public long costo(Envio e) { return 50_000 + e.distanciaKm() * 1_200L + e.pesoKg() * 100L; }
    public int tiempoEstimadoHoras(int km) { return Math.max(2, km / 50); }
    public boolean puedeLlevar(Envio e) { return e.pesoKg() <= 5_000; }
}
