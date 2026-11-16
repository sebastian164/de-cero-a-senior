# Singleton

Familia: creacional. Frecuencia en Java empresarial: muy alta como **alcance** de objetos; baja y desaconsejada como patrón escrito a mano.

## El problema

Debe existir una única instancia de algo (una configuración, un pool de conexiones, un registro) y todo el programa debe usar la misma. El patrón clásico resuelve además un segundo problema que es el que lo hace polémico: dar un punto de acceso global a esa instancia.

## Dos ideas que conviene separar

| Idea | Valoración |
|---|---|
| "Solo hay una instancia de este objeto" | Legítima y muy común. |
| "Cualquiera puede obtenerla desde cualquier parte con un método estático" | Es estado global. Problemática. |

Spring separa las dos: crea una sola instancia de cada bean y la **inyecta**. No hay acceso global.

## Cómo reconocer que lo necesitas

- Crear el objeto es caro y no tiene sentido tener varios.
- Varias instancias causarían inconsistencias (dos generadores de consecutivos).
- El objeto representa un recurso único del proceso.

## Antes

```java
class Configuracion {
    private static Configuracion instancia;

    static Configuracion getInstancia() {
        if (instancia == null) {              // dos hilos pueden entrar a la vez
            instancia = new Configuracion();  // y crear dos instancias
        }
        return instancia;
    }
}

class ServicioPrecios {
    long conIva(long valor) {
        // dependencia oculta: nada en la firma avisa que usa Configuracion
        return valor + valor * Configuracion.getInstancia().iva() / 100;
    }
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        // Forma 1: el enum. Si de verdad hace falta un singleton clásico, esta es la forma segura.
        System.out.println(Consecutivo.INSTANCIA.siguiente());
        System.out.println(Consecutivo.INSTANCIA.siguiente());

        // Forma 2 (preferida): una sola instancia, creada en la raíz y entregada a quien la necesita.
        Configuracion configuracion = new Configuracion(19);
        ServicioPrecios precios = new ServicioPrecios(configuracion);
        ServicioFacturas facturas = new ServicioFacturas(configuracion);
        System.out.println(precios.conIva(100_000));
        System.out.println(facturas.resumen(100_000));
    }
}

/** Singleton con enum: seguro entre hilos, ante serialización y ante reflexión. */
enum Consecutivo {
    INSTANCIA;

    private final java.util.concurrent.atomic.AtomicLong contador = new java.util.concurrent.atomic.AtomicLong();

    long siguiente() {
        return contador.incrementAndGet();
    }
}

/** Una clase normal. Que haya una sola instancia es decisión de quien ensambla. */
final class Configuracion {

    private final int iva;

    Configuracion(int iva) {
        this.iva = iva;
    }

    int iva() {
        return iva;
    }
}

class ServicioPrecios {

    private final Configuracion configuracion;

    ServicioPrecios(Configuracion configuracion) {
        this.configuracion = configuracion;
    }

    long conIva(long valor) {
        return valor + valor * configuracion.iva() / 100;
    }
}

class ServicioFacturas {

    private final Configuracion configuracion;

    ServicioFacturas(Configuracion configuracion) {
        this.configuracion = configuracion;
    }

    String resumen(long valor) {
        return "IVA " + configuracion.iva() + "% sobre $" + valor;
    }
}
```

En la forma 2 hay una única `Configuracion`, pero es inmutable, su uso es visible en los constructores y en una prueba se puede pasar otra.

## Alternativas

- **Inyección de dependencias con una sola instancia**: lo que hace Spring. Es la opción por defecto.
- **`enum` de un elemento**: cuando no hay contenedor y la unicidad es esencial.
- **Clase con métodos estáticos**, solo para funciones puras sin estado (`Math`).
- **Inicialización bajo demanda con clase interna estática** (*holder idiom*), si se necesita creación diferida sin enum.

## En Spring

- **Todo bean es singleton por defecto**: una instancia por contenedor. Otros alcances: `prototype`, `request`, `session`.
- **Consecuencia práctica más importante del curso sobre este tema:** como la misma instancia atiende todas las peticiones concurrentes, un bean singleton **no debe tener estado mutable** por petición. Un atributo `private Usuario usuarioActual` en un `@Service` es un error de concurrencia.
- Inyectar un bean `prototype` o `request` dentro de un singleton requiere `ObjectProvider` o un proxy de alcance; de lo contrario se inyecta una sola vez.
- "Singleton" en Spring es por contenedor, no por JVM ni por clúster. Con tres réplicas del servicio hay tres instancias.

## Ventajas, desventajas y trade-offs

| A favor | En contra (del clásico con acceso global) |
|---|---|
| Garantiza una sola instancia | Es estado global: dependencias ocultas |
| Ahorra recursos | Las pruebas se contaminan entre sí |
| Punto único de coordinación | Difícil de sustituir por un doble |
| | Las versiones caseras suelen fallar con varios hilos |

## Errores comunes

- Inicialización perezosa sin sincronizar, como en el "antes".
- *Double-checked locking* sin `volatile`.
- Singletons con estado mutable usado desde varios hilos.
- Creer que un singleton es único en un sistema distribuido: un contador en memoria no sirve como consecutivo si hay varias réplicas.
- Usar el singleton como cajón global donde se guarda de todo.

## Cuándo no usarlo

Casi nunca hace falta escribirlo a mano si hay un contenedor. No lo uses para evitar pasar parámetros; no lo uses si el objeto tiene estado que cambia por usuario o por petición.

## Taller

1. Implementa el singleton perezoso del "antes" y escribe un programa con varios hilos que demuestre que puede crear más de una instancia (cuenta las construcciones).
2. Corrígelo de tres formas: `synchronized`, *holder idiom* y `enum`. Explica las diferencias.
3. Escribe dos "pruebas" en `Main` que usen un singleton con estado mutable y muestra cómo el resultado de la segunda depende de que la primera haya corrido antes.
4. Reescribe para que la instancia se inyecte. ¿Qué cambió en la facilidad de prueba?
5. En papel: un `@Service` con un atributo `List<String> errores` que se llena en cada petición. Describe el error con dos peticiones simultáneas y tres formas de corregirlo.

Entrega: rama `taller/n3-singleton`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
