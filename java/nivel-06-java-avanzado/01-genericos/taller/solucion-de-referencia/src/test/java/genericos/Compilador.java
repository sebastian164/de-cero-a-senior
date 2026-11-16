package genericos;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Compila código en memoria y devuelve los mensajes de error en inglés (para comprobar qué NO compila). */
final class Compilador {
    private Compilador() { }

    static List<String> errores(String nombreClase, String codigo) {
        var compilador = ToolProvider.getSystemJavaCompiler();
        var diagnosticos = new DiagnosticCollector<JavaFileObject>();
        var fuente = new SimpleJavaFileObject(URI.create("string:///" + nombreClase + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignorar) { return codigo; }
        };
        var archivos = compilador.getStandardFileManager(diagnosticos, Locale.ENGLISH, null);
        compilador.getTask(null, archivos, diagnosticos, List.of("-d", System.getProperty("java.io.tmpdir")), null, List.of(fuente)).call();
        return diagnosticos.getDiagnostics().stream()
                .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(Locale.ENGLISH)).toList();
    }
}
