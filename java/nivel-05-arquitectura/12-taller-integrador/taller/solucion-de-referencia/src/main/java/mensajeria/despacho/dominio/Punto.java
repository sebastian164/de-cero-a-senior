package mensajeria.despacho.dominio;

/** Coordenadas en km sobre un plano local de la ciudad (simplificación). */
public record Punto(double x, double y) {
    public double distanciaA(Punto o) { return Math.hypot(x - o.x, y - o.y); }
}
