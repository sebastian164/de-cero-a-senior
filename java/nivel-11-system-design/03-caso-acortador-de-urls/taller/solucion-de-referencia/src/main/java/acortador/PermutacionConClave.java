package acortador;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Permutación con clave secreta del espacio 62^7: red de Feistel de 6 rondas sobre 42 bits (2^42 > 62^7) con
 * HMAC-SHA256 como función de ronda, y "recorrido de ciclo" (cycle-walking) para quedarse dentro de [0, 62^7):
 * si el resultado cae fuera, se vuelve a cifrar (en promedio 1,25 veces). Una Feistel es biyectiva sea cual sea la
 * función de ronda, y el recorrido de ciclo conserva la biyección. Sin la clave no se puede predecir el código
 * siguiente ni siquiera conociendo muchos pares (n, código); con la afín bastaban dos.
 */
public final class PermutacionConClave implements Permutacion {
    private static final int MITAD = 21;
    private static final long MASCARA = (1L << MITAD) - 1;
    private static final int RONDAS = 6;

    private final SecretKeySpec clave;
    private final ThreadLocal<Mac> mac;

    public PermutacionConClave(String secreto) {
        if (secreto == null || secreto.length() < 16) throw new IllegalArgumentException("la clave debe tener al menos 16 caracteres");
        this.clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.mac = ThreadLocal.withInitial(() -> {
            try {
                Mac m = Mac.getInstance("HmacSHA256");
                m.init(clave);
                return m;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public long aplicar(long n) {
        if (n < 0 || n >= Base62.ESPACIO) throw new IllegalArgumentException("fuera del espacio de códigos: " + n);
        long x = n;
        do { x = cifrar(x); } while (x >= Base62.ESPACIO);
        return x;
    }

    @Override
    public long invertir(long p) {
        if (p < 0 || p >= Base62.ESPACIO) throw new IllegalArgumentException("fuera del espacio de códigos: " + p);
        long x = p;
        do { x = descifrar(x); } while (x >= Base62.ESPACIO);
        return x;
    }

    private long cifrar(long x) {
        long izq = x >>> MITAD, der = x & MASCARA;
        for (int r = 0; r < RONDAS; r++) {
            long nueva = izq ^ ronda(r, der);
            izq = der; der = nueva;
        }
        return (izq << MITAD) | der;
    }

    private long descifrar(long x) {
        long izq = x >>> MITAD, der = x & MASCARA;
        for (int r = RONDAS - 1; r >= 0; r--) {
            long anterior = der ^ ronda(r, izq);
            der = izq; izq = anterior;
        }
        return (izq << MITAD) | der;
    }

    private long ronda(int r, long mitad) {
        Mac m = mac.get();
        m.update((byte) r);
        m.update((byte) (mitad >>> 16)); m.update((byte) (mitad >>> 8)); m.update((byte) mitad);
        byte[] h = m.doFinal();
        return (((h[0] & 0xFFL) << 16) | ((h[1] & 0xFFL) << 8) | (h[2] & 0xFFL)) & MASCARA;
    }
}
