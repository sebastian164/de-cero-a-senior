/** Transporte nuevo: archivo nuevo. Hasta 2 kg y hasta 10 km. */
public final class Dron implements Transporte {
    public String nombre() { return "DRON"; }
    public long costo(Envio e) { return 4_000 + e.distanciaKm() * 700L; }
    public int tiempoEstimadoHoras(int km) { return 1; }
    public boolean puedeLlevar(Envio e) { return e.pesoKg() <= 2 && e.distanciaKm() <= 10; }
}
