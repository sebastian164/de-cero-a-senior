# 01 — Separación de responsabilidades

Todo el código Spring de este nivel es ilustrativo: muestra estructura y decisiones, y no se compiló al preparar el curso.

## Idea sencilla

En un hospital, urgencias, laboratorio, farmacia y facturación están en áreas distintas, con personal distinto y reglas distintas. Se comunican por canales definidos: una orden de laboratorio, una fórmula. Nadie factura en el quirófano. Esa separación permite que el laboratorio cambie sus equipos sin que farmacia se entere.

La arquitectura de software es lo mismo a escala de sistema: decidir qué áreas existen, qué hace cada una y cómo se hablan.

## Explicación técnica

**Arquitectura** es el conjunto de decisiones estructurales que son costosas de cambiar: cómo se divide el sistema, qué depende de qué y por dónde se comunican las partes.

**Separación de responsabilidades** (*separation of concerns*, Dijkstra) es el principio que la sostiene: cada parte del sistema se ocupa de un asunto y sabe lo mínimo de los demás. Es SRP y cohesión aplicados un nivel más arriba: ya no a clases, sino a paquetes, módulos y servicios.

### Los asuntos típicos de una aplicación empresarial

| Asunto | Se ocupa de | Cambia cuando |
|---|---|---|
| Presentación / entrada | HTTP, JSON, validación de formato, autenticación | Cambia el canal o el contrato de la API |
| Aplicación | Orquestar un caso de uso, transacciones | Cambia el flujo de un proceso |
| Dominio | Reglas y conceptos del negocio | Cambia el negocio |
| Infraestructura | Base de datos, mensajería, APIs externas, archivos | Cambia la tecnología o el proveedor |
| Transversales | Registro, seguridad, métricas, transacciones | Cambian las políticas técnicas |

### Dos ejes para dividir

- **Horizontal (por tipo técnico):** capas. Todo lo web junto, toda la persistencia junta.
- **Vertical (por capacidad de negocio):** módulos. Todo lo de pedidos junto, todo lo de facturación junto.

Los sistemas reales combinan los dos. La pregunta de diseño es cuál manda. La experiencia acumulada favorece el eje vertical como división principal, porque los cambios del negocio llegan por funcionalidad, no por capa.

### Qué se evalúa en una arquitectura

No hay arquitecturas buenas en abstracto: hay arquitecturas adecuadas a ciertos **atributos de calidad**: facilidad de cambio, rendimiento, escalabilidad, disponibilidad, seguridad, costo, tiempo de salida. Mejorar uno suele costar otro. Arquitecturar es elegir esos compromisos de forma consciente.

## El problema, en código

```java
@RestController
public class PedidoController {

    @Autowired JdbcTemplate jdbc;

    @PostMapping("/pedidos")
    public Map<String, Object> crear(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!"ADMIN".equals(request.getHeader("X-Rol"))) throw new RuntimeException("403");   // seguridad
        long total = ((Number) body.get("cantidad")).longValue() * 40_000;                    // regla de negocio
        if (total > 1_000_000) total = total * 90 / 100;                                      // regla de negocio
        jdbc.update("INSERT INTO pedidos(cliente, total) VALUES (?, ?)", body.get("cliente"), total);  // persistencia
        log.info("Pedido creado " + body);                                                    // registro
        return Map.of("total", total, "mensaje", "Creado");                                   // presentación
    }
}
```

Cinco asuntos en un método. Consecuencias: la regla del descuento no se puede reutilizar desde un proceso por lotes ni probar sin HTTP y base de datos; cambiar de base toca el controlador; la seguridad se repite en cada endpoint.

## La misma operación, separada

```java
@RestController
@RequestMapping("/pedidos")
class PedidoController {                                  // presentación
    private final CrearPedido crearPedido;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")                     // transversal, declarativo
    PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest request) {
        return PedidoResponse.de(crearPedido.ejecutar(request.aComando()));
    }
}

@Service
class CrearPedido {                                       // aplicación
    private final RepositorioPedidos repositorio;

    @Transactional
    Pedido ejecutar(CrearPedidoComando comando) {
        Pedido pedido = Pedido.nuevo(comando.cliente(), comando.cantidad());
        return repositorio.guardar(pedido);
    }
}

class Pedido {                                            // dominio: aquí vive la regla
    static Pedido nuevo(String cliente, int cantidad) { ... }
    private long calcularTotalConDescuentoPorVolumen() { ... }
}
```

## Trade-offs

- A favor: cada asunto cambia sin arrastrar a los demás; las reglas se prueban sin infraestructura; varias personas trabajan en paralelo; se puede razonar sobre una parte sin cargar todo en la cabeza.
- En contra: más archivos y más saltos para seguir una operación; hay que mapear datos entre las partes; una separación mal elegida obliga a tocar todas las partes en cada cambio.

## Cuándo no hace falta tanto

Un script, un prototipo, una herramienta interna de una pantalla. La separación se paga con estructura; si el sistema es pequeño y de vida corta, un diseño plano es legítimo.

## Taller

Toma un endpoint real de un proyecto tuyo (o escribe uno como el del ejemplo).

1. Colorea o etiqueta cada línea según el asunto que atiende.
2. Reescríbelo separando los asuntos. No hace falta que compile con infraestructura real.
3. Para cada uno de estos cambios, indica qué partes tocas antes y después: cambiar la regla de descuento; exponer la operación también por mensajería; cambiar de base de datos; cambiar el formato de la respuesta.
4. Lista los atributos de calidad más importantes del sistema donde vive ese endpoint, en orden, y justifica el orden.

Rama `taller/n5-01-separacion`.

## Preguntas senior

1. Define arquitectura sin usar las palabras "capas", "componentes" ni "diagramas".
2. ¿Qué diferencia hay entre una decisión de arquitectura y una de diseño?
3. ¿Por qué dividir por capacidad de negocio suele envejecer mejor que dividir por tipo técnico?
4. Nombra dos atributos de calidad que entren en conflicto y cómo decidirías entre ellos.
5. ¿Cómo detectas, mirando el historial de cambios, que la separación de un sistema está mal elegida?

Siguiente: [02 — Arquitectura en capas](../02-arquitectura-en-capas/).
