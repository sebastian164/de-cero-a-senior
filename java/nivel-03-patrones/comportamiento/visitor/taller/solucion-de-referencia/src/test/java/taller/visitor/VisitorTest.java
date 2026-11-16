package taller.visitor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisitorTest {

    // -(2 + 3) * (4 / 2) * 1 + 0
    Expr e = new Suma(new Multiplicacion(new Multiplicacion(new Negacion(new Suma(new Numero(2), new Numero(3))),
            new Division(new Numero(4), new Numero(2))), new Numero(1)), new Numero(0));

    @Test
    void tresOperacionesConVisitor() {
        assertEquals(-10L, e.aceptar(new Evaluar()));
        assertEquals("(((-(2 + 3) * (4 / 2)) * 1) + 0)", e.aceptar(new Imprimir()));
        assertEquals(12, e.aceptar(new ContarNodos()));
    }

    @Test
    void lasMismasConSealed() {
        Arbol.Nodo n = Arbol.desde(e);
        assertEquals(-10L, Arbol.evaluar(n));
        assertEquals(e.aceptar(new Imprimir()), Arbol.imprimir(n));
        assertEquals(12, Arbol.contar(n));
    }

    @Test
    void simplificarEnAmbas() {
        Expr s = e.aceptar(new Simplificar());
        assertEquals("(-(2 + 3) * (4 / 2))", s.aceptar(new Imprimir()));
        assertEquals(-10L, s.aceptar(new Evaluar()), "simplificar no cambia el valor");
        assertEquals("(-(2 + 3) * (4 / 2))", Arbol.imprimir(Arbol.simplificar(Arbol.desde(e))));
        assertEquals(new Numero(7), new Negacion(new Negacion(new Numero(7))).aceptar(new Simplificar()));
        assertEquals(new Arbol.Num(7), Arbol.simplificar(new Arbol.Menos(new Arbol.Menos(new Arbol.Num(7)))));
        assertEquals("x / 1 -> x: 3", "x / 1 -> x: " + new Division(new Numero(3), new Numero(1)).aceptar(new Simplificar()).aceptar(new Imprimir()));
    }

    @Test
    void divisionPorCero() {
        Expr d = new Division(new Numero(1), new Suma(new Numero(1), new Negacion(new Numero(1))));
        assertEquals("División por cero en (1 / (1 + -1))", assertThrows(ArithmeticException.class, () -> d.aceptar(new Evaluar())).getMessage());
        assertThrows(ArithmeticException.class, () -> Arbol.evaluar(Arbol.desde(d)));
    }

    // ---------- Paso 3: qué deja de compilar ----------

    static String compilar(String recurso, Path dir) throws Exception {
        Path fuente = dir.resolve(recurso);
        Files.writeString(fuente, new String(VisitorTest.class.getResourceAsStream("/experimentos/" + recurso).readAllBytes()));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        StringWriter errores = new StringWriter();
        boolean ok = javac.getTask(errores, null, null, List.of("-d", dir.toString()), null,
                javac.getStandardFileManager(null, null, null).getJavaFileObjects(fuente)).call();
        return ok ? "OK" : errores.toString();
    }

    @Test
    void visitorSinElMetodoNuevoNoCompila(@TempDir Path dir) throws Exception {
        String r = compilar("VisitorSinDivision.java", dir);
        assertTrue(r.contains("ContarNodos is not abstract and does not override abstract method visitar(Division)"), r);
    }

    @Test
    void switchSinElCasoNuevoNoCompila(@TempDir Path dir) throws Exception {
        String r = compilar("SwitchSinDivision.java", dir);
        assertTrue(r.contains("the switch expression does not cover all possible input values"), r);
    }

    @Test
    void instanceofSueltoCompilaYFallaraEnEjecucion(@TempDir Path dir) throws Exception {
        assertEquals("OK", compilar("InstanceofSinDivision.java", dir));
    }
}
