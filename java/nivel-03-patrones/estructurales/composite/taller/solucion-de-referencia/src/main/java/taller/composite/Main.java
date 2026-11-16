package taller.composite;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Area empresa = new Area("Empresa");
        Area tecnologia = new Area("Tecnología");
        Area desarrollo = new Area("Desarrollo");
        empresa.agregar(new Empleado("Gerente", 12_000_000));
        empresa.agregar(tecnologia);
        tecnologia.agregar(new Empleado("CTO", 15_000_000));
        tecnologia.agregar(desarrollo);
        desarrollo.agregar(new Empleado("Ana", 7_000_000));
        desarrollo.agregar(new Empleado("Luis", 5_500_000));

        System.out.println("Nómina empresa: $" + empresa.costoMensual() + ", personas: " + empresa.personas());
        System.out.println("Nómina desarrollo: $" + desarrollo.costoMensual() + ", personas: " + desarrollo.personas());
        System.out.println("Ganan más de 6M: " + empresa.queGananMasDe(6_000_000));
        try {
            desarrollo.agregar(empresa);
        } catch (IllegalArgumentException e) {
            System.out.println("Rechazado: " + e.getMessage());
        }

        // Paso 4: la versión sealed
        Organigrama.Nodo arbol = Organigrama.convertir(empresa);
        System.out.println("Sealed -> nómina: $" + Organigrama.costo(arbol) + ", personas: " + Organigrama.personas(arbol)
                + ", más de 6M: " + Organigrama.queGananMasDe(arbol, 6_000_000));
    }
}

/** Componente común. */
interface Unidad {
    String nombre();
    long costoMensual();
    int personas();
    /** Paso 3: operación nueva. Obligó a tocar la interfaz y las dos clases. */
    List<String> queGananMasDe(long umbral);
}

final class Empleado implements Unidad {
    private final String nombre;
    private final long salario;
    Empleado(String nombre, long salario) { this.nombre = nombre; this.salario = salario; }
    @Override public String nombre() { return nombre; }
    long salario() { return salario; }
    @Override public long costoMensual() { return salario; }
    @Override public int personas() { return 1; }
    @Override public List<String> queGananMasDe(long umbral) { return salario > umbral ? List.of(nombre) : List.of(); }
}

final class Area implements Unidad {
    private final String nombre;
    private final List<Unidad> miembros = new ArrayList<>();
    Area(String nombre) { this.nombre = nombre; }

    /** agregar solo existe en el contenedor (seguridad sobre uniformidad). */
    void agregar(Unidad unidad) {
        if (unidad instanceof Area otra && (otra == this || otra.contiene(this))) {
            throw new IllegalArgumentException("Ciclo: '" + otra.nombre + "' ya contiene a '" + nombre + "'");
        }
        miembros.add(unidad);
    }

    /** ¿Esta área contiene a 'buscada' en algún nivel? */
    boolean contiene(Area buscada) {
        for (Unidad u : miembros) {
            if (u == buscada) return true;
            if (u instanceof Area a && a.contiene(buscada)) return true;
        }
        return false;
    }

    List<Unidad> miembros() { return List.copyOf(miembros); }
    @Override public String nombre() { return nombre; }
    @Override public long costoMensual() { return miembros.stream().mapToLong(Unidad::costoMensual).sum(); }
    @Override public int personas() { return miembros.stream().mapToInt(Unidad::personas).sum(); }
    @Override public List<String> queGananMasDe(long umbral) {
        List<String> r = new ArrayList<>();
        miembros.forEach(m -> r.addAll(m.queGananMasDe(umbral)));
        return r;
    }
}

/** Paso 4: la misma estructura con sealed + switch. Las operaciones viven fuera de los nodos. */
final class Organigrama {
    sealed interface Nodo permits Persona, Grupo { }
    record Persona(String nombre, long salario) implements Nodo { }
    record Grupo(String nombre, List<Nodo> miembros) implements Nodo {
        Grupo { miembros = List.copyOf(miembros); }
    }

    private Organigrama() { }

    static long costo(Nodo n) {
        return switch (n) {
            case Persona p -> p.salario();
            case Grupo g -> g.miembros().stream().mapToLong(Organigrama::costo).sum();
        };
    }

    static int personas(Nodo n) {
        return switch (n) {
            case Persona p -> 1;
            case Grupo g -> g.miembros().stream().mapToInt(Organigrama::personas).sum();
        };
    }

    /** Operación nueva: una función nueva; ningún nodo cambia. */
    static List<String> queGananMasDe(Nodo n, long umbral) {
        return switch (n) {
            case Persona p -> p.salario() > umbral ? List.of(p.nombre()) : List.of();
            case Grupo g -> g.miembros().stream().flatMap(m -> queGananMasDe(m, umbral).stream()).toList();
        };
    }

    static Nodo convertir(Unidad u) {
        return switch (u) {
            case Empleado e -> new Persona(e.nombre(), e.salario());
            case Area a -> new Grupo(a.nombre(), a.miembros().stream().map(Organigrama::convertir).toList());
            default -> throw new IllegalArgumentException(u.getClass().getName());
        };
    }
}
