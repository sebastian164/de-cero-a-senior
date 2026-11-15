import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class Main {

    public static void main(String[] args) {
        // En producción: reloj real, generador real, correo real.
        // Aquí, las "pruebas": todo controlado, sin infraestructura.
        probar("primera quincena da 20%", "2026-03-10T10:00:00Z", 3, Optional.of(20));
        probar("segunda quincena da 10%", "2026-03-20T10:00:00Z", 3, Optional.of(10));
        probar("otro mes no da cupón", "2026-03-10T10:00:00Z", 7, Optional.empty());
    }

    static void probar(String caso, String instante, int mesNacimiento, Optional<Integer> esperado) {
        Clock relojFijo = Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
        List<String> enviados = new ArrayList<>();
        ServicioCupones servicio = new ServicioCupones(relojFijo, () -> "CUMPLE-0001",
                (destino, mensaje) -> enviados.add(mensaje));

        Optional<Cupon> cupon = servicio.generarCuponDeCumpleanos("Ana", LocalDate.of(1990, mesNacimiento, 1));

        boolean correcto = cupon.map(Cupon::descuento).equals(esperado)
                && enviados.size() == (esperado.isPresent() ? 1 : 0);
        System.out.println((correcto ? "PASA  " : "FALLA ") + caso);
    }
}

record Cupon(String codigo, int descuento) { }

interface Correo {
    void enviar(String destino, String mensaje);
}

class ServicioCupones {

    private final Clock reloj;
    private final Supplier<String> generadorDeCodigos;
    private final Correo correo;

    ServicioCupones(Clock reloj, Supplier<String> generadorDeCodigos, Correo correo) {
        this.reloj = reloj;
        this.generadorDeCodigos = generadorDeCodigos;
        this.correo = correo;
    }

    Optional<Cupon> generarCuponDeCumpleanos(String cliente, LocalDate nacimiento) {
        LocalDate hoy = LocalDate.now(reloj);
        if (nacimiento.getMonth() != hoy.getMonth()) {
            return Optional.empty();
        }
        Cupon cupon = new Cupon(generadorDeCodigos.get(), descuentoPara(hoy));
        correo.enviar(cliente, "Tu cupón " + cupon.codigo() + " de " + cupon.descuento() + "%");
        return Optional.of(cupon);
    }

    /** Decisión pura: se puede probar sin nada más. */
    static int descuentoPara(LocalDate fecha) {
        return fecha.getDayOfMonth() <= 15 ? 20 : 10;
    }
}
