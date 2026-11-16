package taller.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class Main {

    public static void main(String[] args) {
        List<String> metricas = new ArrayList<>();
        Soporte soporte = Soporte.estandar(metricas::add, System::nanoTime);
        List<Tiquete> tiquetes = List.of(
                new Tiquete("GANA DINERO FACIL haz clic", "OTRO", Prioridad.BAJA, false),
                new Tiquete("¿Cómo cambio mi contraseña?", "CUENTA", Prioridad.MEDIA, false),
                new Tiquete("La factura salió con error", "FACTURACION", Prioridad.MEDIA, false),
                new Tiquete("El sistema está caído", "PLATAFORMA", Prioridad.CRITICA, false),
                new Tiquete("Necesito un reporte", "OTRO", Prioridad.BAJA, true),
                new Tiquete("Consulta general", "SIN_CATEGORIA", Prioridad.BAJA, false));
        for (Tiquete t : tiquetes) {
            System.out.println(t.asunto() + " -> " + soporte.atender(t));
        }
        System.out.println("Métricas: " + metricas.size() + " mediciones, p.ej. " + metricas.get(0));

        System.out.println("Reordenado (FAQ antes que spam): " + Soporte.faqAntesQueSpam()
                .atender(new Tiquete("¿Cómo cambio mi contraseña? GANA DINERO FACIL", "CUENTA", Prioridad.BAJA, false)));
    }
}

enum Prioridad { BAJA, MEDIA, ALTA, CRITICA }

record Tiquete(String asunto, String categoria, Prioridad prioridad, boolean clienteCorporativo) { }

/** Resultado de procesar un tiquete. */
record Resolucion(String quien, String detalle) {
    @Override public String toString() { return quien + ": " + detalle; }
}

/**
 * Se eligió la variante "tubería" (como los filtros de Servlet/Spring Security): cada eslabón recibe la
 * cadena y decide si la continúa. Sirve para ambos tipos de eslabón:
 *  - los que cortan (spam, FAQ, niveles) devuelven sin llamar a continuar;
 *  - los que envuelven (métricas) llaman a continuar y hacen algo antes y después.
 * La variante clásica (el primero que puede atiende) no permite el eslabón de métricas.
 */
interface Eslabon {
    Resolucion manejar(Tiquete tiquete, Cadena cadena);
}

interface Cadena {
    Resolucion continuar(Tiquete tiquete);   // equivale a chain.doFilter(request, response)
}

final class Soporte {
    private final List<Eslabon> eslabones;
    Soporte(List<Eslabon> eslabones) { this.eslabones = List.copyOf(eslabones); }

    static Soporte estandar(Consumer<String> metricas, LongSupplier reloj) {
        return new Soporte(List.of(new Metricas(metricas, reloj), new DetectorSpam(), new RespuestasFrecuentes(),
                new EscalarNivel2(), new EscalarNivel1(), new SinAtender()));
    }

    /** Paso 3: dos eslabones intercambiados. */
    static Soporte faqAntesQueSpam() {
        return new Soporte(List.of(new RespuestasFrecuentes(), new DetectorSpam(), new EscalarNivel2(),
                new EscalarNivel1(), new SinAtender()));
    }

    static Soporte nivel1AntesQueNivel2() {
        return new Soporte(List.of(new DetectorSpam(), new RespuestasFrecuentes(), new EscalarNivel1(),
                new EscalarNivel2(), new SinAtender()));
    }

    Resolucion atender(Tiquete tiquete) { return desde(0).continuar(tiquete); }

    private Cadena desde(int i) {
        if (i == eslabones.size()) {
            // Nunca debería llegar aquí si el último es SinAtender; protege contra cadenas mal armadas.
            return t -> { throw new IllegalStateException("La cadena terminó sin eslabón final"); };
        }
        return t -> eslabones.get(i).manejar(t, desde(i + 1));
    }
}

final class DetectorSpam implements Eslabon {
    @Override public Resolucion manejar(Tiquete t, Cadena c) {
        if (t.asunto().toUpperCase().contains("GANA DINERO")) return new Resolucion("Spam", "descartado");
        return c.continuar(t);
    }
}

final class RespuestasFrecuentes implements Eslabon {
    private final Map<String, String> faq = Map.of("contraseña", "Enviado artículo 'Cambiar contraseña'");
    @Override public Resolucion manejar(Tiquete t, Cadena c) {
        for (var e : faq.entrySet()) {
            if (t.asunto().toLowerCase().contains(e.getKey())) return new Resolucion("FAQ", e.getValue());
        }
        return c.continuar(t);
    }
}

final class EscalarNivel2 implements Eslabon {
    @Override public Resolucion manejar(Tiquete t, Cadena c) {
        if (t.prioridad() == Prioridad.CRITICA || t.clienteCorporativo()) return new Resolucion("Nivel 2", "asignado");
        return c.continuar(t);
    }
}

final class EscalarNivel1 implements Eslabon {
    @Override public Resolucion manejar(Tiquete t, Cadena c) {
        if (!t.categoria().equals("SIN_CATEGORIA")) return new Resolucion("Nivel 1", "asignado a la cola " + t.categoria());
        return c.continuar(t);
    }
}

/** Paso 2: eslabón final explícito. Ningún tiquete se pierde en silencio. */
final class SinAtender implements Eslabon {
    @Override public Resolucion manejar(Tiquete t, Cadena c) {
        return new Resolucion("Triage manual", "nadie lo atendió automáticamente");
    }
}

/** Paso 4: mide cuánto tarda el resto de la cadena. */
final class Metricas implements Eslabon {
    private final Consumer<String> salida;
    private final LongSupplier reloj;
    Metricas(Consumer<String> salida, LongSupplier reloj) { this.salida = salida; this.reloj = reloj; }
    @Override public Resolucion manejar(Tiquete t, Cadena c) {
        long inicio = reloj.getAsLong();
        Resolucion r = c.continuar(t);
        salida.accept(r.quien() + " en " + (reloj.getAsLong() - inicio) + " ns");
        return r;
    }
}
