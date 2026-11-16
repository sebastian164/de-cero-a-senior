import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Módulo de facturación de una ferretería, anonimizado. Es el "código heredado" que usan
 * los talleres 01 (nombres), 03 (code smells) y 05 (DRY, KISS, YAGNI). No se corrige aquí:
 * cada taller lo transforma en su carpeta y comprueba que la salida de main no cambie.
 */
public class Main {

    public static void main(String[] args) {
        FacturaUtil u = new FacturaUtil();
        List<String[]> lst = new ArrayList<>();
        lst.add(new String[] {"Martillo", "2", "25000"});
        lst.add(new String[] {"Taladro", "1", "180000"});
        lst.add(new String[] {"Tornillos", "0", "300"});
        System.out.println(u.proc("Ana Ruiz", "ana@correo.example", "CO", 1, lst, true, false));

        List<String[]> lst2 = new ArrayList<>();
        lst2.add(new String[] {"Cemento", "12", "32000"});
        System.out.println(u.proc("Obras SAS", "compras@obras.example", "MX", 2, lst2, true, true));

        List<String[]> lst3 = new ArrayList<>();
        lst3.add(new String[] {"Brocha", "3", "9000"});
        System.out.println(u.proc("Luis Gil", "luis@correo.example", "CO", 3, lst3, true, false));
        System.out.println(u.proc("Marta", "sin-arroba", "CO", 1, lst3, false, false));
        System.out.println(u.proc("Pedro", "pedro@correo.example", "PE", 2, lst3, false, false));

        System.out.println(u.rep());
        System.out.println(u.comision("CO"));
        System.out.println(u.totCli("Luis Gil"));
        System.out.println(u.anular("F-3"));
        System.out.println(u.anular("F-9"));
        System.out.println(u.rep());
        ClienteDatos cd = new ClienteDatos("Ana Ruiz", new Dir("Cra 10 # 20-30", new Ciudad("Tunja", "150001")), 7);
        System.out.println(u.etiqueta(cd));
        System.out.println(u.pts(cd, 1_250_000));
        System.out.println(new FabricaExportadores().crear("csv").exportar(u.datos()));
    }
}

/** Utilidades de facturación */
class FacturaUtil {

    // lista de facturas
    private final List<String> l = new ArrayList<>();
    private int n = 0; // contador
    private long tmp;

    // procesa la factura
    // t: tipo de cliente 1=normal 2=mayorista 3=empleado
    String proc(String nom, String mail, String p, int t, List<String[]> lst, boolean env, boolean reg) {
        if (nom == null || nom.isBlank() || nom.length() > 60) {
            return "ERROR: nombre";
        }
        if (mail == null || !mail.contains("@")) {
            return "ERROR: correo " + mail;
        }
        long s = 0;
        int c = 0;
        for (String[] x : lst) {
            int q = Integer.parseInt(x[1]);
            long v = Long.parseLong(x[2]);
            // solo líneas con cantidad y precio
            if (q > 0 && v > 0) {
                s = s + q * v;
                c = c + q;
            }
        }
        // descuento
        long d = 0;
        switch (t) {
            case 1:
                d = 0;
                break;
            case 2:
                if (c >= 10) {
                    d = s * 10 / 100;
                } else {
                    d = s * 5 / 100;
                }
                break;
            case 3:
                d = s * 20 / 100;
                break;
            default:
                break;
        }
        tmp = s - d;
        // iva
        long i;
        if (p.equals("CO")) {
            i = tmp * 19 / 100;
        } else if (p.equals("MX")) {
            i = tmp * 16 / 100;
        } else {
            i = 0;
        }
        // envio
        long e = 0;
        if (env) {
            if (tmp < 150000) {
                e = 8000;
            }
        }
        long total = tmp + i + e;
        n++;
        String num = "F-" + n;
        String r = num + " " + nom + " <" + mail + "> subtotal " + s + " desc " + d + " iva " + i
                + " envio " + e + " total " + total;
        if (reg) {
            r = r + " [regalo]";
        }
        l.add(num + ";" + nom + ";" + tmp + ";" + p);
        return r;
    }

    // reporte por pais: total con iva
    String rep() {
        Map<String, Long> m = l.stream().collect(Collectors.groupingBy(x -> x.split(";")[3],
                TreeMap::new, Collectors.summingLong(x -> {
                    String[] f = x.split(";");
                    long total = Long.parseLong(f[2]);
                    return total + calcular(total, true, false, f[3]);
                })));
        StringBuilder sb = new StringBuilder("Reporte:");
        for (Map.Entry<String, Long> en : m.entrySet()) {
            sb.append(" ").append(en.getKey()).append("=").append(en.getValue());
        }
        return sb.toString();
    }

    // comision del vendedor sobre las ventas sin iva de un pais
    String comision(String p) {
        long s = 0;
        for (String x : l) {
            String[] f = x.split(";");
            if (f[3].equals(p)) {
                s += Long.parseLong(f[2]);
            }
        }
        return "Comision " + p + ": " + calcular(s, false, true, p);
    }

    // calcula iva o comision
    long calcular(long b, boolean esIva, boolean esCom, String p) {
        long pct = esIva ? (p.equals("CO") ? 19 : p.equals("MX") ? 16 : 0) : (esCom ? 3 : 0);
        return b * pct / 100;
    }

    String etiqueta(ClienteDatos cd) {
        if (cd.getNombre() == null || cd.getNombre().isBlank() || cd.getNombre().length() > 60) {
            return "ERROR: nombre";
        }
        return cd.getNombre().toUpperCase() + "\n" + cd.getDir().getCalle() + "\n"
                + cd.getDir().getCiudad().getNombre() + " " + cd.getDir().getCiudad().getCp();
    }

    // puntos de fidelidad
    String pts(ClienteDatos cd, long compras) {
        long pts = compras / 10000;
        if (cd.getAnios() > 5) {
            pts = pts * 2;
        }
        return cd.getNombre() + " tiene " + pts + " puntos";
    }

    List<String> datos() {
        return l;
    }

    // anula una factura
    String anular(String num) {
        for (int k = 0; k < l.size(); k++) {
            String[] f = l.get(k).split(";");
            if (f[0].equals(num)) {
                l.remove(k);
                return "Anulada " + num + " por " + f[2];
            }
        }
        return "No existe " + num;
    }

    // total facturado a un cliente, sin iva
    long totCli(String comprador) {
        long s = 0;
        for (String x : l) {
            String[] f = x.split(";");
            if (f[1].equals(comprador)) {
                s += Long.parseLong(f[2]);
            }
        }
        return s;
    }

    // ya no se usa
    private long calc2(long a) {
        return a * 19 / 100 + 1;
    }
}

class ClienteDatos {
    private final String nombre;
    private final Dir dir;
    private final int anios;

    ClienteDatos(String nombre, Dir dir, int anios) {
        this.nombre = nombre;
        this.dir = dir;
        this.anios = anios;
    }

    String getNombre() { return nombre; }
    Dir getDir() { return dir; }
    int getAnios() { return anios; }
}

class Dir {
    private final String calle;
    private final Ciudad ciudad;

    Dir(String calle, Ciudad ciudad) {
        this.calle = calle;
        this.ciudad = ciudad;
    }

    String getCalle() { return calle; }
    Ciudad getCiudad() { return ciudad; }
}

class Ciudad {
    private final String nombre;
    private final String cp;

    Ciudad(String nombre, String cp) {
        this.nombre = nombre;
        this.cp = cp;
    }

    String getNombre() { return nombre; }
    String getCp() { return cp; }
}

interface IExportador {
    String exportar(List<String> datos);
}

class ExportadorCsv implements IExportador {
    @Override
    public String exportar(List<String> datos) {
        return String.join("\n", datos);
    }
}

class FabricaExportadores {
    IExportador crear(String tipo) {
        if (tipo.equals("csv")) {
            return new ExportadorCsv();
        }
        throw new IllegalArgumentException("Tipo no soportado: " + tipo);
    }
}
