/** Un personaje TIENE un tipo, un arma y una armadura. El arma cambia en plena partida. */
public final class Personaje {

    private final String nombre;
    private final TipoPersonaje tipo;
    private Arma arma;
    private Armadura armadura;
    private int vida = 100;

    public Personaje(String nombre, TipoPersonaje tipo, Arma arma, Armadura armadura) {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Falta el nombre");
        if (tipo == null || arma == null || armadura == null) throw new IllegalArgumentException("Faltan datos");
        this.nombre = nombre;
        this.tipo = tipo;
        this.arma = arma;
        this.armadura = armadura;
    }

    public int atacar() {
        return arma.dano() + tipo.bonoAtaque();
    }

    /** Devuelve el daño efectivamente recibido. */
    public int defender(int dano) {
        int recibido = armadura.reducir(dano);   // delegación
        vida = Math.max(0, vida - recibido);
        return recibido;
    }

    public void cambiarArma(Arma nueva) {
        if (nueva == null) throw new IllegalArgumentException("Falta el arma");
        this.arma = nueva;
    }

    public void cambiarArmadura(Armadura nueva) {
        if (nueva == null) throw new IllegalArgumentException("Falta la armadura");
        this.armadura = nueva;
    }

    public String describir() {
        String texto = nombre + ", " + tipo.nombre() + " con " + arma.nombre();
        return armadura == Armadura.NINGUNA ? texto : texto + " y armadura de " + armadura.nombre();
    }

    public int vida() { return vida; }
}
