package concurrencia;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class Contadores {
    private Contadores() { }

    public interface Contador { void incrementar(); long valor(); }

    public static class Inseguro implements Contador {
        private int valor;
        public void incrementar() { valor++; }               // leer, sumar, escribir: se pierden incrementos
        public long valor() { return valor; }
    }

    public static class ConSynchronized implements Contador {
        private int valor;
        public synchronized void incrementar() { valor++; }
        public synchronized long valor() { return valor; }
    }

    public static class ConAtomico implements Contador {
        private final AtomicInteger valor = new AtomicInteger();
        public void incrementar() { valor.incrementAndGet(); }
        public long valor() { return valor.get(); }
    }

    public static class ConLongAdder implements Contador {
        private final LongAdder valor = new LongAdder();
        public void incrementar() { valor.increment(); }
        public long valor() { return valor.sum(); }
    }

    /** 10 hilos que incrementan 'porHilo' veces cada uno, arrancando a la vez. */
    public static long ejecutar(Contador contador, int hilos, int porHilo) throws InterruptedException {
        var salida = new java.util.concurrent.CountDownLatch(1);
        Thread[] trabajadores = new Thread[hilos];
        for (int i = 0; i < hilos; i++) {
            trabajadores[i] = new Thread(() -> {
                try { salida.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (int j = 0; j < porHilo; j++) contador.incrementar();
            });
            trabajadores[i].start();
        }
        salida.countDown();
        for (Thread t : trabajadores) t.join();
        return contador.valor();
    }
}
