import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Taller 05: parte del resultado del taller 03 y aplica DRY (una sola fuente para el IVA),
 * YAGNI (fuera la fábrica y la interfaz de exportadores), separa la función con banderas y
 * reescribe el reporte ingenioso. La salida debe ser idéntica a
 * ../../../01-nombres/taller/solucion-de-referencia/legado/salida-esperada.txt.
 */
public class Main {

    public static void main(String[] args) {
        Facturacion facturacion = new Facturacion();
        List<String[]> lineasDeAna = new ArrayList<>();
        lineasDeAna.add(new String[] {"Martillo", "2", "25000"});
        lineasDeAna.add(new String[] {"Taladro", "1", "180000"});
        lineasDeAna.add(new String[] {"Tornillos", "0", "300"});
        System.out.println(facturacion.facturar("Ana Ruiz", "ana@correo.example", "CO", TipoDeCliente.DETAL, lineasDeAna, true, false));

        List<String[]> lineasDeObras = new ArrayList<>();
        lineasDeObras.add(new String[] {"Cemento", "12", "32000"});
        System.out.println(facturacion.facturar("Obras SAS", "compras@obras.example", "MX", TipoDeCliente.MAYORISTA, lineasDeObras, true, true));

        List<String[]> lineasDeBrochas = new ArrayList<>();
        lineasDeBrochas.add(new String[] {"Brocha", "3", "9000"});
        System.out.println(facturacion.facturar("Luis Gil", "luis@correo.example", "CO", TipoDeCliente.EMPLEADO, lineasDeBrochas, true, false));
        System.out.println(facturacion.facturar("Marta", "sin-arroba", "CO", TipoDeCliente.DETAL, lineasDeBrochas, false, false));
        System.out.println(facturacion.facturar("Pedro", "pedro@correo.example", "PE", TipoDeCliente.MAYORISTA, lineasDeBrochas, false, false));

        System.out.println(facturacion.reporteDeVentasConIvaPorPais());
        System.out.println(facturacion.comisionDelVendedor("CO"));
        System.out.println(facturacion.baseGravableFacturadaA("Luis Gil"));
        System.out.println(facturacion.anular("F-3"));
        System.out.println(facturacion.anular("F-9"));
        System.out.println(facturacion.reporteDeVentasConIvaPorPais());
        Cliente ana = new Cliente("Ana Ruiz", new Direccion("Cra 10 # 20-30", new Ciudad("Tunja", "150001")), 7);
        System.out.println(facturacion.etiquetaDeEnvio(ana));
        System.out.println(facturacion.puntosDeFidelidad(ana, 1_250_000));
        System.out.println(facturacion.exportarCsv());
    }
}

class Facturacion {

    private static final int LONGITUD_MAXIMA_DEL_NOMBRE = 60;
    private static final int PORCENTAJE_COMISION_VENDEDOR = 3;
    private static final long COMPRA_MINIMA_PARA_ENVIO_GRATIS = 150_000;
    private static final long COSTO_DE_ENVIO = 8_000;
    private static final long PESOS_POR_PUNTO_DE_FIDELIDAD = 10_000;
    private static final int ANIOS_PARA_PUNTOS_DOBLES = 5;

    private final List<String> registrosDeFacturas = new ArrayList<>();
    private int ultimoNumeroDeFactura = 0;
    private long baseGravable;

    String facturar(String cliente, String correo, String pais, TipoDeCliente tipoDeCliente, List<String[]> lineas,
                    boolean conEnvio, boolean esRegalo) {
        if (cliente == null || cliente.isBlank() || cliente.length() > LONGITUD_MAXIMA_DEL_NOMBRE) {
            return "ERROR: nombre";
        }
        if (correo == null || !correo.contains("@")) {
            return "ERROR: correo " + correo;
        }
        long subtotal = 0;
        int unidades = 0;
        for (String[] linea : lineas) {
            int cantidad = Integer.parseInt(linea[1]);
            long precioUnitario = Long.parseLong(linea[2]);
            boolean lineaFacturable = cantidad > 0 && precioUnitario > 0;
            if (lineaFacturable) {
                subtotal = subtotal + cantidad * precioUnitario;
                unidades = unidades + cantidad;
            }
        }
        long descuento = tipoDeCliente.descuento(subtotal, unidades);
        baseGravable = subtotal - descuento;
        long iva = Iva.sobre(baseGravable, pais);
        long costoDeEnvio = 0;
        if (conEnvio) {
            if (baseGravable < COMPRA_MINIMA_PARA_ENVIO_GRATIS) {
                costoDeEnvio = COSTO_DE_ENVIO;
            }
        }
        long totalAPagar = baseGravable + iva + costoDeEnvio;
        ultimoNumeroDeFactura++;
        String numeroDeFactura = "F-" + ultimoNumeroDeFactura;
        String factura = numeroDeFactura + " " + cliente + " <" + correo + "> subtotal " + subtotal + " desc " + descuento
                + " iva " + iva + " envio " + costoDeEnvio + " total " + totalAPagar;
        if (esRegalo) {
            factura = factura + " [regalo]";
        }
        registrosDeFacturas.add(numeroDeFactura + ";" + cliente + ";" + baseGravable + ";" + pais);
        return factura;
    }

    String reporteDeVentasConIvaPorPais() {
        Map<String, Long> ventasConIvaPorPais = new TreeMap<>();   // TreeMap: países en orden alfabético
        for (String registro : registrosDeFacturas) {
            String[] campos = registro.split(";");
            String pais = campos[3];
            long baseGravableDeLaFactura = Long.parseLong(campos[2]);
            long ventaConIva = baseGravableDeLaFactura + Iva.sobre(baseGravableDeLaFactura, pais);
            ventasConIvaPorPais.merge(pais, ventaConIva, Long::sum);
        }
        StringBuilder reporte = new StringBuilder("Reporte:");
        for (Map.Entry<String, Long> ventasDeUnPais : ventasConIvaPorPais.entrySet()) {
            reporte.append(" ").append(ventasDeUnPais.getKey()).append("=").append(ventasDeUnPais.getValue());
        }
        return reporte.toString();
    }

    String comisionDelVendedor(String pais) {
        long ventasSinIva = 0;
        for (String registro : registrosDeFacturas) {
            String[] campos = registro.split(";");
            if (campos[3].equals(pais)) {
                ventasSinIva += Long.parseLong(campos[2]);
            }
        }
        return "Comision " + pais + ": " + ventasSinIva * PORCENTAJE_COMISION_VENDEDOR / 100;
    }

    String etiquetaDeEnvio(Cliente cliente) {
        if (cliente.getNombre() == null || cliente.getNombre().isBlank()
                || cliente.getNombre().length() > LONGITUD_MAXIMA_DEL_NOMBRE) {
            return "ERROR: nombre";
        }
        return cliente.getNombre().toUpperCase() + "\n" + cliente.getDireccion().paraEtiqueta();
    }

    String puntosDeFidelidad(Cliente cliente, long comprasDelAnio) {
        long puntos = comprasDelAnio / PESOS_POR_PUNTO_DE_FIDELIDAD;
        if (cliente.getAniosComoCliente() > ANIOS_PARA_PUNTOS_DOBLES) {
            puntos = puntos * 2;
        }
        return cliente.getNombre() + " tiene " + puntos + " puntos";
    }

    String exportarCsv() {
        return String.join("\n", registrosDeFacturas);
    }

    String anular(String numeroDeFactura) {
        for (int posicion = 0; posicion < registrosDeFacturas.size(); posicion++) {
            String[] campos = registrosDeFacturas.get(posicion).split(";");
            if (campos[0].equals(numeroDeFactura)) {
                registrosDeFacturas.remove(posicion);
                return "Anulada " + numeroDeFactura + " por " + campos[2];
            }
        }
        return "No existe " + numeroDeFactura;
    }

    long baseGravableFacturadaA(String cliente) {
        long baseGravableAcumulada = 0;
        for (String registro : registrosDeFacturas) {
            String[] campos = registro.split(";");
            if (campos[1].equals(cliente)) {
                baseGravableAcumulada += Long.parseLong(campos[2]);
            }
        }
        return baseGravableAcumulada;
    }
}

enum TipoDeCliente {

    DETAL {
        @Override
        long descuento(long subtotal, int unidades) {
            return 0;
        }
    },
    MAYORISTA {
        private static final int UNIDADES_PARA_DESCUENTO_POR_VOLUMEN = 10;
        private static final int PORCENTAJE_POR_VOLUMEN = 10;
        private static final int PORCENTAJE = 5;

        @Override
        long descuento(long subtotal, int unidades) {
            int porcentaje = unidades >= UNIDADES_PARA_DESCUENTO_POR_VOLUMEN ? PORCENTAJE_POR_VOLUMEN : PORCENTAJE;
            return subtotal * porcentaje / 100;
        }
    },
    EMPLEADO {
        private static final int PORCENTAJE = 20;

        @Override
        long descuento(long subtotal, int unidades) {
            return subtotal * PORCENTAJE / 100;
        }
    };

    abstract long descuento(long subtotal, int unidades);
}

class Cliente {
    private final String nombre;
    private final Direccion direccion;
    private final int aniosComoCliente;

    Cliente(String nombre, Direccion direccion, int aniosComoCliente) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.aniosComoCliente = aniosComoCliente;
    }

    String getNombre() { return nombre; }
    Direccion getDireccion() { return direccion; }
    int getAniosComoCliente() { return aniosComoCliente; }
}

class Direccion {
    private final String calle;
    private final Ciudad ciudad;

    Direccion(String calle, Ciudad ciudad) {
        this.calle = calle;
        this.ciudad = ciudad;
    }

    String paraEtiqueta() {
        return calle + "\n" + ciudad.paraEtiqueta();
    }
}

class Ciudad {
    private final String nombre;
    private final String codigoPostal;

    Ciudad(String nombre, String codigoPostal) {
        this.nombre = nombre;
        this.codigoPostal = codigoPostal;
    }

    String paraEtiqueta() {
        return nombre + " " + codigoPostal;
    }
}

/** La única fuente de las tarifas de IVA: facturar y el reporte la usan las dos. */
final class Iva {

    private static final int PORCENTAJE_COLOMBIA = 19;
    private static final int PORCENTAJE_MEXICO = 16;

    private Iva() {
    }

    static long sobre(long baseGravable, String pais) {
        return baseGravable * porcentaje(pais) / 100;
    }

    private static int porcentaje(String pais) {
        return switch (pais) {
            case "CO" -> PORCENTAJE_COLOMBIA;
            case "MX" -> PORCENTAJE_MEXICO;
            default -> 0;
        };
    }
}
