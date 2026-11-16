# Proxy

Familia: estructural. Frecuencia en Java empresarial: muy alta (casi siempre generado por el framework).

## El problema

Necesitas **controlar el acceso** a un objeto: retrasar su creación porque es costosa, verificar permisos antes de dejar pasar una llamada, guardar resultados en caché, abrir y cerrar una transacción alrededor, o hacer que un objeto remoto parezca local. No quieres que el cliente ni el objeto real se enteren.

## Proxy frente a Decorator

Tienen la misma estructura (misma interfaz, un objeto envuelto) y distinta intención.

| | Decorator | Proxy |
|---|---|---|
| Intención | Agregar comportamiento | Controlar el acceso |
| Quién lo arma | El cliente, por capas | Normalmente se oculta al cliente |
| Puede no llamar al objeto real | Raro | Habitual (caché, permisos, carga diferida) |

## Cómo reconocer que lo necesitas

- Crear el objeto real es caro y no siempre se usa.
- Hay que comprobar algo antes de cada llamada.
- El mismo código de apertura y cierre rodea muchos métodos.
- El objeto real vive en otro proceso.

## Antes

```java
class ServicioReportes {
    Reporte generar(String usuario, int anio) {
        if (!permisos.puedeVerReportes(usuario)) throw new SecurityException();   // seguridad
        Reporte enCache = cache.get(anio);                                         // caché
        if (enCache != null) return enCache;
        Reporte reporte = calcular(anio);                                          // lo único que es negocio
        cache.put(anio, reporte);
        return reporte;
    }
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        Reportes reportes = new ReportesConPermisos(
                new ReportesConCache(new ReportesReales()), Set.of("ana"));

        System.out.println(reportes.ventasDelAnio("ana", 2025));
        System.out.println(reportes.ventasDelAnio("ana", 2025));   // sale de la caché
        try {
            reportes.ventasDelAnio("luis", 2025);
        } catch (SecurityException e) {
            System.out.println("Acceso denegado: " + e.getMessage());
        }
    }
}

interface Reportes {
    String ventasDelAnio(String usuario, int anio);
}

/** El objeto real: solo negocio. */
class ReportesReales implements Reportes {
    @Override
    public String ventasDelAnio(String usuario, int anio) {
        System.out.println("  (calculando " + anio + ", operación costosa)");
        return "Ventas " + anio + ": $1.250 millones";
    }
}

/** Proxy de caché: puede responder sin llamar al objeto real. */
class ReportesConCache implements Reportes {

    private final Reportes real;
    private final Map<Integer, String> cache = new HashMap<>();

    ReportesConCache(Reportes real) {
        this.real = real;
    }

    @Override
    public String ventasDelAnio(String usuario, int anio) {
        return cache.computeIfAbsent(anio, a -> real.ventasDelAnio(usuario, a));
    }
}

/** Proxy de protección: decide si la llamada pasa. */
class ReportesConPermisos implements Reportes {

    private final Reportes real;
    private final Set<String> autorizados;

    ReportesConPermisos(Reportes real, Set<String> autorizados) {
        this.real = real;
        this.autorizados = autorizados;
    }

    @Override
    public String ventasDelAnio(String usuario, int anio) {
        if (!autorizados.contains(usuario)) {
            throw new SecurityException(usuario + " no puede ver reportes");
        }
        return real.ventasDelAnio(usuario, anio);
    }
}
```

## Alternativas

- **AOP del framework**: en Spring casi nunca se escribe un proxy a mano.
- **Decorator**, si la intención es añadir y el cliente compone.
- **Llamada directa**, si no hay acceso que controlar.
- **`Supplier` con memoización**, para carga diferida de un valor.

## En Spring

Esta es la lección de patrones con más consecuencias prácticas. `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize` y `@Retryable` funcionan porque Spring **no te entrega tu objeto: te entrega un proxy** que lo envuelve: en Spring Framework, un proxy dinámico del JDK si hay interfaz o una subclase generada con CGLIB si no; Spring Boot usa CGLIB en ambos casos por defecto (`spring.aop.proxy-target-class=true`).

De ahí salen tres trampas que todo senior debe explicar:

1. **Autoinvocación.** Si un método de la clase llama a otro método `@Transactional` de la misma clase (`this.otro()`), la llamada no pasa por el proxy y la anotación no tiene efecto.
2. **Métodos `private` o `final`.** El proxy no puede interceptarlos.
3. **Objetos creados con `new`.** No son beans, no hay proxy, las anotaciones se ignoran.

También son proxies las asociaciones `LAZY` de Hibernate (de ahí la `LazyInitializationException` al acceder fuera de la sesión) y los clientes declarativos como Feign o las interfaces de Spring Data.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| El objeto real queda limpio de aspectos transversales | Comportamiento invisible en el código |
| El cliente no cambia | Trampas como la autoinvocación |
| Un mismo proxy sirve a muchas clases (si es dinámico) | Trazas de error más largas |
| Permite carga diferida y caché transparentes | Pequeño costo por llamada |

## Errores comunes

- Creer que `@Transactional` funciona en llamadas internas de la misma clase.
- Proxies de caché sin política de invalidación ni límite de tamaño.
- Un proxy de caché que ignora parámetros que sí afectan el resultado (el ejemplo ignora el usuario a propósito: discútelo en el taller).
- Poner reglas de negocio en el proxy.

## Cuándo no usarlo

Cuando no hay acceso que controlar; cuando el comportamiento oculto sorprendería a quien mantiene el código; cuando una llamada explícita es más clara que la magia.

## Taller

1. En el ejemplo, la caché usa solo el año como clave. Describe un escenario en el que eso sea un error de seguridad y corrígelo.
2. Invierte el orden de los proxies (caché por fuera, permisos por dentro). ¿Qué falla? Demuéstralo.
3. Escribe un proxy de carga diferida para `interface Imagen { void mostrar(); }` cuya implementación real "carga del disco" en el constructor. La carga debe ocurrir solo en el primer `mostrar()`.
4. Implementa un proxy genérico con `java.lang.reflect.Proxy` que mida el tiempo de cualquier método de cualquier interfaz.
5. Escribe (en papel, con Spring) una clase con dos métodos donde uno llama al otro, que es `@Transactional`. Explica qué ocurre y da dos formas de corregirlo.

Entrega: rama `taller/n3-proxy`.
