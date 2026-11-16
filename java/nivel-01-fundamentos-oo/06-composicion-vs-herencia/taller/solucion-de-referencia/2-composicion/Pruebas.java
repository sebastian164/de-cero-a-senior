public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        Personaje p = new Personaje("Brom", TipoPersonaje.GUERRERO, Arma.ESPADA, Armadura.MALLA);
        igual("ataque espada + guerrero", 17, p.atacar());
        p.cambiarArma(Arma.HACHA);
        igual("cambio de arma en partida", 19, p.atacar());
        igual("armadura reduce", 14, p.defender(20));
        igual("vida", 86, p.vida());
        igual("armadura no cura", 0, p.defender(2));
        p.cambiarArmadura(Armadura.NINGUNA);
        igual("sin armadura", 20, p.defender(20));
        igual("descripción", "Brom, guerrero con hacha", p.describir());
        Personaje m = new Personaje("Ozz", TipoPersonaje.MAGO, Arma.ESPADA, Armadura.PLACAS);
        igual("mago con espada sin clase nueva", 22, m.atacar());
        igual("descripción con armadura", "Ozz, mago con espada y armadura de placas", m.describir());
        total++;
        try { p.cambiarArma(null); fallos++; } catch (IllegalArgumentException e) { }
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }
}
