package biblioteca;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Reglas de la biblioteca. No sabe de archivos ni de consola: guarda a través de {@link Almacen}. */
public class Biblioteca {
    public static final int MAX_PRESTAMOS = 3;
    public static final int PLAZO_DIAS = 14;
    public static final int MULTA_POR_DIA_CENTAVOS = 50;

    public record Libro(String isbn, String titulo, int ejemplares) {}
    /** El socio protege sus propias reglas: quién puede pedir prestado y cómo acumula o paga multas. */
    public record Socio(String id, String nombre, int multaCentavos) {
        public void validarPuedePedir(int prestadosAhora) {
            if (multaCentavos > 0) throw new ReglaViolada("El socio " + id + " tiene multas pendientes");
            if (prestadosAhora >= MAX_PRESTAMOS) throw new ReglaViolada("Máximo " + MAX_PRESTAMOS + " préstamos");
        }
        public Socio conMultaAcumulada(int centavos) { return new Socio(id, nombre, multaCentavos + centavos); }
        public Socio conMultaPagada() { return new Socio(id, nombre, 0); }
    }
    /** El préstamo sabe cuándo vence y cuánto se debe si se devuelve en una fecha. */
    public record Prestamo(String isbn, String socioId, LocalDate desde) {
        public LocalDate vence() { return desde.plusDays(PLAZO_DIAS); }
        public int multaAl(LocalDate hoy) { return (int) Math.max(0, ChronoUnit.DAYS.between(vence(), hoy)) * MULTA_POR_DIA_CENTAVOS; }
    }
    public record Espera(String isbn, String socioId) {}
    public record Estado(List<Libro> libros, List<Socio> socios, List<Prestamo> prestamos, List<Espera> esperas) {}
    public enum Resultado { PRESTADO, EN_LISTA_DE_ESPERA }

    public static class ReglaViolada extends RuntimeException {
        public ReglaViolada(String m) { super(m); }
    }

    public interface Almacen {
        Estado cargar();
        void guardar(Estado estado);
    }

    private final Map<String, Libro> libros = new LinkedHashMap<>();
    private final Map<String, Socio> socios = new LinkedHashMap<>();
    private final List<Prestamo> prestamos = new ArrayList<>();
    private final List<Espera> esperas = new ArrayList<>();
    private final Almacen almacen;
    private final Clock reloj;

    public Biblioteca(Almacen almacen, Clock reloj) {
        this.almacen = almacen;
        this.reloj = reloj;
        Estado e = almacen.cargar();
        e.libros().forEach(l -> libros.put(l.isbn(), l));
        e.socios().forEach(s -> socios.put(s.id(), s));
        prestamos.addAll(e.prestamos());
        esperas.addAll(e.esperas());
    }

    public void agregarLibro(String isbn, String titulo, int ejemplares) {
        libros.merge(isbn, new Libro(isbn, titulo, ejemplares),
                (a, b) -> new Libro(isbn, a.titulo(), a.ejemplares() + ejemplares));
        guardar();
    }

    public void registrarSocio(String id, String nombre) {
        socios.putIfAbsent(id, new Socio(id, nombre, 0));
        guardar();
    }

    public Resultado prestar(String isbn, String socioId) {
        Socio s = socio(socioId);
        Libro l = libro(isbn);
        validar(s);
        if (disponibles(l) > 0 && esperas.stream().noneMatch(e -> e.isbn().equals(isbn))) {
            prestamos.add(new Prestamo(isbn, socioId, LocalDate.now(reloj)));
            guardar();
            return Resultado.PRESTADO;
        }
        if (esperas.contains(new Espera(isbn, socioId))) throw new ReglaViolada("Ya está en la lista de espera");
        esperas.add(new Espera(isbn, socioId));
        guardar();
        return Resultado.EN_LISTA_DE_ESPERA;
    }

    /** Devuelve el libro, cobra la multa por retraso y entrega el ejemplar al primero de la lista de espera que pueda recibirlo. */
    public int devolver(String isbn, String socioId) {
        Prestamo p = prestamos.stream().filter(x -> x.isbn().equals(isbn) && x.socioId().equals(socioId)).findFirst()
                .orElseThrow(() -> new ReglaViolada("Ese socio no tiene ese libro"));
        prestamos.remove(p);
        int multa = p.multaAl(LocalDate.now(reloj));
        if (multa > 0) socios.put(socioId, socio(socioId).conMultaAcumulada(multa));
        for (Iterator<Espera> it = esperas.iterator(); it.hasNext() && disponibles(libro(isbn)) > 0; ) {
            Espera e = it.next();
            if (!e.isbn().equals(isbn)) continue;
            try {
                validar(socio(e.socioId()));
            } catch (ReglaViolada ignorada) {
                continue;
            }
            it.remove();
            prestamos.add(new Prestamo(isbn, e.socioId(), LocalDate.now(reloj)));
        }
        guardar();
        return multa;
    }

    public void pagarMulta(String socioId) {
        socios.put(socioId, socio(socioId).conMultaPagada());
        guardar();
    }

    public int disponibles(Libro l) {
        return l.ejemplares() - (int) prestamos.stream().filter(p -> p.isbn().equals(l.isbn())).count();
    }

    public int disponibles(String isbn) { return disponibles(libro(isbn)); }

    public int multa(String socioId) { return socio(socioId).multaCentavos(); }

    public List<Prestamo> prestamosDe(String socioId) {
        return prestamos.stream().filter(p -> p.socioId().equals(socioId)).toList();
    }

    public List<String> listaDeEspera(String isbn) {
        return esperas.stream().filter(e -> e.isbn().equals(isbn)).map(Espera::socioId).toList();
    }

    private void validar(Socio s) { s.validarPuedePedir(prestamosDe(s.id()).size()); }

    private Socio socio(String id) {
        return Optional.ofNullable(socios.get(id)).orElseThrow(() -> new ReglaViolada("Socio inexistente: " + id));
    }

    private Libro libro(String isbn) {
        return Optional.ofNullable(libros.get(isbn)).orElseThrow(() -> new ReglaViolada("Libro inexistente: " + isbn));
    }

    private void guardar() {
        almacen.guardar(new Estado(List.copyOf(libros.values()), List.copyOf(socios.values()), List.copyOf(prestamos), List.copyOf(esperas)));
    }
}
