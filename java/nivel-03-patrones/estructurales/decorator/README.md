# Decorator

Familia: estructural. Frecuencia en Java empresarial: alta.

## El problema

Quieres agregar comportamiento a un objeto (registro, caché, compresión, reintentos, validación) sin modificar su clase y pudiendo combinar esos agregados libremente. Con herencia, cada combinación exige una subclase: `ServicioConCache`, `ServicioConLog`, `ServicioConCacheYLog`…

## Cómo reconocer que lo necesitas

- Hay comportamientos opcionales que se combinan de varias formas.
- La jerarquía empieza a tener nombres con "Con…Y…".
- Quieres agregar algo antes o después de una operación sin tocar la clase original.
- La clase original es ajena o `final`.

## Antes

```java
class NotificadorSms { ... }
class NotificadorSmsConRegistro extends NotificadorSms { ... }
class NotificadorSmsConReintento extends NotificadorSms { ... }
class NotificadorSmsConRegistroYReintento extends NotificadorSmsConRegistro { ... }
// y todo otra vez para el notificador de correo
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Notificador basico = new NotificadorSms();

        // Se arma por capas, en el orden que se necesite.
        Notificador completo = new ConRegistro(new ConReintento(basico, 3));

        completo.enviar("3001234567", "Tu código es 4821");
    }
}

interface Notificador {
    void enviar(String destino, String mensaje);
}

class NotificadorSms implements Notificador {
    private int intentos = 0;

    @Override
    public void enviar(String destino, String mensaje) {
        intentos++;
        if (intentos < 3) {
            throw new IllegalStateException("Red no disponible (intento " + intentos + ")");
        }
        System.out.println("SMS a " + destino + ": " + mensaje);
    }
}

/** Un decorador ES un Notificador y TIENE un Notificador. */
class ConReintento implements Notificador {

    private final Notificador envuelto;
    private final int maximo;

    ConReintento(Notificador envuelto, int maximo) {
        this.envuelto = envuelto;
        this.maximo = maximo;
    }

    @Override
    public void enviar(String destino, String mensaje) {
        for (int intento = 1; ; intento++) {
            try {
                envuelto.enviar(destino, mensaje);
                return;
            } catch (IllegalStateException e) {
                if (intento == maximo) {
                    throw e;
                }
                System.out.println("  reintentando tras: " + e.getMessage());
            }
        }
    }
}

class ConRegistro implements Notificador {

    private final Notificador envuelto;

    ConRegistro(Notificador envuelto) {
        this.envuelto = envuelto;
    }

    @Override
    public void enviar(String destino, String mensaje) {
        System.out.println("[inicio] envío a " + destino);
        envuelto.enviar(destino, mensaje);
        System.out.println("[fin] envío a " + destino);
    }
}
```

Dos decoradores sirven para cualquier `Notificador`, presente o futuro. Las combinaciones se arman al construir, sin clases nuevas.

## Alternativas

- **Proxy / AOP**, si el comportamiento es transversal y lo aplica el framework (transacciones, caché, seguridad).
- **Strategy**, si quieres cambiar el interior del algoritmo y no envolverlo.
- **Herencia**, si hay un solo agregado y nunca se combinará.
- **Composición de funciones** (`Function.andThen`), cuando lo decorado es una función sin estado.

## En Spring

- `HttpServletRequestWrapper` y `ContentCachingRequestWrapper` decoran la petición.
- `TransactionAwareDataSourceProxy`, `DelegatingPasswordEncoder`.
- En el JDK: `new BufferedReader(new InputStreamReader(new FileInputStream(…)))`, `Collections.unmodifiableList`, `Collections.synchronizedList`.
- Resilience4j decora llamadas con circuit breaker, reintento y límite de tasa.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| Combinaciones sin explosión de subclases | Muchos objetos pequeños encadenados |
| Cada agregado es una clase con una sola responsabilidad | El orden de las capas importa y no es evidente |
| Se agrega comportamiento sin tocar el original | Depurar exige atravesar las capas |
| Se decide al construir | La identidad cambia: el decorador no es `==` al original |

## Errores comunes

- Equivocar el orden: registrar fuera del reintento muestra un envío; registrar dentro, tres.
- Decoradores que dependen de un tipo concreto del envuelto (con `instanceof`).
- Interfaces grandes: cada decorador debe reenviar todos los métodos, incluso los que no le interesan.
- Decoradores con estado compartido entre hilos sin protección.

## Cuándo no usarlo

Cuando hay un solo comportamiento adicional y fijo; cuando la interfaz tiene muchos métodos; cuando el framework ya ofrece el mismo efecto con una anotación.

## Taller

Tienes `interface FuenteDatos { String leer(String clave); void escribir(String clave, String valor); }` y una implementación en memoria.

1. Escribe tres decoradores: caché de lecturas, cifrado del valor (un cifrado César basta) y registro de tiempos.
2. Arma dos combinaciones con distinto orden entre caché y cifrado. Explica qué queda guardado en la caché en cada caso y cuál es correcta.
3. El decorador de caché, ¿qué debe hacer cuando se escribe una clave? Impleméntalo.
4. Agrega un cuarto método a la interfaz. ¿Cuántas clases tuviste que tocar? ¿Qué te dice eso sobre el tamaño de las interfaces decorables?
5. Explica qué diferencia hay entre tu decorador de registro y un aspecto de Spring que haga lo mismo.

Entrega: rama `taller/n3-decorator`.
