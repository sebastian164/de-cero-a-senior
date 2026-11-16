package taller.abstractfactory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FabricasTest {

    private String generar(FabricaDocumento f) {
        return new GeneradorReporte(f).generar("Ventas", List.of("A", "B"),
                List.of(List.of("x", "1")), List.of(3, 6), "pie");
    }

    @Test
    void htmlEsCoherente() {
        String doc = generar(new FabricaHtml());
        assertTrue(doc.startsWith("<h1>Ventas</h1>"));
        assertTrue(doc.contains("<table><tr><th>A</th><th>B</th></tr><tr><td>x</td><td>1</td></tr></table>"));
        assertTrue(doc.contains("<svg data-valores=\"3,6\"/>"));
        assertTrue(doc.endsWith("<footer>pie</footer>"));
        assertFalse(doc.contains("# "), "no se mezclan piezas de otra familia");
    }

    @Test
    void textoPlanoNoTieneMarcas() {
        String doc = generar(new FabricaTexto());
        assertTrue(doc.startsWith("VENTAS\n======"));
        assertTrue(doc.contains("A\tB\nx\t1"));
        assertTrue(doc.contains("# 3\n## 6"));
        assertFalse(doc.contains("<"));
    }

    @Test
    void markdownEsLaTerceraFamilia() {
        String doc = generar(new FabricaMarkdown());
        assertTrue(doc.startsWith("# Ventas"));
        assertTrue(doc.contains("| A | B |\n|---|---|\n| x | 1 |"));
        assertTrue(doc.contains("- 3: ■\n- 6: ■■"), "gráfico propio de Markdown");
        assertFalse(doc.contains("# 3"), "no usa el gráfico de la familia texto");
        assertTrue(doc.endsWith("_pie_"));
    }

    @Test
    void cadaFamiliaCreaLosCuatroProductos() {
        for (FabricaDocumento f : List.of(new FabricaHtml(), new FabricaTexto(), new FabricaMarkdown())) {
            assertNotNull(f.crearEncabezado());
            assertNotNull(f.crearTabla());
            assertNotNull(f.crearPie());
            assertNotNull(f.crearGrafico());
        }
        assertEquals(4, FabricaDocumento.class.getDeclaredMethods().length,
                "agregar un producto amplió la interfaz a 4 métodos de creación");
    }
}
