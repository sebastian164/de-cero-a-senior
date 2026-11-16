/** Parte A: el clásico, con la comprobación que falla para el Cuadrado. */
public class Main {

    static void duplicarAncho(Rectangulo r) {
        r.setAncho(r.getAncho() * 2);
    }

    /** Devuelve true si el área se duplicó, que es lo que el cliente de Rectangulo espera. */
    static boolean comprobarDuplicaArea(Rectangulo r) {
        int antes = r.area();
        duplicarAncho(r);
        return r.area() == antes * 2;
    }

    public static void main(String[] args) {
        Rectangulo rectangulo = new Rectangulo(2, 3);
        Rectangulo cuadrado = new Cuadrado(3);
        System.out.println("Rectángulo: área duplicada = " + comprobarDuplicaArea(rectangulo) + " (" + rectangulo.area() + ")");
        System.out.println("Cuadrado:   área duplicada = " + comprobarDuplicaArea(cuadrado) + " (" + cuadrado.area() + ")");
        if (!comprobarDuplicaArea(new Rectangulo(1, 1)) || comprobarDuplicaArea(new Cuadrado(1))) {
            System.exit(1);   // la demostración no se cumplió
        }
    }
}

class Rectangulo {
    private int ancho, alto;
    Rectangulo(int ancho, int alto) { this.ancho = ancho; this.alto = alto; }
    void setAncho(int ancho) { this.ancho = ancho; }
    void setAlto(int alto) { this.alto = alto; }
    int getAncho() { return ancho; }
    int getAlto() { return alto; }
    int area() { return ancho * alto; }
}

class Cuadrado extends Rectangulo {
    Cuadrado(int lado) { super(lado, lado); }
    @Override void setAncho(int ancho) { super.setAncho(ancho); super.setAlto(ancho); }
    @Override void setAlto(int alto) { super.setAncho(alto); super.setAlto(alto); }
}
