package taller.iterator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        RangoFechas semana = new RangoFechas(LocalDate.of(2025, 6, 6), LocalDate.of(2025, 6, 10));
        for (LocalDate dia : semana) System.out.println("todos:   " + dia + " " + dia.getDayOfWeek());
        for (LocalDate dia : semana.diasHabiles()) System.out.println("hábiles: " + dia);
        System.out.println("con Stream: " + semana.streamDiasHabiles().toList());

        System.out.println("CME: " + Modificacion.provocar());
        System.out.println("Con Iterator.remove: " + Modificacion.conIteratorRemove());
        System.out.println("Con removeIf:        " + Modificacion.conRemoveIf());
    }
}

/** Paso 1 y 2. Inclusivo en ambos extremos. */
final class RangoFechas implements Iterable<LocalDate> {
    private final LocalDate inicio;
    private final LocalDate fin;

    RangoFechas(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio)) throw new IllegalArgumentException("El fin es anterior al inicio");
        this.inicio = inicio;
        this.fin = fin;
    }

    @Override
    public Iterator<LocalDate> iterator() {
        return new Iterator<>() {
            private LocalDate siguiente = inicio;
            @Override public boolean hasNext() { return !siguiente.isAfter(fin); }
            @Override public LocalDate next() {
                if (!hasNext()) throw new NoSuchElementException();
                LocalDate actual = siguiente;
                siguiente = siguiente.plusDays(1);
                return actual;
            }
        };
    }

    /** Segundo recorrido: un Iterable distinto sobre los mismos datos. */
    Iterable<LocalDate> diasHabiles() {
        return () -> new Iterator<>() {
            private final Iterator<LocalDate> todos = RangoFechas.this.iterator();
            private LocalDate pendiente = avanzar();

            private LocalDate avanzar() {
                while (todos.hasNext()) {
                    LocalDate d = todos.next();
                    if (esHabil(d)) return d;
                }
                return null;
            }
            @Override public boolean hasNext() { return pendiente != null; }
            @Override public LocalDate next() {
                if (pendiente == null) throw new NoSuchElementException();
                LocalDate r = pendiente;
                pendiente = avanzar();
                return r;
            }
        };
    }

    static boolean esHabil(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    // ----- Paso 3: con Stream -----
    Stream<LocalDate> streamDias() { return inicio.datesUntil(fin.plusDays(1)); }   // datesUntil excluye el final
    Stream<LocalDate> streamDiasHabiles() { return streamDias().filter(RangoFechas::esHabil); }
}

/** Paso 4. */
final class Modificacion {
    private Modificacion() { }

    static List<Integer> datos() { return new ArrayList<>(List.of(1, 2, 3, 4, 5, 6)); }

    static String provocar() {
        List<Integer> lista = datos();
        try {
            for (Integer n : lista) {
                if (n % 2 == 0) lista.remove(n);        // modifica la lista mientras el for-each la recorre
            }
            return "no falló";
        } catch (ConcurrentModificationException e) {
            return "ConcurrentModificationException";
        }
    }

    static List<Integer> conIteratorRemove() {
        List<Integer> lista = datos();
        for (Iterator<Integer> it = lista.iterator(); it.hasNext(); ) {
            if (it.next() % 2 == 0) it.remove();        // el iterador sabe que la modificación es suya
        }
        return lista;
    }

    static List<Integer> conRemoveIf() {
        List<Integer> lista = datos();
        lista.removeIf(n -> n % 2 == 0);
        return lista;
    }
}
