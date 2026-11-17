package biblioteca;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

/** Capa de consola: traduce comandos de texto a llamadas a las reglas. */
public class Consola {
    private final Biblioteca b;
    private final PrintStream out;

    public Consola(Biblioteca b, PrintStream out) { this.b = b; this.out = out; }

    public void ejecutar(Scanner in) {
        while (in.hasNextLine()) {
            String[] t = in.nextLine().trim().split("\\s+");
            try {
                switch (t[0]) {
                    case "libro" -> { b.agregarLibro(t[1], t[2], Integer.parseInt(t[3])); out.println("ok"); }
                    case "socio" -> { b.registrarSocio(t[1], t[2]); out.println("ok"); }
                    case "prestar" -> out.println(b.prestar(t[1], t[2]));
                    case "devolver" -> out.println("multa=" + b.devolver(t[1], t[2]));
                    case "pagar" -> { b.pagarMulta(t[1]); out.println("ok"); }
                    case "salir" -> { return; }
                    default -> out.println("comando desconocido: " + t[0]);
                }
            } catch (Biblioteca.ReglaViolada e) {
                out.println("NO: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Biblioteca b = new Biblioteca(new AlmacenArchivo(Path.of(args.length > 0 ? args[0] : "biblioteca.tsv")), Clock.systemDefaultZone());
        new Consola(b, System.out).ejecutar(new Scanner(System.in));
    }
}
