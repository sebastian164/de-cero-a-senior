/** La política de contraseñas se prueba sola: sin almacén, sin correo, sin auditoría. */
public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        PoliticaContrasenas politica = new PoliticaContrasenas();
        lanza("corta", () -> politica.validar("abc1"));
        lanza("sin número", () -> politica.validar("abcdefgh"));
        lanza("nula", () -> politica.validar(null));
        politica.validar("clave1234");
        igual("hash SHA-256 de 64 hexadecimales", 64, politica.proteger("x").length());
        igual("hash determinista", politica.proteger("clave1234"), politica.proteger("clave1234"));
        ValidadorDatosUsuario v = new ValidadorDatosUsuario();
        lanza("nombre vacío", () -> v.validar(" ", "a@b.co"));
        lanza("correo inválido", () -> v.validar("Ana", "ana@correo"));
        AlmacenUsuarios a = new AlmacenUsuarios();
        a.guardar("Ana", "ANA@b.co", "h");
        igual("correo sin mayúsculas", true, a.existe("ana@B.co"));
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }

    static void lanza(String n, Runnable a) {
        total++;
        try { a.run(); fallos++; System.out.println("FALLA " + n + ": no lanzó"); }
        catch (IllegalArgumentException x) { }
    }
}
