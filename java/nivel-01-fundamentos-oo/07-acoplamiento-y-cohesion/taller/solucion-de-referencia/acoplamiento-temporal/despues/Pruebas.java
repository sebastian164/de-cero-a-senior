import correo.Configuracion;


public class Pruebas {
    public static void main(String[] args) {
        String linea = new Configuracion("smtp.ejemplo.test", 25).conectar().enviar("a@b.example", "x");
        boolean ok = linea.equals("Enviado a a@b.example por smtp.ejemplo.test:25: x");
        try { new Configuracion("", 25); ok = false; } catch (IllegalArgumentException e) { }
        System.out.println(ok ? "2 pruebas, 0 fallos" : "FALLA");
        if (!ok) System.exit(1);
    }
}
