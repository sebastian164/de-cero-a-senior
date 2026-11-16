package moderno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static moderno.FormaDePago.Franquicia.AMEX;
import static moderno.FormaDePago.Franquicia.VISA;
import static org.assertj.core.api.Assertions.assertThat;

class ModernoTest {

    @Test
    void comisionPorFormaDePago() {
        assertThat(FormaDePago.comision(new FormaDePago.Tarjeta(VISA, "1234", 1), 100_000)).isEqualTo(2_900);
        assertThat(FormaDePago.comision(new FormaDePago.Tarjeta(VISA, "1234", 6), 100_000)).isEqualTo(2_900 + 3_000);
        assertThat(FormaDePago.comision(new FormaDePago.Tarjeta(AMEX, "0005", 6), 100_000)).isEqualTo(3_500);
        assertThat(FormaDePago.comision(new FormaDePago.Transferencia("X", true), 100_000)).isZero();
        assertThat(FormaDePago.comision(new FormaDePago.Transferencia("X", false), 100_000)).isEqualTo(7_000);
        assertThat(FormaDePago.comision(new FormaDePago.Efectivo("Baloto"), 100_000)).isEqualTo(3_500);
        assertThat(FormaDePago.comision(new FormaDePago.Billetera("Nequi", "300"), 10_000)).isEqualTo(1_000);
        assertThat(FormaDePago.comision(new FormaDePago.Billetera("Nequi", "300"), 1_000_000)).isEqualTo(15_000);
    }

    /** 2. Se agrega Cripto a la jerarquía sellada sin tocar el switch: deja de compilar. */
    @Test
    void agregarUnaFormaDePagoRompeLaCompilacionDelSwitch() throws Exception {
        String fuente = Files.readString(Path.of("src/main/java/moderno/FormaDePago.java"))
                .replace("FormaDePago.Billetera {", "FormaDePago.Billetera, FormaDePago.Cripto {")
                .replace("    record Billetera(", "    record Cripto(String red) implements FormaDePago { }\n    record Billetera(");
        List<String> errores = compilar("moderno/FormaDePago", fuente);
        System.out.println(errores);
        assertThat(errores).singleElement().asString().isEqualTo("the switch expression does not cover all possible input values");
    }

    @Test
    void recordFrenteAClaseManual() {
        var manual = new ClienteManual("1", "Ana", "ana@x.co");
        var record = new Cliente("1", "Ana", "ana@x.co");
        assertThat(manual).isEqualTo(new ClienteManual("1", "Ana", "ana@x.co"));
        assertThat(record).isEqualTo(new Cliente("1", "Ana", "ana@x.co")).hasSameHashCodeAs(new Cliente("1", "Ana", "ana@x.co"));
        assertThat(record.toString()).isEqualTo("Cliente[documento=1, nombre=Ana, correo=ana@x.co]");
        assertThat(manual.getNombre()).isEqualTo(record.nombre());          // getNombre() pasó a nombre()
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Cliente(" ", "x", "y")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Cliente.class.getDeclaredConstructors()).allMatch(c -> c.getParameterCount() == 3);   // no hay constructor vacío
    }

    @Test
    void patronesEnLugarDeInstanceofEnCadena() {
        List<Figuras.Figura> figuras = List.of(new Figuras.Circulo(11), new Figuras.Circulo(2),
                new Figuras.Rectangulo(3, 3), new Figuras.Rectangulo(2, 5), new Figuras.Triangulo(4, 3));
        for (var f : figuras) assertThat(Figuras.describir(f)).isEqualTo(Figuras.describirAntes(f));
        assertThat(Figuras.describir(new Figuras.Rectangulo(3, 3))).isEqualTo("cuadrado de lado 3.0");
    }

    @Test
    void visitorDaLoMismoQueElSwitch() {
        long monto = 250_000;
        var v = new ComisionVisitor.Comision(monto);
        assertThat(new ComisionVisitor.Tarjeta(VISA, 3).aceptar(v)).isEqualTo(FormaDePago.comision(new FormaDePago.Tarjeta(VISA, "1", 3), monto));
        assertThat(new ComisionVisitor.Tarjeta(AMEX, 1).aceptar(v)).isEqualTo(FormaDePago.comision(new FormaDePago.Tarjeta(AMEX, "1", 1), monto));
        assertThat(new ComisionVisitor.Transferencia(false).aceptar(v)).isEqualTo(7_000L);
        assertThat(new ComisionVisitor.Billetera().aceptar(v)).isEqualTo(FormaDePago.comision(new FormaDePago.Billetera("n", "1"), monto));
    }

    /** 6. Las tres novedades elegidas, ejecutadas con un JDK posterior al 21. */
    @Test
    void novedadesPosterioresAJava21() throws Exception {
        Path jdk = Path.of(System.getProperty("jdk.reciente", "/opt/homebrew/opt/openjdk"), "bin", "java");
        Assumptions.assumeTrue(Files.isExecutable(jdk), "no hay JDK posterior al 21 en " + jdk);
        Process p = new ProcessBuilder(jdk.toString(), "post21/Novedades.java").redirectErrorStream(true).start();
        String salida = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        System.out.println(salida);
        assertThat(salida).contains("x=3", "elementos=3", "ventanas=[[1, 2], [2, 3], [3, 4]]",
                "validado antes de super: saldo negativo", "saldo=10");
    }

    static List<String> compilar(String nombre, String codigo) {
        var compilador = ToolProvider.getSystemJavaCompiler();
        var diagnosticos = new DiagnosticCollector<JavaFileObject>();
        var fuente = new SimpleJavaFileObject(URI.create("string:///" + nombre + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignorar) { return codigo; }
        };
        compilador.getTask(null, compilador.getStandardFileManager(diagnosticos, Locale.ENGLISH, null), diagnosticos,
                List.of("-d", System.getProperty("java.io.tmpdir") + "/moderno-compilado"), null, List.of(fuente)).call();
        return diagnosticos.getDiagnostics().stream().filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(Locale.ENGLISH)).toList();
    }
}
