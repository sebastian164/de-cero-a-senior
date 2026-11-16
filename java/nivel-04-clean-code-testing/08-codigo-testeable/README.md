# 08 — Código testeable

## Idea sencilla

Un electricista prueba un bombillo conectándolo a un probador, sin tener que instalarlo en el techo de la casa. Puede hacerlo porque el bombillo tiene una rosca estándar: se conecta a cualquier cosa que tenga esa rosca.

Si el bombillo viniera soldado al techo, la única forma de probarlo sería encender toda la casa. El código testeable es código con "rosca": se puede desconectar de su entorno real y conectar a uno de prueba.

## Explicación técnica

Una prueba unitaria necesita tres cosas: poner el código en una situación conocida, ejecutarlo y observar el resultado. El código es difícil de probar cuando impide alguna.

### Enemigos de la testeabilidad

| Problema | Por qué estorba | Solución |
|---|---|---|
| `new` de colaboradores dentro de la lógica | No se puede sustituir el colaborador | Inyectar por constructor |
| Métodos y estado `static` | No se pueden reemplazar; el estado se filtra entre pruebas | Objetos e inyección |
| Reloj del sistema (`LocalDate.now()`) | El resultado depende de cuándo se ejecuta | Inyectar un `Clock` |
| Aleatoriedad y UUID | Resultado no repetible | Inyectar el generador |
| Red, disco, base de datos | Lento, frágil, requiere entorno | Interfaz y doble de prueba |
| Lógica en el constructor | No se puede crear el objeto sin efectos | Constructores que solo asignan |
| Métodos enormes | Demasiadas combinaciones | Extraer |
| Salida solo por `System.out` | Nada que observar | Devolver valores |
| Singletons y contexto global | Dependencias invisibles | Pasarlas explícitamente |

### La idea central

**Separa las decisiones de los efectos.** La lógica que decide (calcular, validar, elegir) debe ser código puro: recibe datos, devuelve datos. Los efectos (guardar, enviar, leer la hora) se dejan en el borde. Lo puro se prueba trivialmente; lo impuro queda delgado.

Un método es fácil de probar cuando es **determinista** (mismas entradas, mismo resultado) y **observable** (el resultado se puede comprobar).

El diseño testeable y el buen diseño coinciden: la dificultad para probar una clase es un síntoma de acoplamiento. Por eso DIP y la testeabilidad son la misma conversación.

## Código con problemas

<!-- file: ejemplos/antes/Main.java -->
```java
import java.time.LocalDate;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        new ServicioCupones().generarCuponDeCumpleanos("Ana", LocalDate.of(1990, LocalDate.now().getMonthValue(), 1));
    }
}

class ServicioCupones {

    void generarCuponDeCumpleanos(String cliente, LocalDate nacimiento) {
        LocalDate hoy = LocalDate.now();
        if (nacimiento.getMonth() == hoy.getMonth()) {
            String codigo = "CUMPLE-" + new Random().nextInt(10_000);
            int descuento = hoy.getDayOfMonth() <= 15 ? 20 : 10;
            new CorreoSmtp().enviar(cliente, "Tu cupón " + codigo + " de " + descuento + "%");
        }
    }
}

class CorreoSmtp {
    void enviar(String destino, String mensaje) {
        System.out.println("[SMTP real] a " + destino + ": " + mensaje);
    }
}
```

## Análisis

Intenta escribir una prueba que verifique "en la primera quincena el descuento es del 20 %":

- Solo pasa si se ejecuta entre el día 1 y el 15. La misma prueba falla el día 16.
- El código del cupón es aleatorio: no se puede comparar.
- Enviaría un correo real en cada ejecución.
- El método no devuelve nada: no hay resultado que observar.

Cuatro dependencias ocultas: el reloj, el azar, el correo y la salida.

## Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
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
```

Las tres comprobaciones pasan cualquier día del año, en milisegundos y sin enviar nada. `java.time.Clock` existe exactamente para esto.

## Taller

<!-- file: taller/inicio/Main.java -->
```java
import java.time.LocalDateTime;
import java.util.UUID;

public class Main {

    public static void main(String[] args) {
        new ServicioTurnos().asignar("Ana");
    }
}

class ServicioTurnos {

    private static int contador = 0;

    void asignar(String paciente) {
        LocalDateTime ahora = LocalDateTime.now();
        if (ahora.getHour() < 7 || ahora.getHour() >= 17) {
            System.out.println("Fuera de horario");
            return;
        }
        contador++;
        String turno = (ahora.getHour() < 12 ? "M" : "T") + contador + "-" + UUID.randomUUID().toString().substring(0, 4);
        new PantallaSala().mostrar(paciente + " -> " + turno);
        new Sms().enviar(paciente, "Tu turno es " + turno);
    }
}

class PantallaSala { void mostrar(String texto) { System.out.println("[pantalla] " + texto); } }
class Sms { void enviar(String paciente, String texto) { System.out.println("[sms] " + texto); } }
```

1. Lista todas las dependencias ocultas. Hay al menos cinco.
2. Refactoriza para que sea testeable. Separa las decisiones puras de los efectos.
3. Escribe en `Main` comprobaciones para: fuera de horario, turno de mañana, turno de tarde y numeración consecutiva.
4. El contador `static` hace que el orden de las pruebas importe. Demuéstralo en la versión original y explica cómo lo resolviste.
5. ¿Qué parte quedó sin probar con estas comprobaciones y cómo la cubrirías?

Se revisará: que las pruebas sean deterministas e independientes entre sí. Rama `taller/n4-08-testeable`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Aplicación profesional

- **Inyección por constructor:** la clase se instancia en una prueba con `new`, sin levantar Spring. Si para probar un servicio necesitas `@SpringBootTest`, el diseño te está avisando.
- **`Clock` como bean:** declara `@Bean Clock clock() { return Clock.systemDefaultZone(); }` e inyéctalo. Nunca `LocalDate.now()` sin argumento en lógica de negocio.
- **Lógica en el dominio, no en el servicio:** un método de una entidad o de un objeto de valor se prueba sin dobles.
- **Evita la lógica en `@PostConstruct`**, en bloques estáticos y en constructores.
- **Métodos estáticos de utilidades con efectos** (`ContextoSeguridad.usuarioActual()`): envuélvelos tras una interfaz que se pueda sustituir.

## Preguntas senior

1. ¿Por qué decimos que la dificultad para probar es un síntoma de diseño y no un problema de las herramientas de prueba?
2. ¿Cómo pruebas código que depende de la hora, de un número aleatorio y de un identificador generado?
3. Hay herramientas que permiten simular métodos estáticos y constructores. ¿Por qué usarlas suele ser una mala señal?
4. ¿Qué es "núcleo funcional, cáscara imperativa" y cómo lo aplicarías a un servicio de Spring?
5. ¿Cómo haces testeable una clase heredada sin poder cambiar a quienes la usan?

## Cuándo no forzarlo

No hace falta inyectar lo que es determinista y barato (`Math`, `String`, colecciones). No crees interfaces solo para poder simular objetos de valor.

Siguiente: [09 — JUnit 5](../09-junit5/).
