public class Main {
    public static void main(String[] args) {
        Personaje brom = new Personaje("Brom", TipoPersonaje.GUERRERO, Arma.ESPADA, Armadura.MALLA);
        Personaje ozz = new Personaje("Ozz", TipoPersonaje.MAGO, Arma.BASTON, Armadura.NINGUNA);
        System.out.println(brom.describir() + " ataca por " + brom.atacar() + " y recibe " + brom.defender(20) + " de 20");
        System.out.println(ozz.describir() + " ataca por " + ozz.atacar() + " y recibe " + ozz.defender(20) + " de 20");
        brom.cambiarArma(Arma.ARCO);
        System.out.println("En plena partida: " + brom.describir() + " ataca por " + brom.atacar());
        System.out.println("Combinaciones con herencia (4 tipos x 5 armas x 3 armaduras): " + 4 * 5 * 3);
        System.out.println("Piezas con composición: " + (TipoPersonaje.values().length + 5 + 3)
                + " valores y 4 tipos (Personaje, TipoPersonaje, Arma, Armadura)");
    }
}
