public enum TipoPersonaje {
    GUERRERO("guerrero", 5), MAGO("mago", 10), ARQUERO("arquero", 4), CLERIGO("clérigo", 3);

    private final String nombre;
    private final int bonoAtaque;

    TipoPersonaje(String nombre, int bonoAtaque) {
        this.nombre = nombre;
        this.bonoAtaque = bonoAtaque;
    }

    int bonoAtaque() { return bonoAtaque; }
    String nombre() { return nombre; }
}
