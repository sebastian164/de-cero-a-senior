package moderno;

import java.util.Objects;

/** Clase "de antes": getters, equals, hashCode y toString escritos a mano. */
public final class ClienteManual {
    private final String documento;
    private final String nombre;
    private final String correo;

    public ClienteManual(String documento, String nombre, String correo) {
        if (documento == null || documento.isBlank()) throw new IllegalArgumentException("documento obligatorio");
        this.documento = documento;
        this.nombre = nombre;
        this.correo = correo;
    }

    public String getDocumento() { return documento; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClienteManual c)) return false;
        return documento.equals(c.documento) && Objects.equals(nombre, c.nombre) && Objects.equals(correo, c.correo);
    }
    @Override public int hashCode() { return Objects.hash(documento, nombre, correo); }
    @Override public String toString() { return "ClienteManual[documento=" + documento + ", nombre=" + nombre + ", correo=" + correo + "]"; }
}
