package taller.singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("Paso 1, perezoso sin sincronizar: " + Carrera.contarConstrucciones(ConfiguracionPerezosa::getInstancia,
                ConfiguracionPerezosa.CONSTRUCCIONES) + " construcciones");
        System.out.println("Paso 2, synchronized: " + Carrera.contarConstrucciones(ConfiguracionSincronizada::getInstancia,
                ConfiguracionSincronizada.CONSTRUCCIONES));
        System.out.println("Paso 2, holder idiom: " + Carrera.contarConstrucciones(ConfiguracionHolder::getInstancia,
                ConfiguracionHolder.CONSTRUCCIONES));
        System.out.println("Paso 2, enum: " + Carrera.contarConstrucciones(() -> ConfiguracionEnum.INSTANCIA,
                ConfiguracionEnum.CONSTRUCCIONES));

        System.out.println("Paso 3, orden A->B: " + PruebasContaminadas.correr(true));
        RegistroGlobal.INSTANCIA.limpiar();
        System.out.println("Paso 3, solo B:     " + PruebasContaminadas.correr(false));

        System.out.println("Paso 4, inyectado: " + new ServicioAlertas(new Registro()).alertar("disco lleno"));
    }
}

/** Lanza muchos hilos a la vez contra getInstancia y cuenta cuántas construcciones hubo. */
final class Carrera {
    static int contarConstrucciones(Supplier<Object> obtener, AtomicInteger contador) throws InterruptedException {
        int hilos = 32;
        CountDownLatch salida = new CountDownLatch(1);
        List<Thread> lista = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            Thread t = new Thread(() -> {
                try { salida.await(); } catch (InterruptedException e) { return; }
                obtener.get();
            });
            t.start();
            lista.add(t);
        }
        salida.countDown();
        for (Thread t : lista) t.join();
        return contador.get();
    }

    static void construccionLenta() {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

// ----- Paso 1: el "antes" -----
final class ConfiguracionPerezosa {
    static final AtomicInteger CONSTRUCCIONES = new AtomicInteger();
    private static ConfiguracionPerezosa instancia;

    private ConfiguracionPerezosa() {
        CONSTRUCCIONES.incrementAndGet();
        Carrera.construccionLenta();          // leer un archivo, por ejemplo: ensancha la ventana de carrera
    }

    static ConfiguracionPerezosa getInstancia() {
        if (instancia == null) {
            instancia = new ConfiguracionPerezosa();
        }
        return instancia;
    }
}

// ----- Paso 2a: synchronized. Correcto, pero cada llamada toma el candado. -----
final class ConfiguracionSincronizada {
    static final AtomicInteger CONSTRUCCIONES = new AtomicInteger();
    private static ConfiguracionSincronizada instancia;

    private ConfiguracionSincronizada() { CONSTRUCCIONES.incrementAndGet(); Carrera.construccionLenta(); }

    static synchronized ConfiguracionSincronizada getInstancia() {
        if (instancia == null) instancia = new ConfiguracionSincronizada();
        return instancia;
    }
}

// ----- Paso 2b: holder idiom. Perezoso y sin candados: la JVM garantiza la inicialización de clases una sola vez. -----
final class ConfiguracionHolder {
    static final AtomicInteger CONSTRUCCIONES = new AtomicInteger();

    private ConfiguracionHolder() { CONSTRUCCIONES.incrementAndGet(); Carrera.construccionLenta(); }

    private static final class Holder {
        static final ConfiguracionHolder INSTANCIA = new ConfiguracionHolder();
    }

    static ConfiguracionHolder getInstancia() { return Holder.INSTANCIA; }
}

// ----- Paso 2c: enum. Además resiste serialización y reflexión. -----
enum ConfiguracionEnum {
    INSTANCIA;

    /** Un enum no puede leer sus propios campos estáticos en el constructor: el contador vive en otra clase. */
    static final AtomicInteger CONSTRUCCIONES = ContadorEnum.N;

    ConfiguracionEnum() {
        ContadorEnum.N.incrementAndGet();
        Carrera.construccionLenta();
    }
}

final class ContadorEnum {
    static final AtomicInteger N = new AtomicInteger();
}

// ----- Paso 3: estado mutable global contamina pruebas -----
enum RegistroGlobal {
    INSTANCIA;
    private final List<String> mensajes = new ArrayList<>();
    void registrar(String m) { mensajes.add(m); }
    int cantidad() { return mensajes.size(); }
    void limpiar() { mensajes.clear(); }
}

final class ServicioAlertasGlobal {
    String alertar(String m) {
        RegistroGlobal.INSTANCIA.registrar(m);
        return "alertas registradas: " + RegistroGlobal.INSTANCIA.cantidad();
    }
}

final class PruebasContaminadas {
    /** "Prueba A" registra una alerta. "Prueba B" espera que, tras su alerta, haya exactamente 1. */
    static String correr(boolean correrA) {
        if (correrA) {
            new ServicioAlertasGlobal().alertar("prueba A");
        }
        String b = new ServicioAlertasGlobal().alertar("prueba B");
        return b.equals("alertas registradas: 1") ? "B pasa" : "B FALLA (" + b + ")";
    }
}

// ----- Paso 4: la instancia se inyecta -----
final class Registro {
    private final List<String> mensajes = new ArrayList<>();
    void registrar(String m) { mensajes.add(m); }
    int cantidad() { return mensajes.size(); }
}

final class ServicioAlertas {
    private final Registro registro;
    ServicioAlertas(Registro registro) { this.registro = registro; }
    String alertar(String m) {
        registro.registrar(m);
        return "alertas registradas: " + registro.cantidad();
    }
}
