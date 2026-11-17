import java.util.List;

/** Pruebas de la función pura etiquetas() (tarea 1). Se ejecuta con: javac Servidor.java PruebaEtiquetas.java && java PruebaEtiquetas */
public class PruebaEtiquetas {
    static int fallos = 0;

    static void caso(String texto, List<String> esperado) {
        List<String> real = Servidor.etiquetas(texto);
        if (!real.equals(esperado)) {
            fallos++;
            System.out.println("FALLA  \"" + texto + "\" -> " + real + " (esperado " + esperado + ")");
        }
    }

    public static void main(String[] args) {
        caso("ir al banco #trabajo", List.of("trabajo"));
        caso("a#b #c", List.of("c"));
        caso("#", List.of());
        caso("##x", List.of());
        caso("# x", List.of());
        caso("#trabajo, #casa.", List.of("trabajo", "casa"));
        caso("#Trabajo", List.of("trabajo"));
        caso("#a #A #a", List.of("a"));
        caso("x #Uno #dos #UNO", List.of("uno", "dos"));
        caso("sin etiquetas", List.of());
        caso("#" + "a".repeat(31), List.of());
        caso("#" + "a".repeat(30), List.of("a".repeat(30)));
        caso("#Café #cafe", List.of("café", "cafe"));
        caso("#a-b_c", List.of("a-b_c"));
        caso("#ÑANDÚ", List.of("ñandú"));
        caso("#1 #22", List.of("1", "22"));
        caso("", List.of());
        caso("  #x", List.of("x"));
        caso("x\t#y", List.of("y"));
        if (fallos > 0) {
            System.out.println(fallos + " casos fallan");
            System.exit(1);
        }
        System.out.println("PRUEBAS etiquetas(): todos los casos pasan");
    }
}
