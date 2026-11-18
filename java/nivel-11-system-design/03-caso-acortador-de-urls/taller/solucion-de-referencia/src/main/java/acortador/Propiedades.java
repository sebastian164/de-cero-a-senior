package acortador;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("acortador")
public record Propiedades(String dominio, String estrategia, boolean cache, int tamanoRango, String claveSecreta) {
    /** El enlace de propiedades deja el texto literal "${CLAVE_CODIGOS}" si la variable falta: se rechaza aquí. */
    public Propiedades {
        if (claveSecreta == null || claveSecreta.isBlank() || claveSecreta.startsWith("${"))
            throw new IllegalStateException("Falta la variable CLAVE_CODIGOS (secreto de la permutación): sin ella la aplicación no arranca");
    }
}
