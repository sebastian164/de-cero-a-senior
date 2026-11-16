package jvm;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analizador mínimo de volcados HPROF (formato "JAVA PROFILE 1.0.2" de HotSpot).
 * Construye el grafo de objetos y, para cada campo estático, calcula el tamaño de todo lo
 * alcanzable desde él. El campo estático con más memoria alcanzable es el candidato a raíz de la fuga.
 * (MAT calcula el tamaño retenido exacto con el árbol de dominadores; para una fuga dominada
 * por una sola raíz, ambos coinciden.)
 */
public class AnalizadorHprof {

    public record CampoEstatico(String clase, String campo, long bytesAlcanzables, long objetos) { }
    public record Histograma(String clase, long instancias, long bytes) { }
    public record Informe(List<CampoEstatico> raices, List<Histograma> histograma) { }

    private int tamId;
    private final Map<Long, String> cadenas = new HashMap<>();
    private final Map<Long, String> nombreClase = new HashMap<>();             // id del objeto Class -> nombre
    private final Map<Long, long[]> refs = new HashMap<>();                    // objeto -> referencias salientes
    private final Map<Long, Long> tamano = new HashMap<>();                    // objeto -> tamaño superficial aprox.
    private final Map<Long, Long> claseDe = new HashMap<>();                   // objeto -> id de su clase
    private final Map<Long, Long> superDe = new HashMap<>();
    private final Map<Long, List<Byte>> camposInstancia = new HashMap<>();     // clase -> tipos de sus campos propios
    private final List<long[]> estaticos = new ArrayList<>();                  // {clase, nombreCampo, objeto}
    private final Map<Long, String> nombreArregloPrimitivo = Map.of(4L, "boolean[]", 5L, "char[]", 6L, "float[]",
            7L, "double[]", 8L, "byte[]", 9L, "short[]", 10L, "int[]", 11L, "long[]");

    public static Informe analizar(Path archivo) throws IOException {
        AnalizadorHprof a = new AnalizadorHprof();
        a.leer(archivo);
        return a.informe();
    }

    private long id(DataInputStream in) throws IOException { return tamId == 4 ? in.readInt() & 0xffffffffL : in.readLong(); }

    private int tamanoTipo(int tipo) {
        return switch (tipo) {
            case 2 -> tamId;
            case 4, 8 -> 1;
            case 5, 9 -> 2;
            case 6, 10 -> 4;
            case 7, 11 -> 8;
            default -> throw new IllegalStateException("tipo " + tipo);
        };
    }

    private void leer(Path archivo) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(new FileInputStream(archivo.toFile()), 1 << 20))) {
            while (in.readByte() != 0) { }                     // "JAVA PROFILE 1.0.2\0"
            tamId = in.readInt();
            in.readLong();
            while (true) {
                int tag;
                try { tag = in.readUnsignedByte(); } catch (EOFException fin) { break; }
                in.readInt();
                long largo = in.readInt() & 0xffffffffL;
                switch (tag) {
                    case 0x01 -> {
                        long sid = id(in);
                        byte[] utf = new byte[(int) (largo - tamId)];
                        in.readFully(utf);
                        cadenas.put(sid, new String(utf, java.nio.charset.StandardCharsets.UTF_8));
                    }
                    case 0x02 -> {
                        in.readInt();
                        long claseId = id(in);
                        in.readInt();
                        nombreClase.put(claseId, cadenas.getOrDefault(id(in), "?").replace('/', '.'));
                    }
                    case 0x0C, 0x1C -> leerVolcado(in, largo);
                    default -> in.skipNBytes(largo);
                }
            }
        }
    }

    private void leerVolcado(DataInputStream in, long largo) throws IOException {
        long leido = 0;
        while (leido < largo) {
            int sub = in.readUnsignedByte();
            leido += 1 + subRegistro(sub, in);
        }
    }

    /** Devuelve los bytes consumidos después de la etiqueta. */
    private long subRegistro(int sub, DataInputStream in) throws IOException {
        switch (sub) {
            case 0xFF, 0x05, 0x07 -> { id(in); return tamId; }
            case 0x01 -> { id(in); id(in); return 2L * tamId; }
            case 0x02, 0x03, 0x08 -> { id(in); in.readInt(); in.readInt(); return tamId + 8; }
            case 0x04, 0x06 -> { id(in); in.readInt(); return tamId + 4; }
            case 0x20 -> { return claseDump(in); }
            case 0x21 -> {
                long obj = id(in); in.readInt(); long clase = id(in);
                int n = in.readInt();
                byte[] datos = new byte[n];
                in.readFully(datos);
                claseDe.put(obj, clase);
                tamano.put(obj, (long) n + 16);
                instanciaPendiente.put(obj, datos);
                return 2L * tamId + 8 + n;
            }
            case 0x22 -> {
                long obj = id(in); in.readInt(); int n = in.readInt(); long clase = id(in);
                long[] r = new long[n];
                for (int i = 0; i < n; i++) r[i] = id(in);
                refs.put(obj, r);
                claseDe.put(obj, clase);
                tamano.put(obj, 16L + (long) n * tamId);
                return 2L * tamId + 8 + (long) n * tamId;
            }
            case 0x23 -> {
                long obj = id(in); in.readInt(); int n = in.readInt(); int tipo = in.readUnsignedByte();
                long bytes = (long) n * tamanoTipo(tipo);
                in.skipNBytes(bytes);
                claseDe.put(obj, (long) -tipo);                    // clase sintética por tipo primitivo
                tamano.put(obj, 16 + bytes);
                return tamId + 9 + bytes;
            }
            default -> throw new IllegalStateException("subregistro desconocido 0x" + Integer.toHexString(sub));
        }
    }

    private final Map<Long, byte[]> instanciaPendiente = new HashMap<>();

    private long claseDump(DataInputStream in) throws IOException {
        long consumido = 0;
        long clase = id(in); in.readInt(); long sup = id(in);
        for (int i = 0; i < 5; i++) id(in);
        in.readInt();
        consumido += 7L * tamId + 8;
        superDe.put(clase, sup);
        int constantes = in.readUnsignedShort(); consumido += 2;
        for (int i = 0; i < constantes; i++) {
            in.readUnsignedShort(); int tipo = in.readUnsignedByte();
            in.skipNBytes(tamanoTipo(tipo)); consumido += 3 + tamanoTipo(tipo);
        }
        int estaticosN = in.readUnsignedShort(); consumido += 2;
        for (int i = 0; i < estaticosN; i++) {
            long nombre = id(in); int tipo = in.readUnsignedByte();
            consumido += tamId + 1 + tamanoTipo(tipo);
            if (tipo == 2) {
                long valor = id(in);
                if (valor != 0) estaticos.add(new long[]{clase, nombre, valor});
            } else in.skipNBytes(tamanoTipo(tipo));
        }
        int campos = in.readUnsignedShort(); consumido += 2;
        List<Byte> tipos = new ArrayList<>();
        for (int i = 0; i < campos; i++) { id(in); tipos.add((byte) in.readUnsignedByte()); consumido += tamId + 1; }
        camposInstancia.put(clase, tipos);
        return consumido;
    }

    /** Resuelve las referencias de las instancias con la disposición de campos de su clase y superclases. */
    private void resolverInstancias() {
        for (var e : instanciaPendiente.entrySet()) {
            var buffer = java.nio.ByteBuffer.wrap(e.getValue());
            List<Long> salientes = new ArrayList<>();
            for (long c = claseDe.get(e.getKey()); c != 0 && camposInstancia.containsKey(c); c = superDe.getOrDefault(c, 0L)) {
                for (byte tipo : camposInstancia.get(c)) {
                    if (tipo == 2) {
                        long r = tamId == 4 ? buffer.getInt() & 0xffffffffL : buffer.getLong();
                        if (r != 0) salientes.add(r);
                    } else buffer.position(buffer.position() + tamanoTipo(tipo));
                }
            }
            refs.put(e.getKey(), salientes.stream().mapToLong(Long::longValue).toArray());
        }
        instanciaPendiente.clear();
    }

    private String nombreDe(long claseId) {
        if (claseId < 0) return nombreArregloPrimitivo.getOrDefault(-claseId, "?[]");
        return nombreClase.getOrDefault(claseId, "?");
    }

    private Informe informe() {
        resolverInstancias();
        List<CampoEstatico> raices = new ArrayList<>();
        for (long[] est : estaticos) {
            Set<Long> vistos = new HashSet<>();
            var pendientes = new ArrayDeque<Long>();
            pendientes.add(est[2]);
            long bytes = 0;
            while (!pendientes.isEmpty()) {
                long o = pendientes.poll();
                if (!vistos.add(o) || !tamano.containsKey(o)) continue;
                bytes += tamano.get(o);
                for (long r : refs.getOrDefault(o, new long[0])) if (!vistos.contains(r)) pendientes.add(r);
            }
            raices.add(new CampoEstatico(nombreDe(est[0]), cadenas.getOrDefault(est[1], "?"), bytes, vistos.size()));
        }
        raices.sort((a, b) -> Long.compare(b.bytesAlcanzables(), a.bytesAlcanzables()));
        Map<String, long[]> porClase = new HashMap<>();
        tamano.forEach((obj, t) -> {
            long[] acc = porClase.computeIfAbsent(nombreDe(claseDe.get(obj)), k -> new long[2]);
            acc[0]++; acc[1] += t;
        });
        List<Histograma> histo = new ArrayList<>();
        porClase.forEach((k, v) -> histo.add(new Histograma(k, v[0], v[1])));
        histo.sort((a, b) -> Long.compare(b.bytes(), a.bytes()));
        return new Informe(raices.subList(0, Math.min(5, raices.size())), histo.subList(0, Math.min(5, histo.size())));
    }

    public static void main(String[] args) throws IOException {
        Informe informe = analizar(Path.of(args[0]));
        System.out.println("Clases que más ocupan:");
        informe.histograma().forEach(h -> System.out.printf("  %-45s %,10d instancias %,14d bytes%n", h.clase(), h.instancias(), h.bytes()));
        System.out.println("Campos estáticos con más memoria alcanzable:");
        informe.raices().forEach(r -> System.out.printf("  %s.%s  %,d bytes en %,d objetos%n", r.clase(), r.campo(), r.bytesAlcanzables(), r.objetos()));
    }
}
