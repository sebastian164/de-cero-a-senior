package taller.factorymethod;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) {
        FabricaLectores fabrica = FabricaLectores.conFormatosEstandar();
        String csv = "2025-05-01;Nómina;3500000\n2025-05-03;Arriendo;-1200000";
        String xml = "<extracto><mov fecha=\"2025-05-02\" desc=\"Mercado\" valor=\"-250000\"/></extracto>";
        String fijo = String.format("%s%-22s%s", "20250504", "Transferencia recibida", "+0000150000");
        String json = "[{\"fecha\":\"2025-05-05\",\"desc\":\"Intereses\",\"valor\":1200}]";

        for (var e : Map.of("mayo.csv", csv, "mayo.xml", xml, "mayo.txt", fijo, "mayo.json", json).entrySet()) {
            System.out.println(e.getKey() + " -> " + fabrica.paraArchivo(e.getKey()).leer(e.getValue()));
        }
        try {
            fabrica.paraArchivo("mayo.pdf");
        } catch (FormatoNoSoportadoException ex) {
            System.out.println("Error: " + ex.getMessage());
        }
    }
}

/** Valor del dominio con métodos de fábrica con nombre (paso 4). */
record Movimiento(LocalDate fecha, String descripcion, long valor) {

    static Movimiento ingreso(LocalDate fecha, String descripcion, long monto) {
        if (monto < 0) throw new IllegalArgumentException("Un ingreso no puede ser negativo");
        return new Movimiento(fecha, descripcion, monto);
    }

    static Movimiento egreso(LocalDate fecha, String descripcion, long monto) {
        if (monto < 0) throw new IllegalArgumentException("Pasa el monto en positivo; egreso() pone el signo");
        return new Movimiento(fecha, descripcion, -monto);
    }

    /** Usado por los lectores: decide con el signo. */
    static Movimiento conSigno(LocalDate fecha, String descripcion, long valor) {
        return valor >= 0 ? ingreso(fecha, descripcion, valor) : egreso(fecha, descripcion, -valor);
    }

    boolean esIngreso() { return valor >= 0; }
}

/** Paso 1: el contrato. */
interface LectorExtracto {
    List<Movimiento> leer(String contenido);
}

final class LectorCsv implements LectorExtracto {
    @Override public List<Movimiento> leer(String contenido) {
        List<Movimiento> movs = new ArrayList<>();
        for (String linea : contenido.split("\n")) {
            if (linea.isBlank()) continue;
            String[] p = linea.split(";");
            movs.add(Movimiento.conSigno(LocalDate.parse(p[0]), p[1], Long.parseLong(p[2])));
        }
        return movs;
    }
}

final class LectorXml implements LectorExtracto {
    private static final Pattern MOV = Pattern.compile(
            "<mov fecha=\"([^\"]+)\" desc=\"([^\"]+)\" valor=\"(-?\\d+)\"/>");
    @Override public List<Movimiento> leer(String contenido) {
        List<Movimiento> movs = new ArrayList<>();
        Matcher m = MOV.matcher(contenido);
        while (m.find()) {
            movs.add(Movimiento.conSigno(LocalDate.parse(m.group(1)), m.group(2), Long.parseLong(m.group(3))));
        }
        return movs;
    }
}

/** Ancho fijo: fecha AAAAMMDD (8), descripción (22), signo (1), valor (10). */
final class LectorAnchoFijo implements LectorExtracto {
    @Override public List<Movimiento> leer(String contenido) {
        List<Movimiento> movs = new ArrayList<>();
        for (String linea : contenido.split("\n")) {
            if (linea.isBlank()) continue;
            String f = linea.substring(0, 8);
            LocalDate fecha = LocalDate.of(Integer.parseInt(f.substring(0, 4)),
                    Integer.parseInt(f.substring(4, 6)), Integer.parseInt(f.substring(6, 8)));
            String desc = linea.substring(8, 30).strip();
            long valor = Long.parseLong(linea.substring(31, 41));
            movs.add(linea.charAt(30) == '-' ? Movimiento.egreso(fecha, desc, valor) : Movimiento.ingreso(fecha, desc, valor));
        }
        return movs;
    }
}

/** Paso 3: el cuarto formato. Se registró sin modificar la fábrica. */
final class LectorJson implements LectorExtracto {
    private static final Pattern OBJ = Pattern.compile(
            "\\{\"fecha\":\"([^\"]+)\",\"desc\":\"([^\"]+)\",\"valor\":(-?\\d+)}");
    @Override public List<Movimiento> leer(String contenido) {
        List<Movimiento> movs = new ArrayList<>();
        Matcher m = OBJ.matcher(contenido);
        while (m.find()) {
            movs.add(Movimiento.conSigno(LocalDate.parse(m.group(1)), m.group(2), Long.parseLong(m.group(3))));
        }
        return movs;
    }
}

final class FormatoNoSoportadoException extends RuntimeException {
    FormatoNoSoportadoException(String archivo, Set<String> soportados) {
        super("No hay lector para '" + archivo + "'. Extensiones soportadas: " + soportados);
    }
}

/**
 * Paso 2 y 3: fábrica por registro. Agregar un formato es llamar a registrar(...):
 * la clase no cambia.
 */
final class FabricaLectores {

    private final Map<String, Supplier<LectorExtracto>> registro = new TreeMap<>();

    FabricaLectores registrar(String extension, Supplier<LectorExtracto> creador) {
        registro.put(extension.toLowerCase(), creador);
        return this;
    }

    static FabricaLectores conFormatosEstandar() {
        return new FabricaLectores()
                .registrar("csv", LectorCsv::new)
                .registrar("xml", LectorXml::new)
                .registrar("txt", LectorAnchoFijo::new)
                .registrar("json", LectorJson::new);
    }

    /** Extensión desconocida: falla rápido y con un mensaje que dice qué sí se soporta. */
    LectorExtracto paraArchivo(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        String ext = punto < 0 ? "" : nombreArchivo.substring(punto + 1).toLowerCase();
        Supplier<LectorExtracto> creador = registro.get(ext);
        if (creador == null) {
            throw new FormatoNoSoportadoException(nombreArchivo, registro.keySet());
        }
        return creador.get();
    }
}
