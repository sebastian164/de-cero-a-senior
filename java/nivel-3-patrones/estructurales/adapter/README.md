# Adapter

Familia: estructural. Frecuencia en Java empresarial: muy alta.

## El problema

Tu código espera un contrato y la pieza que quieres usar (una biblioteca, un SDK, un sistema heredado, una API externa) ofrece otro. No puedes o no quieres modificar ninguno de los dos lados. Si usas la pieza directamente, su forma de hablar se filtra por todo tu código.

## Cómo reconocer que lo necesitas

- Integras un SDK de terceros cuyos nombres, tipos y errores no coinciden con tu dominio.
- Tienes una interfaz propia (un puerto) y una clase ajena que "casi" la cumple.
- Conviertes datos de un formato a otro en muchos lugares antes de llamar a lo mismo.
- Quieres poder cambiar de proveedor sin tocar el negocio.

## Antes

```java
class ServicioCobros {
    private final SdkPagoRapido sdk = new SdkPagoRapido();

    void cobrar(String cliente, long pesos) {
        // el negocio hablando el idioma del proveedor
        int codigo = sdk.ejecutarTransaccion(cliente, pesos * 100, "COP", "SALE");
        if (codigo != 0) {
            throw new RuntimeException("Error PR-" + codigo);
        }
    }
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        ServicioCobros servicio = new ServicioCobros(new AdaptadorPagoRapido(new SdkPagoRapido()));
        servicio.cobrar("ana", 50_000);
        try {
            servicio.cobrar("moroso", 50_000);
        } catch (PagoRechazadoException e) {
            System.out.println("Rechazado: " + e.getMessage());
        }
    }
}

// ----- Lo que el negocio define -----

interface PasarelaPagos {
    void cobrar(String cliente, long pesos);
}

class PagoRechazadoException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    PagoRechazadoException(String mensaje) { super(mensaje); }
}

class ServicioCobros {
    private final PasarelaPagos pasarela;
    ServicioCobros(PasarelaPagos pasarela) { this.pasarela = pasarela; }

    void cobrar(String cliente, long pesos) {
        pasarela.cobrar(cliente, pesos);
        System.out.println("Cobro registrado: " + cliente + " $" + pesos);
    }
}

// ----- Lo que no podemos modificar -----

class SdkPagoRapido {
    /** Recibe centavos; devuelve 0 si todo salió bien y un código de error si no. */
    int ejecutarTransaccion(String idCliente, long centavos, String moneda, String tipo) {
        return idCliente.equals("moroso") ? 51 : 0;
    }
}

// ----- El adaptador: traduce en ambos sentidos -----

class AdaptadorPagoRapido implements PasarelaPagos {

    private final SdkPagoRapido sdk;

    AdaptadorPagoRapido(SdkPagoRapido sdk) {
        this.sdk = sdk;
    }

    @Override
    public void cobrar(String cliente, long pesos) {
        int codigo = sdk.ejecutarTransaccion(cliente, pesos * 100, "COP", "SALE");
        if (codigo != 0) {
            throw new PagoRechazadoException(traducir(codigo));
        }
    }

    private String traducir(int codigo) {
        return codigo == 51 ? "Fondos insuficientes" : "Error de la pasarela (" + codigo + ")";
    }
}
```

El adaptador traduce tres cosas: unidades (pesos a centavos), convención de errores (códigos a excepciones del dominio) y vocabulario. El negocio no sabe que existe `SdkPagoRapido`.

## Alternativas

- **Usar la pieza directamente**, si es estable, es tuya o es parte de la plataforma (`java.time`, las colecciones).
- **Una función de conversión**, si solo difieren los datos y no el contrato.
- **Facade**, si lo que quieres es simplificar un subsistema, no hacerlo encajar en una interfaz existente.

## En Spring

- `HandlerAdapter` permite que `DispatcherServlet` invoque distintos tipos de controladores con un contrato único.
- En arquitectura hexagonal, las implementaciones de los puertos son adaptadores: un adaptador JPA, un adaptador REST, un adaptador de mensajería.
- `Converter<S, T>` y los `HttpMessageConverter` adaptan representaciones.
- En el JDK: `InputStreamReader` adapta bytes a caracteres; `Arrays.asList` adapta un arreglo a `List`.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| Aísla al negocio de APIs ajenas | Una clase más por integración |
| Cambiar de proveedor toca solo el adaptador | La traducción puede perder capacidades del proveedor |
| El negocio se prueba con un doble del puerto | Hay que mantener el mapeo cuando el proveedor cambia |
| Centraliza la traducción de errores y unidades | Si el puerto imita al proveedor, no aísla nada |

## Errores comunes

- Diseñar la interfaz propia copiando la del proveedor: el adaptador queda trivial y el acoplamiento intacto.
- Poner reglas de negocio dentro del adaptador.
- Dejar escapar tipos o excepciones del SDK a través del adaptador.
- Adaptadores que hacen de todo: traducir, reintentar, cachear, registrar. Esas responsabilidades adicionales son decoradores o proxies.

## Cuándo no usarlo

Cuando controlas ambos lados y puedes cambiar uno; cuando la biblioteca es tan estable y central que aislarla no aporta; en código desechable.

## Taller

Tu aplicación define `interface ServicioClima { Pronostico pronosticoPara(String ciudad); }` donde `Pronostico` tiene temperatura en grados Celsius y probabilidad de lluvia entre 0 y 1.

Hay dos proveedores que no puedes modificar: uno devuelve un texto como `"TUNJA|53.6F|RAIN:40"` y el otro un `Map<String, Object>` con claves `temp_k` (Kelvin) y `pop` (porcentaje como entero), y lanza una excepción comprobada propia cuando la ciudad no existe.

1. Simula ambos proveedores y escribe un adaptador para cada uno.
2. Define cómo se comporta el contrato cuando la ciudad no existe y haz que ambos adaptadores lo cumplan igual.
3. Escribe un servicio que recomiende llevar sombrilla y pruébalo con ambos sin cambiarlo.
4. ¿Qué información del proveedor se pierde al adaptar? ¿Es aceptable?
5. Agrega reintentos ante fallo. ¿Lo pusiste en el adaptador? Argumenta dónde debería ir.

Entrega: rama `taller/n3-adapter`.
