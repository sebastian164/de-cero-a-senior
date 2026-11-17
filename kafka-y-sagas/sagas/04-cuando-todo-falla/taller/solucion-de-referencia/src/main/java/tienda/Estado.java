package tienda;

public enum Estado {
    RESERVANDO, COBRANDO, PROGRAMANDO_ENVIO, SUMANDO_PUNTOS, COMPLETADA,
    REEMBOLSANDO, LIBERANDO, CANCELADA,
    /** Una compensación que no se logra: se detiene y espera a una persona. */
    REVISION_HUMANA;

    public boolean terminal() {
        return this == COMPLETADA || this == CANCELADA;
    }

    public boolean compensando() {
        return this == REEMBOLSANDO || this == LIBERANDO;
    }
}
