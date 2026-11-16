package correo;

/** Paso 1: sin configuración no hay nada que conectar. */
public record Configuracion(String servidor, int puerto) {
    public Configuracion {
        if (servidor == null || servidor.isBlank()) throw new IllegalArgumentException("Falta el servidor");
        if (puerto <= 0) throw new IllegalArgumentException("Puerto inválido");
    }

    /** Paso 2: conectar devuelve el único tipo que sabe enviar. */
    public Conexion conectar() {
        return new Conexion(this);
    }
}
