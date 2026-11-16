import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Taller 01: el mismo módulo de legado/Main.java con nombres nuevos. Solo cambian nombres,
 * constantes con nombre y comentarios reemplazados por nombres; la estructura y el comportamiento
 * son los mismos (la salida se compara con legado/salida-esperada.txt).
 */
public class Main {

    public static void main(String[] args) {
        Facturacion facturacion = new Facturacion();
        List<String[]> lineasDeAna = new ArrayList<>();
        lineasDeAna.add(new String[] {"Martillo", "2", "25000"});
        lineasDeAna.add(new String[] {"Taladro", "1", "180000"});
        lineasDeAna.add(new String[] {"Tornillos", "0", "300"});
        System.out.println(facturacion.facturar("Ana Ruiz", "ana@correo.example", "CO", Facturacion.CLIENTE_DETAL, lineasDeAna, true, false));

        List<String[]> lineasDeObras = new ArrayList<>();
        lineasDeObras.add(new String[] {"Cemento", "12", "32000"});
        System.out.println(facturacion.facturar("Obras SAS", "compras@obras.example", "MX", Facturacion.CLIENTE_MAYORISTA, lineasDeObras, true, true));

        List<String[]> lineasDeBrochas = new ArrayList<>();
        lineasDeBrochas.add(new String[] {"Brocha", "3", "9000"});
        System.out.println(facturacion.facturar("Luis Gil", "luis@correo.example", "CO", Facturacion.CLIENTE_EMPLEADO, lineasDeBrochas, true, false));
        System.out.println(facturacion.facturar("Marta", "sin-arroba", "CO", Facturacion.CLIENTE_DETAL, lineasDeBrochas, false, false));
        System.out.println(facturacion.facturar("Pedro", "pedro@correo.example", "PE", Facturacion.CLIENTE_MAYORISTA, lineasDeBrochas, false, false));

        System.out.println(facturacion.reporteDeVentasConIvaPorPais());
        System.out.println(facturacion.comisionDelVendedor("CO"));
        System.out.println(facturacion.baseGravableFacturadaA("Luis Gil"));
        System.out.println(facturacion.anular("F-3"));
        System.out.println(facturacion.anular("F-9"));
        System.out.println(facturacion.reporteDeVentasConIvaPorPais());
        Cliente ana = new Cliente("Ana Ruiz", new Direccion("Cra 10 # 20-30", new Ciudad("Tunja", "150001")), 7);
        System.out.println(facturacion.etiquetaDeEnvio(ana));
        System.out.println(facturacion.puntosDeFidelidad(ana, 1_250_000));
        System.out.println(new FabricaExportadores().crear("csv").exportar(facturacion.registrosDeFacturas()));
    }
}

class Facturacion {

    static final int CLIENTE_DETAL = 1;
    static final int CLIENTE_MAYORISTA = 2;
    static final int CLIENTE_EMPLEADO = 3;

    private static final int LONGITUD_MAXIMA_DEL_NOMBRE = 60;
    private static final int UNIDADES_PARA_DESCUENTO_POR_VOLUMEN = 10;
    private static final int PORCENTAJE_DESCUENTO_MAYORISTA_POR_VOLUMEN = 10;
    private static final int PORCENTAJE_DESCUENTO_MAYORISTA = 5;
    private static final int PORCENTAJE_DESCUENTO_EMPLEADO = 20;
    private static final int PORCENTAJE_IVA_COLOMBIA = 19;
    private static final int PORCENTAJE_IVA_MEXICO = 16;
    private static final int PORCENTAJE_COMISION_VENDEDOR = 3;
    private static final long COMPRA_MINIMA_PARA_ENVIO_GRATIS = 150_000;
    private static final long COSTO_DE_ENVIO = 8_000;
    private static final long PESOS_POR_PUNTO_DE_FIDELIDAD = 10_000;
    private static final int ANIOS_PARA_PUNTOS_DOBLES = 5;

    private final List<String> registrosDeFacturas = new ArrayList<>();
    private int ultimoNumeroDeFactura = 0;
    private long baseGravable;

    String facturar(String cliente, String correo, String pais, int tipoDeCliente, List<String[]> lineas,
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
        long descuento = 0;
        switch (tipoDeCliente) {
            case CLIENTE_DETAL:
                descuento = 0;
                break;
            case CLIENTE_MAYORISTA:
                if (unidades >= UNIDADES_PARA_DESCUENTO_POR_VOLUMEN) {
                    descuento = subtotal * PORCENTAJE_DESCUENTO_MAYORISTA_POR_VOLUMEN / 100;
                } else {
                    descuento = subtotal * PORCENTAJE_DESCUENTO_MAYORISTA / 100;
                }
                break;
            case CLIENTE_EMPLEADO:
                descuento = subtotal * PORCENTAJE_DESCUENTO_EMPLEADO / 100;
                break;
            default:
                break;
        }
        baseGravable = subtotal - descuento;
        long iva;
        if (pais.equals("CO")) {
            iva = baseGravable * PORCENTAJE_IVA_COLOMBIA / 100;
        } else if (pais.equals("MX")) {
            iva = baseGravable * PORCENTAJE_IVA_MEXICO / 100;
        } else {
            iva = 0;
        }
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
        Map<String, Long> ventasConIvaPorPais = registrosDeFacturas.stream().collect(Collectors.groupingBy(
                registro -> registro.split(";")[3],
                TreeMap::new, Collectors.summingLong(registro -> {
                    String[] campos = registro.split(";");
                    long baseGravableDeLaFactura = Long.parseLong(campos[2]);
                    return baseGravableDeLaFactura + calcular(baseGravableDeLaFactura, true, false, campos[3]);
                })));
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
        return "Comision " + pais + ": " + calcular(ventasSinIva, false, true, pais);
    }

    long calcular(long base, boolean esIva, boolean esComision, String pais) {
        long porcentaje = esIva
                ? (pais.equals("CO") ? PORCENTAJE_IVA_COLOMBIA : pais.equals("MX") ? PORCENTAJE_IVA_MEXICO : 0)
                : (esComision ? PORCENTAJE_COMISION_VENDEDOR : 0);
        return base * porcentaje / 100;
    }

    String etiquetaDeEnvio(Cliente cliente) {
        if (cliente.getNombre() == null || cliente.getNombre().isBlank()
                || cliente.getNombre().length() > LONGITUD_MAXIMA_DEL_NOMBRE) {
            return "ERROR: nombre";
        }
        return cliente.getNombre().toUpperCase() + "\n" + cliente.getDireccion().getCalle() + "\n"
                + cliente.getDireccion().getCiudad().getNombre() + " " + cliente.getDireccion().getCiudad().getCodigoPostal();
    }

    String puntosDeFidelidad(Cliente cliente, long comprasDelAnio) {
        long puntos = comprasDelAnio / PESOS_POR_PUNTO_DE_FIDELIDAD;
        if (cliente.getAniosComoCliente() > ANIOS_PARA_PUNTOS_DOBLES) {
            puntos = puntos * 2;
        }
        return cliente.getNombre() + " tiene " + puntos + " puntos";
    }

    List<String> registrosDeFacturas() {
        return registrosDeFacturas;
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

    private long calc2(long a) {
        return a * 19 / 100 + 1;
    }
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

    String getCalle() { return calle; }
    Ciudad getCiudad() { return ciudad; }
}

class Ciudad {
    private final String nombre;
    private final String codigoPostal;

    Ciudad(String nombre, String codigoPostal) {
        this.nombre = nombre;
        this.codigoPostal = codigoPostal;
    }

    String getNombre() { return nombre; }
    String getCodigoPostal() { return codigoPostal; }
}

interface Exportador {
    String exportar(List<String> registros);
}

class ExportadorCsv implements Exportador {
    @Override
    public String exportar(List<String> registros) {
        return String.join("\n", registros);
    }
}

class FabricaExportadores {
    Exportador crear(String formato) {
        if (formato.equals("csv")) {
            return new ExportadorCsv();
        }
        throw new IllegalArgumentException("Tipo no soportado: " + formato);
    }
}
