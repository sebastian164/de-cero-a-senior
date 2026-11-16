public final class Moto implements Transporte {
    public String nombre() { return "MOTO"; }
    public long costo(Envio e) { return 3_000 + e.distanciaKm() * 500L; }
    public int tiempoEstimadoHoras(int km) { return Math.max(1, km / 30); }
    public boolean puedeLlevar(Envio e) { return e.pesoKg() <= 20; }
}
