/** Parte A, diseño elegido: figuras inmutables; "cambiar el ancho" devuelve una figura nueva. */
public class Main {
    public static void main(String[] args) {
        Rectangulo r = new Rectangulo(2, 3);
        Rectangulo doble = r.conAncho(r.ancho() * 2);
        Cuadrado c = new Cuadrado(3);
        Rectangulo cDoble = c.comoRectangulo().conAncho(6);   // deja de ser cuadrado y el tipo lo dice
        System.out.println("Rectángulo " + r.area() + " -> " + doble.area());
        System.out.println("Cuadrado " + c.area() + " -> rectángulo " + cDoble.area());
        boolean ok = doble.area() == 2 * r.area() && cDoble.area() == 2 * c.area();
        Figura[] figuras = {r, c};
        for (Figura f : figuras) System.out.println("Área polimórfica: " + f.area());
        if (!ok) System.exit(1);
    }
}

interface Figura { int area(); }

record Rectangulo(int ancho, int alto) implements Figura {
    Rectangulo conAncho(int nuevo) { return new Rectangulo(nuevo, alto); }
    public int area() { return ancho * alto; }
}

record Cuadrado(int lado) implements Figura {
    Rectangulo comoRectangulo() { return new Rectangulo(lado, lado); }
    public int area() { return lado * lado; }
}
