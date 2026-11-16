package taller.decorator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        // Orden A: caché por fuera del cifrado -> la caché guarda texto plano.
        EnMemoria discoA = new EnMemoria();
        ConCache cacheA = new ConCache(new ConCifrado(discoA, 3));
        FuenteDatos a = new ConRegistroTiempos(cacheA, System.out::println);
        a.escribir("clave", "secreto");
        a.leer("clave");
        a.leer("clave");
        System.out.println("A: disco=" + discoA.crudo() + " cache=" + cacheA.contenido());

        // Orden B: caché por dentro del cifrado -> la caché guarda texto cifrado.
        EnMemoria discoB = new EnMemoria();
        ConCache cacheB = new ConCache(discoB);
        FuenteDatos b = new ConCifrado(cacheB, 3);
        b.escribir("clave", "secreto");
        System.out.println("B: lee=" + b.leer("clave") + " disco=" + discoB.crudo() + " cache=" + cacheB.contenido());

        b.escribir("clave", "otro");
        System.out.println("B tras reescribir: " + b.leer("clave"));
    }
}

interface FuenteDatos {
    String leer(String clave);
    void escribir(String clave, String valor);
    /** Paso 4: método nuevo. Obligó a tocar las 4 implementaciones. */
    void eliminar(String clave);
}

final class EnMemoria implements FuenteDatos {
    private final Map<String, String> datos = new HashMap<>();
    private int lecturas;
    @Override public String leer(String clave) { lecturas++; return datos.get(clave); }
    @Override public void escribir(String clave, String valor) { datos.put(clave, valor); }
    @Override public void eliminar(String clave) { datos.remove(clave); }
    Map<String, String> crudo() { return Map.copyOf(datos); }
    int lecturas() { return lecturas; }
}

final class ConCache implements FuenteDatos {
    private final FuenteDatos envuelto;
    private final Map<String, String> cache = new HashMap<>();
    ConCache(FuenteDatos envuelto) { this.envuelto = envuelto; }

    @Override public String leer(String clave) {
        String v = cache.get(clave);
        if (v == null) {
            v = envuelto.leer(clave);
            if (v != null) cache.put(clave, v);
        }
        return v;
    }

    /** Paso 3: escribir invalida la entrada (no la actualiza: no sabe qué devolverá el envuelto). */
    @Override public void escribir(String clave, String valor) {
        envuelto.escribir(clave, valor);
        cache.remove(clave);
    }

    @Override public void eliminar(String clave) {
        envuelto.eliminar(clave);
        cache.remove(clave);
    }

    Map<String, String> contenido() { return Map.copyOf(cache); }
}

/** Cifrado César sobre letras a-z (suficiente para el taller). */
final class ConCifrado implements FuenteDatos {
    private final FuenteDatos envuelto;
    private final int desplazamiento;
    ConCifrado(FuenteDatos envuelto, int desplazamiento) { this.envuelto = envuelto; this.desplazamiento = desplazamiento; }

    @Override public String leer(String clave) {
        String v = envuelto.leer(clave);
        return v == null ? null : cesar(v, 26 - desplazamiento);
    }
    @Override public void escribir(String clave, String valor) { envuelto.escribir(clave, cesar(valor, desplazamiento)); }
    @Override public void eliminar(String clave) { envuelto.eliminar(clave); }

    static String cesar(String texto, int d) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            sb.append(c >= 'a' && c <= 'z' ? (char) ('a' + (c - 'a' + d) % 26) : c);
        }
        return sb.toString();
    }
}

final class ConRegistroTiempos implements FuenteDatos {
    interface Bitacora { void registrar(String linea); }

    private final FuenteDatos envuelto;
    private final Bitacora bitacora;
    ConRegistroTiempos(FuenteDatos envuelto, Bitacora bitacora) { this.envuelto = envuelto; this.bitacora = bitacora; }

    @Override public String leer(String clave) {
        long t = System.nanoTime();
        try { return envuelto.leer(clave); }
        finally { bitacora.registrar("leer(" + clave + ") " + (System.nanoTime() - t) / 1000 + " µs"); }
    }
    @Override public void escribir(String clave, String valor) {
        long t = System.nanoTime();
        try { envuelto.escribir(clave, valor); }
        finally { bitacora.registrar("escribir(" + clave + ") " + (System.nanoTime() - t) / 1000 + " µs"); }
    }
    @Override public void eliminar(String clave) {
        long t = System.nanoTime();
        try { envuelto.eliminar(clave); }
        finally { bitacora.registrar("eliminar(" + clave + ") " + (System.nanoTime() - t) / 1000 + " µs"); }
    }
}
