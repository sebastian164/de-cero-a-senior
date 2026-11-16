/** El original no tenía caso en puedeLlevar (devolvía false siempre). Ahora la interfaz obliga a decidirlo. */
public final class Bicicleta implements Transporte {
    public String nombre() { return "BICICLETA"; }
    public long costo(Envio e) { return 2_000 + e.distanciaKm() * 300L; }
    public int tiempoEstimadoHoras(int km) { return Math.max(1, km / 12); }
    public boolean puedeLlevar(Envio e) { return e.pesoKg() <= 8; }
}
