/** Punto 1: tres de las clases con herencia, para sentir la duplicación. */
public class Main {
    public static void main(String[] args) {
        Personaje[] personajes = {new GuerreroConEspada("Brom"), new GuerreroConArco("Lia"), new MagoConBaston("Ozz")};
        for (Personaje p : personajes) {
            System.out.println(p.describir() + " ataca por " + p.atacar() + " y recibe " + p.defender(20) + " de 20");
        }
    }
}

abstract class Personaje {
    protected final String nombre;
    protected int vida = 100;
    Personaje(String nombre) { this.nombre = nombre; }
    abstract int atacar();
    abstract int defender(int dano);
    abstract String describir();
}

class GuerreroConEspada extends Personaje {
    GuerreroConEspada(String nombre) { super(nombre); }
    int atacar() { return 12 + 5; }                    // espada 12 + fuerza de guerrero 5
    int defender(int dano) { vida -= dano; return dano; } // sin armadura
    String describir() { return nombre + ", guerrero con espada"; }
}

class GuerreroConArco extends Personaje {
    GuerreroConArco(String nombre) { super(nombre); }
    int atacar() { return 9 + 5; }                     // arco 9 + fuerza de guerrero 5 (duplicado)
    int defender(int dano) { vida -= dano; return dano; } // duplicado
    String describir() { return nombre + ", guerrero con arco"; }
}

class MagoConBaston extends Personaje {
    MagoConBaston(String nombre) { super(nombre); }
    int atacar() { return 6 + 10; }                    // bastón 6 + poder de mago 10
    int defender(int dano) { vida -= dano; return dano; } // duplicado otra vez
    String describir() { return nombre + ", mago con bastón"; }
}
