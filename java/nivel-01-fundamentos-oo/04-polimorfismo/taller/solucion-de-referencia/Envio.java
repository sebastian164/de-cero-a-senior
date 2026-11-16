public record Envio(int pesoKg, int distanciaKm) {
    public Envio {
        if (pesoKg <= 0) throw new IllegalArgumentException("El peso debe ser positivo");
        if (distanciaKm <= 0) throw new IllegalArgumentException("La distancia debe ser positiva");
    }
}
