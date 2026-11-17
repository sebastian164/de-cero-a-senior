package tienda.integrador.orquestador.dominio;

public enum Estado {
    RESERVANDO, COBRANDO, PROGRAMANDO_ENVIO, SUMANDO_PUNTOS, COMPLETADA,
    REEMBOLSANDO, LIBERANDO, CANCELADA;

    public boolean terminal() {
        return this == COMPLETADA || this == CANCELADA;
    }
}
