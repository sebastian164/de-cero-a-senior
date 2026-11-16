# Chain of Responsibility

Familia: comportamiento. Frecuencia en Java empresarial: alta (filtros e interceptores).

## El problema

Una solicitud debe pasar por varios pasos de procesamiento o puede ser atendida por uno de varios candidatos, y ni el emisor ni los pasos deberían conocer la lista completa. Si un método central decide quién atiende con una cadena de `if`, cada paso nuevo lo modifica.

## Dos variantes

| Variante | Comportamiento | Ejemplo |
|---|---|---|
| Clásica | El primer eslabón que puede atender lo hace y la cadena se detiene | Niveles de aprobación |
| Tubería (pipeline) | Todos los eslabones procesan en orden; cualquiera puede cortar | Filtros web, middleware |

## Cómo reconocer que lo necesitas

- Hay un procesamiento en etapas, cada una con derecho a detenerlo.
- El orden de las etapas debe poder configurarse.
- Varios objetos pueden atender una solicitud y se decide en ejecución cuál.
- Lógica transversal antes y después de un núcleo: autenticar, registrar, limitar, validar.

## Antes

```java
String aprobar(long monto) {
    if (monto <= 500_000) return "Aprobado por el supervisor";
    else if (monto <= 5_000_000) return "Aprobado por el gerente";
    else if (monto <= 50_000_000) return "Aprobado por el director";
    else return "Requiere junta directiva";
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Variante clásica: atiende el primero que puede.
        Aprobador cadena = new Aprobador("Supervisor", 500_000,
                new Aprobador("Gerente", 5_000_000,
                        new Aprobador("Director", 50_000_000, null)));
        System.out.println(cadena.aprobar(300_000));
        System.out.println(cadena.aprobar(8_000_000));
        System.out.println(cadena.aprobar(90_000_000));

        // Variante tubería: todos procesan y cualquiera puede cortar.
        Tuberia tuberia = new Tuberia(List.of(new FiltroAutenticacion(), new FiltroRegistro()));
        System.out.println(tuberia.procesar("token-valido /pedidos"));
        System.out.println(tuberia.procesar("sin-token /pedidos"));
    }
}

class Aprobador {

    private final String cargo;
    private final long limite;
    private final Aprobador siguiente;

    Aprobador(String cargo, long limite, Aprobador siguiente) {
        this.cargo = cargo;
        this.limite = limite;
        this.siguiente = siguiente;
    }

    String aprobar(long monto) {
        if (monto <= limite) {
            return "$" + monto + " aprobado por " + cargo;
        }
        if (siguiente == null) {
            return "$" + monto + " requiere junta directiva";
        }
        return siguiente.aprobar(monto);
    }
}

interface Filtro {
    String filtrar(String solicitud, Cadena cadena);
}

interface Cadena {
    String continuar(String solicitud);
}

class Tuberia {

    private final List<Filtro> filtros;

    Tuberia(List<Filtro> filtros) {
        this.filtros = filtros;
    }

    String procesar(String solicitud) {
        return desde(0).continuar(solicitud);
    }

    private Cadena desde(int indice) {
        if (indice == filtros.size()) {
            return solicitud -> "200 OK para [" + solicitud + "]";   // el núcleo
        }
        return solicitud -> filtros.get(indice).filtrar(solicitud, desde(indice + 1));
    }
}

class FiltroAutenticacion implements Filtro {
    @Override
    public String filtrar(String solicitud, Cadena cadena) {
        if (!solicitud.startsWith("token-valido")) {
            return "401 No autorizado";            // corta la cadena
        }
        return cadena.continuar(solicitud);
    }
}

class FiltroRegistro implements Filtro {
    @Override
    public String filtrar(String solicitud, Cadena cadena) {
        System.out.println("  -> entra " + solicitud);
        String respuesta = cadena.continuar(solicitud);
        System.out.println("  <- sale " + respuesta);
        return respuesta;
    }
}
```

## Alternativas

- **Una lista de reglas recorrida en un bucle**, si los eslabones no necesitan envolver ni cortar.
- **Decorator**, si todos los pasos siempre se ejecutan y no hay corte.
- **Un `switch` o tabla**, si los candidatos son pocos y fijos.
- **Strategy con selector**, si exactamente un candidato atiende y se sabe cuál por un dato.

## En Spring

- **Spring Security** es una cadena de filtros (`SecurityFilterChain`): cada uno autentica, autoriza, protege contra CSRF o corta la petición.
- `jakarta.servlet.Filter` con `chain.doFilter(request, response)`: exactamente la interfaz `Filtro` del ejemplo.
- `HandlerInterceptor` (`preHandle` puede devolver `false` y cortar), `ClientHttpRequestInterceptor`, `WebFilter` en WebFlux.
- `@Order` controla la posición de cada eslabón.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| El emisor no sabe quién atiende | Nada garantiza que alguien atienda |
| Eslabones independientes y reordenables | El flujo es difícil de seguir y de depurar |
| Agregar un paso no modifica los demás | El orden es crítico y suele estar en configuración |
| Cada eslabón tiene una responsabilidad | Cadenas largas alargan las trazas y el tiempo |

## Errores comunes

- Olvidar llamar a `continuar` y cortar la cadena sin querer.
- Eslabones que dependen de que otro haya corrido antes, sin que el orden esté documentado ni validado.
- No definir qué pasa si nadie atiende.
- Eslabones que modifican estado compartido de forma que el siguiente no espera.

## Cuándo no usarlo

Cuando el orden y los participantes son fijos y pocos; cuando siempre atiende el mismo; cuando la claridad de un método lineal vale más que la flexibilidad.

## Taller

Un sistema de soporte recibe tiquetes con categoría, prioridad y cliente. Deben pasar por: detección de spam (corta), asignación automática de respuestas frecuentes (corta si resuelve), escalamiento a nivel 1, y si es prioridad crítica o cliente corporativo, directo a nivel 2.

1. Implementa la cadena. Decide cuál variante necesitas o si necesitas ambas.
2. ¿Qué pasa con un tiquete que nadie atiende? Programa un eslabón final explícito.
3. Reordena dos eslabones y describe un error real que eso produciría.
4. Agrega un eslabón de métricas que mida cuánto tarda el resto de la cadena.
5. Relaciona tu diseño con un filtro de Spring Security: ¿qué equivale a `chain.doFilter`?

Entrega: rama `taller/n3-chain-of-responsibility`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
