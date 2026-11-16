package jvm;

public final class Recursion {
    private Recursion() { }

    /** Recursión sin caso base: cada llamada agrega un marco a la pila del hilo. */
    public static long sinFin(long n) { return sinFin(n + 1) + 1; }

    static int profundidad;
    public static void medir() { profundidad++; medir(); }
}
