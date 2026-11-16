package taller.iterator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class IteratorTest {

    // 2025-06-06 es viernes; 7 y 8 son fin de semana.
    RangoFechas rango = new RangoFechas(LocalDate.of(2025, 6, 6), LocalDate.of(2025, 6, 10));

    static List<LocalDate> lista(Iterable<LocalDate> it) {
        List<LocalDate> r = new ArrayList<>();
        for (LocalDate d : it) r.add(d);
        return r;
    }

    @Test
    void recorreTodosLosDiasInclusivo() {
        assertEquals(5, lista(rango).size());
        assertEquals(LocalDate.of(2025, 6, 6), lista(rango).get(0));
        assertEquals(LocalDate.of(2025, 6, 10), lista(rango).get(4));
        assertEquals(1, lista(new RangoFechas(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))).size());
    }

    @Test
    void soloDiasHabiles() {
        assertEquals(List.of(LocalDate.of(2025, 6, 6), LocalDate.of(2025, 6, 9), LocalDate.of(2025, 6, 10)), lista(rango.diasHabiles()));
        assertEquals(List.of(), lista(new RangoFechas(LocalDate.of(2025, 6, 7), LocalDate.of(2025, 6, 8)).diasHabiles()));
    }

    @Test
    void lasVersionesStreamDanLoMismo() {
        assertEquals(lista(rango), rango.streamDias().toList());
        assertEquals(lista(rango.diasHabiles()), rango.streamDiasHabiles().toList());
    }

    @Test
    void cadaRecorridoEsIndependienteYRespetaElContrato() {
        Iterator<LocalDate> a = rango.iterator();
        Iterator<LocalDate> b = rango.iterator();
        a.next();
        assertEquals(LocalDate.of(2025, 6, 6), b.next());
        Iterator<LocalDate> it = new RangoFechas(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)).iterator();
        it.next();
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void concurrentModificationYSusCorrecciones() {
        assertEquals("ConcurrentModificationException", Modificacion.provocar());
        assertEquals(List.of(1, 3, 5), Modificacion.conIteratorRemove());
        assertEquals(List.of(1, 3, 5), Modificacion.conRemoveIf());
    }
}
