/** Puerto en el idioma del negocio: cobrar una renovación. No sabe de monedas, reintentos ni claves. */
public interface Cobros {
    String cobrar(String usuario, long valor);
}
