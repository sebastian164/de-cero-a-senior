package taller.interpreter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterpreterTest {

    Map<String, Object> p1 = Map.of("vip", true, "total", 350_000L, "categoria", "HOGAR", "nuevo", false, "edad", 30L);
    Map<String, Object> p2 = Map.of("vip", false, "total", 40_000L, "categoria", "LIBROS", "nuevo", true, "edad", 17L);
    Map<String, Object> p3 = Map.of("vip", false, "total", 40_000L, "categoria", "LIBROS", "nuevo", false, "edad", 65L);

    @Test
    void nuevasExpresiones() {
        assertTrue(new MenorQue("total", 50_000).evaluar(p2));
        assertFalse(new MenorQue("total", 50_000).evaluar(p1));
        assertTrue(new Entre("edad", 18, 65).evaluar(p3), "inclusivo");
        assertFalse(new Entre("edad", 18, 65).evaluar(p2));
        assertTrue(new EnLista("categoria", List.of("LIBROS", "MUSICA")).evaluar(p2));
        assertFalse(new EnLista("categoria", List.of("LIBROS", "MUSICA")).evaluar(p1));
        assertThrows(ErrorDeRegla.class, () -> new Entre("edad", 65, 18));
    }

    @Test
    void elAnalizadorConstruyeElMismoArbolQueElEjemplo() {
        Regla r = Analizador.analizar("vip Y total > 200000 O categoria = LIBROS Y NO nuevo");
        assertEquals("((vip Y total > 200000) O (categoria = LIBROS Y NO nuevo))", r.describir());
        assertEquals(new O(new Y(new EsVerdadero("vip"), new MayorQue("total", 200_000)),
                new Y(new Igual("categoria", "LIBROS"), new No(new EsVerdadero("nuevo")))), r);
        assertTrue(r.evaluar(p1));
        assertFalse(r.evaluar(p2));
        assertTrue(r.evaluar(p3));
    }

    @Test
    void analizaLasExpresionesNuevas() {
        assertEquals(new Y(new MenorQue("total", 50_000), new EnLista("categoria", List.of("LIBROS", "MUSICA"))),
                Analizador.analizar("total < 50000 Y categoria EN LIBROS,MUSICA"));
        assertEquals(new Entre("edad", 18, 65), Analizador.analizar("edad ENTRE 18 65"));
    }

    @Test
    void erroresDeSintaxisClaros() {
        assertEquals("Se esperaba un número y llegó 'mucho'", assertThrows(ErrorDeRegla.class,
                () -> Analizador.analizar("total > mucho")).getMessage());
        assertEquals("Se esperaba un campo al final de la regla", assertThrows(ErrorDeRegla.class,
                () -> Analizador.analizar("vip Y")).getMessage());
        assertEquals("Sobra '200' en la posición 2", assertThrows(ErrorDeRegla.class,
                () -> Analizador.analizar("vip 200")).getMessage());
    }

    @Test
    void errorDeTiposIndicaCampoYRegla() {
        var e = assertThrows(ErrorDeRegla.class,
                () -> Analizador.analizar("vip Y total > 100").evaluar(Map.of("vip", true, "total", "mucho")));
        assertEquals("En la regla 'total > 100' el campo 'total' debe ser numérico, pero es String (\"mucho\")", e.getMessage());
        var f = assertThrows(ErrorDeRegla.class, () -> new EsVerdadero("vip").evaluar(Map.of("vip", "si")));
        assertTrue(f.getMessage().contains("'vip' debe ser verdadero/falso"));
        var g = assertThrows(ErrorDeRegla.class, () -> new MayorQue("total", 1).evaluar(Map.of()));
        assertEquals("En la regla 'total > 1' falta el campo 'total'", g.getMessage());
    }

    @Test
    void conPredicadosEvaluaIgualPeroNoSabeDescribirse() {
        var pred = Predicados.reglaDelEjemplo();
        Regla arbol = Analizador.analizar("vip Y total > 200000 O categoria = LIBROS Y NO nuevo");
        for (var ctx : List.of(p1, p2, p3)) assertEquals(arbol.evaluar(ctx), pred.test(ctx));
        assertFalse(pred.toString().contains("vip"), "toString de una lambda no dice nada de la regla: " + pred);
        assertThrows(ClassCastException.class, () -> pred.test(Map.of("vip", true, "total", "mucho")),
                "y el error de tipos vuelve a ser un ClassCastException sin contexto");
    }
}
