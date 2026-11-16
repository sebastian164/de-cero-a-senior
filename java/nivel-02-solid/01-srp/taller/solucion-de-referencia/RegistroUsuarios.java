/**
 * El flujo del registro: en qué orden se valida, se guarda, se avisa y se audita.
 * Cambia solo si cambian los pasos del proceso.
 */
public class RegistroUsuarios {

    private final ValidadorDatosUsuario validador;
    private final PoliticaContrasenas politica;
    private final AlmacenUsuarios almacen;
    private final CorreoBienvenida correoBienvenida;
    private final Auditoria auditoria;

    public RegistroUsuarios(ValidadorDatosUsuario validador, PoliticaContrasenas politica, AlmacenUsuarios almacen,
                            CorreoBienvenida correoBienvenida, Auditoria auditoria) {
        this.validador = validador;
        this.politica = politica;
        this.almacen = almacen;
        this.correoBienvenida = correoBienvenida;
        this.auditoria = auditoria;
    }

    public void registrar(String nombre, String correo, String contrasena) {
        validador.validar(nombre, correo);
        politica.validar(contrasena);
        if (almacen.existe(correo)) {
            throw new IllegalStateException("Ya existe un usuario con ese correo");
        }
        almacen.guardar(nombre, correo, politica.proteger(contrasena));
        correoBienvenida.enviar(nombre, correo);
        auditoria.registroUsuario(correo);
    }

    public int totalUsuarios() {
        return almacen.total();
    }
}
