package citas;

/** Errores de la aplicación que el manejador traduce a Problem Details. */
sealed class ErrorDeApi extends RuntimeException {
    ErrorDeApi(String mensaje) { super(mensaje); }

    static final class NoEncontrada extends ErrorDeApi {
        NoEncontrada(Long id) { super("No existe la cita " + id); }
    }

    static final class Conflicto extends ErrorDeApi {
        Conflicto(String mensaje) { super(mensaje); }
    }

    static final class ReglaDeNegocio extends ErrorDeApi {
        final String tipo;
        final String titulo;
        ReglaDeNegocio(String tipo, String titulo, String mensaje) {
            super(mensaje);
            this.tipo = tipo;
            this.titulo = titulo;
        }
    }
}
