package jvm;

import java.util.HashMap;
import java.util.Map;

/** El ejemplo de fuga del README, ejecutable: un mapa estático que nunca se limpia. */
public class RegistroDeSesiones {
    public record Sesion(String id, byte[] datos) { }

    private static final Map<String, Sesion> SESIONES = new HashMap<>();   // nunca se limpia

    static void registrar(Sesion sesion) {
        SESIONES.put(sesion.id(), sesion);     // cada inicio de sesión se queda para siempre
    }

    public static void main(String[] args) {
        for (int i = 0; ; i++) {
            registrar(new Sesion("sesion-" + i, new byte[100 * 1024]));    // 100 KB por sesión
            if (i % 100 == 0) System.out.println("sesiones: " + i);
        }
    }
}
