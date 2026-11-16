public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        // El doble de prueba del tablero: una lambda.
        igual("tablero con 3", "Usuarios activos este mes: 3", new TableroMetricas(() -> 3).resumen());
        igual("tablero con 0", "Sin usuarios activos este mes", new TableroMetricas(() -> 0).resumen());

        ServicioUsuariosEnMemoria s = new ServicioUsuariosEnMemoria();
        s.registrar("ana", "ana@correo.example");
        s.registrar("luis", "luis@correo.example");
        s.bloquear("luis");
        igual("bloquear ya no es vacío", true, s.estaBloqueado("luis"));
        igual("activos excluye bloqueados", 1, s.contarActivosUltimoMes());
        s.enviarCorreoMasivo("Promo", "...");
        igual("correo masivo no va a bloqueados", 1, s.correosEnviados().size());
        igual("csv", "ana\nluis", s.exportarCsv());
        s.eliminar("luis");
        igual("eliminar", java.util.List.of("ana"), s.listarTodos());
        total++;
        try { s.cambiarContrasena("nadie", "x"); fallos++; } catch (IllegalArgumentException e) { }
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }
}
