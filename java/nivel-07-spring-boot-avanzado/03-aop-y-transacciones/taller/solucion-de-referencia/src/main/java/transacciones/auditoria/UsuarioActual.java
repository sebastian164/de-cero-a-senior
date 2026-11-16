package transacciones.auditoria;

/** En una aplicación con Spring Security esto saldría del SecurityContext; aquí, de un ThreadLocal. */
public final class UsuarioActual {
    private static final ThreadLocal<String> USUARIO = ThreadLocal.withInitial(() -> "anonimo");
    private UsuarioActual() { }
    public static void es(String usuario) { USUARIO.set(usuario); }
    public static String quien() { return USUARIO.get(); }
    public static void limpiar() { USUARIO.remove(); }
}
