package taller.integrador;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Requisito 2: el precio depende de promociones que se combinan según reglas.
 * Cada promoción es una Strategy (calcula un descuento sobre el carrito); la regla de combinación
 * vive en un único lugar, el Cotizador:
 *   - las promociones "acumulables" se suman entre sí;
 *   - de las "exclusivas" se aplica solo la mejor, y no se acumula con ninguna otra:
 *     el cliente recibe lo que más le convenga entre (mejor exclusiva) y (suma de acumulables).
 */
final class Promociones {
    private Promociones() { }

    record Carrito(List<Catalogo.Curso> cursos, Optional<String> cupon, LocalDate fecha) {
        Carrito { cursos = List.copyOf(cursos); }
        long bruto() { return cursos.stream().mapToLong(Catalogo.Curso::precio).sum(); }
    }

    interface Promocion {
        String nombre();
        boolean acumulable();
        long descuento(Carrito carrito);
    }

    /** Descuento por lanzamiento: un porcentaje sobre ciertos cursos hasta una fecha. */
    record Lanzamiento(String cursoId, int porcentaje, LocalDate hasta) implements Promocion {
        public String nombre() { return "Lanzamiento " + cursoId + " -" + porcentaje + "%"; }
        public boolean acumulable() { return true; }
        public long descuento(Carrito c) {
            if (c.fecha().isAfter(hasta)) return 0;
            return c.cursos().stream().filter(x -> x.id().equals(cursoId)).mapToLong(x -> x.precio() * porcentaje / 100).sum();
        }
    }

    /** Descuento por paquete: N o más cursos en la misma compra. Exclusiva. */
    record Paquete(int minimoCursos, int porcentaje) implements Promocion {
        public String nombre() { return "Paquete " + minimoCursos + "+ cursos -" + porcentaje + "%"; }
        public boolean acumulable() { return false; }
        public long descuento(Carrito c) { return c.cursos().size() >= minimoCursos ? c.bruto() * porcentaje / 100 : 0; }
    }

    record Cupon(String codigo, int porcentaje) implements Promocion {
        public String nombre() { return "Cupón " + codigo; }
        public boolean acumulable() { return true; }
        public long descuento(Carrito c) { return c.cupon().filter(codigo::equals).isPresent() ? c.bruto() * porcentaje / 100 : 0; }
    }

    /**
     * Parte 3, cambio 3: 2x1 en cursos de la misma categoría. Por cada par de la misma categoría,
     * el más barato es gratis. Exclusiva. Solo se agregó esta clase y se registró.
     */
    record DosPorUno() implements Promocion {
        public String nombre() { return "2x1 misma categoría"; }
        public boolean acumulable() { return false; }
        public long descuento(Carrito c) {
            Map<String, List<Long>> porCategoria = c.cursos().stream().collect(Collectors.groupingBy(
                    Catalogo.Curso::categoria, Collectors.mapping(Catalogo.Curso::precio, Collectors.toList())));
            long total = 0;
            for (List<Long> precios : porCategoria.values()) {
                List<Long> orden = new ArrayList<>(precios);
                orden.sort(Comparator.reverseOrder());
                for (int i = 1; i < orden.size(); i += 2) total += orden.get(i);   // el segundo de cada par
            }
            return total;
        }
    }

    record Cotizacion(long bruto, long descuento, List<String> aplicadas) {
        long neto() { return bruto - descuento; }
    }

    static final class Cotizador {
        private final List<Promocion> vigentes;
        Cotizador(Collection<Promocion> vigentes) { this.vigentes = List.copyOf(vigentes); }

        Cotizacion cotizar(Carrito c) {
            List<Promocion> acumulables = vigentes.stream().filter(Promocion::acumulable).filter(p -> p.descuento(c) > 0).toList();
            long sumaAcumulables = acumulables.stream().mapToLong(p -> p.descuento(c)).sum();
            Optional<Promocion> mejorExclusiva = vigentes.stream().filter(p -> !p.acumulable())
                    .filter(p -> p.descuento(c) > 0).max(Comparator.comparingLong(p -> p.descuento(c)));

            long bruto = c.bruto();
            if (mejorExclusiva.isPresent() && mejorExclusiva.get().descuento(c) >= sumaAcumulables) {
                Promocion p = mejorExclusiva.get();
                return new Cotizacion(bruto, Math.min(bruto, p.descuento(c)), List.of(p.nombre()));
            }
            return new Cotizacion(bruto, Math.min(bruto, sumaAcumulables), acumulables.stream().map(Promocion::nombre).toList());
        }
    }
}
