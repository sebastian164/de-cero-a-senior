package taller.adapter;

import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        ServicioClima texto = new AdaptadorClimaTexto(new ProveedorClimaTexto());
        ServicioClima mapa = new AdaptadorClimaMapa(new ProveedorClimaMapa());
        for (ServicioClima clima : new ServicioClima[] {texto, mapa}) {
            Recomendador r = new Recomendador(clima);
            System.out.println(clima.getClass().getSimpleName() + ": Tunja -> " + clima.pronosticoPara("Tunja")
                    + " | " + r.recomendar("Tunja") + " | Paipa -> " + r.recomendar("Paipa")
                    + " | Atlantida -> " + r.recomendar("Atlantida"));
        }

        ProveedorClimaMapa inestable = new ProveedorClimaMapa();
        inestable.fallarProximas(2);
        ServicioClima conReintentos = new ConReintentos(new AdaptadorClimaMapa(inestable), 3);
        System.out.println("Con 2 fallos transitorios y 3 intentos: " + conReintentos.pronosticoPara("Tunja"));
    }
}

// ===================== Lado propio (el puerto) =====================

/** Temperatura en Celsius; probabilidad de lluvia en [0, 1]. */
record Pronostico(double celsius, double probabilidadLluvia) {
    /** Redondeo a un decimal: regla del lado propio que comparten todos los adaptadores. */
    static double redondear(double v) { return Math.round(v * 10) / 10.0; }

    Pronostico {
        if (probabilidadLluvia < 0 || probabilidadLluvia > 1) {
            throw new IllegalArgumentException("Probabilidad fuera de [0,1]: " + probabilidadLluvia);
        }
    }
}

interface ServicioClima {
    /**
     * @throws CiudadNoEncontradaException si el proveedor no conoce la ciudad (excepción NO comprobada del dominio).
     * @throws ClimaNoDisponibleException  si el proveedor falla de forma transitoria.
     */
    Pronostico pronosticoPara(String ciudad);
}

final class CiudadNoEncontradaException extends RuntimeException {
    CiudadNoEncontradaException(String ciudad) { super("Ciudad no encontrada: " + ciudad); }
}

final class ClimaNoDisponibleException extends RuntimeException {
    ClimaNoDisponibleException(String mensaje, Throwable causa) { super(mensaje, causa); }
}

/** Servicio de negocio: no sabe qué proveedor hay detrás. */
final class Recomendador {
    private final ServicioClima clima;
    Recomendador(ServicioClima clima) { this.clima = clima; }

    String recomendar(String ciudad) {
        try {
            return clima.pronosticoPara(ciudad).probabilidadLluvia() >= 0.5 ? "lleva sombrilla" : "no hace falta sombrilla";
        } catch (CiudadNoEncontradaException e) {
            return "sin datos para " + ciudad;
        }
    }
}

// ===================== Proveedores ajenos (no se pueden modificar) =====================

/** Devuelve "TUNJA|53.6F|RAIN:40" o "ERR|404" si no conoce la ciudad. */
final class ProveedorClimaTexto {
    private final Map<String, String> datos = Map.of(
            "TUNJA", "TUNJA|53.6F|RAIN:40",
            "PAIPA", "PAIPA|64.4F|RAIN:75");

    String consultar(String ciudadMayusculas) {
        return datos.getOrDefault(ciudadMayusculas, "ERR|404");
    }
}

/** Excepción comprobada propia del SDK. */
final class CityNotFoundException extends Exception {
    CityNotFoundException(String city) { super("City not found: " + city); }
}

final class ProveedorTemporalmenteCaido extends RuntimeException {
    ProveedorTemporalmenteCaido() { super("HTTP 503"); }
}

/** Devuelve un mapa con temp_k (Kelvin) y pop (porcentaje entero). */
final class ProveedorClimaMapa {
    private int fallosPendientes;
    private int llamadas;

    void fallarProximas(int n) { this.fallosPendientes = n; }
    int llamadas() { return llamadas; }

    Map<String, Object> fetch(String city) throws CityNotFoundException {
        llamadas++;
        if (fallosPendientes > 0) {
            fallosPendientes--;
            throw new ProveedorTemporalmenteCaido();
        }
        Map<String, Object> r = new HashMap<>();
        switch (city.toLowerCase()) {
            case "tunja" -> { r.put("temp_k", 285.15); r.put("pop", 40); r.put("station", "SKTJ"); }
            case "paipa" -> { r.put("temp_k", 291.15); r.put("pop", 75); r.put("station", "SKPA"); }
            default -> throw new CityNotFoundException(city);
        }
        return r;
    }
}

// ===================== Adaptadores =====================

final class AdaptadorClimaTexto implements ServicioClima {
    private final ProveedorClimaTexto proveedor;
    AdaptadorClimaTexto(ProveedorClimaTexto proveedor) { this.proveedor = proveedor; }

    @Override
    public Pronostico pronosticoPara(String ciudad) {
        String respuesta = proveedor.consultar(ciudad.toUpperCase());
        String[] partes = respuesta.split("\\|");
        if (partes[0].equals("ERR")) {
            throw new CiudadNoEncontradaException(ciudad);
        }
        double fahrenheit = Double.parseDouble(partes[1].replace("F", ""));
        int porcentaje = Integer.parseInt(partes[2].substring("RAIN:".length()));
        return new Pronostico(Pronostico.redondear((fahrenheit - 32) * 5 / 9), porcentaje / 100.0);
    }

}

final class AdaptadorClimaMapa implements ServicioClima {
    private final ProveedorClimaMapa proveedor;
    AdaptadorClimaMapa(ProveedorClimaMapa proveedor) { this.proveedor = proveedor; }

    @Override
    public Pronostico pronosticoPara(String ciudad) {
        Map<String, Object> datos;
        try {
            datos = proveedor.fetch(ciudad);
        } catch (CityNotFoundException e) {
            throw new CiudadNoEncontradaException(ciudad);      // la excepción del SDK no escapa
        } catch (ProveedorTemporalmenteCaido e) {
            throw new ClimaNoDisponibleException("Proveedor de clima no disponible", e);
        }
        double kelvin = ((Number) datos.get("temp_k")).doubleValue();
        int pop = ((Number) datos.get("pop")).intValue();
        return new Pronostico(Pronostico.redondear(kelvin - 273.15), pop / 100.0);
    }
}

// ===================== Paso 5: reintentos como decorador, fuera de los adaptadores =====================

final class ConReintentos implements ServicioClima {
    private final ServicioClima envuelto;
    private final int maximo;
    ConReintentos(ServicioClima envuelto, int maximo) { this.envuelto = envuelto; this.maximo = maximo; }

    @Override
    public Pronostico pronosticoPara(String ciudad) {
        for (int intento = 1; ; intento++) {
            try {
                return envuelto.pronosticoPara(ciudad);
            } catch (ClimaNoDisponibleException e) {   // CiudadNoEncontrada NO se reintenta: no es transitoria
                if (intento == maximo) throw e;
            }
        }
    }
}
