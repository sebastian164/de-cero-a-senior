import java.util.List;

/**
 * Se compila con taller/inicio/Main.java del repo: el doble que necesitaba el tablero
 * cuando dependía de la interfaz gorda ServicioUsuarios. Nueve métodos para usar uno.
 */
class DobleAntes implements ServicioUsuarios {
    private final int activos;
    DobleAntes(int activos) { this.activos = activos; }
    @Override public void registrar(String usuario, String correo) { throw new UnsupportedOperationException(); }
    @Override public String buscar(String usuario) { throw new UnsupportedOperationException(); }
    @Override public List<String> listarTodos() { throw new UnsupportedOperationException(); }
    @Override public void cambiarContrasena(String usuario, String nueva) { throw new UnsupportedOperationException(); }
    @Override public void bloquear(String usuario) { throw new UnsupportedOperationException(); }
    @Override public void eliminar(String usuario) { throw new UnsupportedOperationException(); }
    @Override public String exportarCsv() { throw new UnsupportedOperationException(); }
    @Override public void enviarCorreoMasivo(String asunto, String cuerpo) { throw new UnsupportedOperationException(); }
    @Override public int contarActivosUltimoMes() { return activos; }
}

class TableroAntes {
    private final ServicioUsuarios servicio;
    TableroAntes(ServicioUsuarios servicio) { this.servicio = servicio; }
    String resumen() { return "Usuarios activos este mes: " + servicio.contarActivosUltimoMes(); }
}

class PruebaAntes {
    public static void main(String[] args) {
        String r = new TableroAntes(new DobleAntes(3)).resumen();
        System.out.println(r);
        if (!r.equals("Usuarios activos este mes: 3")) System.exit(1);
    }
}
