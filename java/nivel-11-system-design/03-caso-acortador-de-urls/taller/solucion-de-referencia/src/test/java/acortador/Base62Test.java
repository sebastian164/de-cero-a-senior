package acortador;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Base62Test {
    @Test
    void idaYVuelta() {
        for (long n : new long[]{0, 1, 61, 62, 3843, 123_456_789L, Base62.ESPACIO - 1}) {
            assertEquals(n, Base62.desdeBase62(Base62.aBase62(n)));
        }
        assertEquals("1C", Base62.aBase62(100)); // 100 = 1*62 + 38; el dígito 38 del alfabeto 0-9a-zA-Z es "C"
    }

    @Test
    void laPermutacionEsBiyectivaYReversible() {
        Set<Long> vistos = new HashSet<>();
        for (long n = 0; n < 200_000; n++) {
            long p = Base62.permutar(n);
            assertTrue(p >= 0 && p < Base62.ESPACIO);
            assertTrue(vistos.add(p), "colisión en " + n);
            assertEquals(n, Base62.despermutar(p));
        }
    }

    @Test
    void losCodigosSiempreTienenSieteCaracteres() {
        assertEquals(7, Base62.deSiete(0).length());
        assertEquals(7, Base62.deSiete(Base62.ESPACIO - 1).length());
    }

    /** Límite honesto de la permutación afín: oculta la adyacencia, pero es lineal; no es un secreto. */
    @Test
    void laPermutacionAfinEsPredecibleSiSeConoceUnPar() {
        long p1 = Base62.permutar(1000), p2 = Base62.permutar(1001);
        long paso = Math.floorMod(p2 - p1, Base62.ESPACIO);
        assertEquals(Base62.permutar(1002), Math.floorMod(p2 + paso, Base62.ESPACIO));
    }

    @Test
    void laPermutacionConClaveEsBiyectivaDentroDelEspacioYReversible() {
        var perm = new PermutacionConClave("clave-secreta-de-prueba");
        Set<Long> vistos = new HashSet<>();
        for (long n = 0; n < 200_000; n++) {
            long p = perm.aplicar(n);
            assertTrue(p >= 0 && p < Base62.ESPACIO);
            assertTrue(vistos.add(p), "colisión en " + n);
            assertEquals(n, perm.invertir(p));
        }
        for (long n : new long[]{Base62.ESPACIO - 1, Base62.ESPACIO / 2, 1L << 41}) assertEquals(n, perm.invertir(perm.aplicar(n)));
        assertThrows(IllegalArgumentException.class, () -> perm.aplicar(Base62.ESPACIO));
    }

    /** El ataque que rompe la afín (dos pares revelan el paso) deja de funcionar con la permutación con clave. */
    @Test
    void elAtaqueLinealYaNoFuncionaConLaPermutacionConClave() {
        var perm = new PermutacionConClave("clave-secreta-de-prueba");
        int aciertos = 0, aciertosAfin = 0;
        for (long n = 1000; n < 2000; n++) {
            long p1 = perm.aplicar(n), p2 = perm.aplicar(n + 1);
            if (perm.aplicar(n + 2) == Math.floorMod(p2 + Math.floorMod(p2 - p1, Base62.ESPACIO), Base62.ESPACIO)) aciertos++;
            long a1 = Base62.permutar(n), a2 = Base62.permutar(n + 1);
            if (Base62.permutar(n + 2) == Math.floorMod(a2 + Math.floorMod(a2 - a1, Base62.ESPACIO), Base62.ESPACIO)) aciertosAfin++;
        }
        assertEquals(1000, aciertosAfin, "la afín se predice siempre");
        assertEquals(0, aciertos, "la permutación con clave no se predice con el mismo ataque");
        // otra clave, otra permutación
        assertNotEquals(perm.aplicar(12345), new PermutacionConClave("otra-clave-de-prueba!").aplicar(12345));
    }

    @Test
    void alAgotarseElEspacioSeLanzaExcepcionEnLugarDeRepetirCodigos() {
        var g = new GeneradorPorRangos(() -> Base62.ESPACIO / 10_000, 10_000, Base62.AFIN); // el bloque pedido ya no cabe
        var e = assertThrows(IllegalStateException.class, g::siguiente);
        assertTrue(e.getMessage().contains("agotado"));
    }
}
