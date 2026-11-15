# 03 — MVC

## Idea sencilla

En un restaurante, la cocina prepara la comida (modelo), el plato servido es lo que el cliente ve (vista) y el mesero recibe el pedido, lo lleva a la cocina y trae el resultado (controlador). La cocina no sabe en qué mesa se servirá; el plato no sabe cocinar; el mesero no cocina ni decide la receta.

## Explicación técnica

Modelo-Vista-Controlador es un patrón para la **capa de presentación**: separa el estado y las reglas (modelo), su representación (vista) y el manejo de la entrada del usuario (controlador).

| Parte | Responsabilidad | No debe |
|---|---|---|
| Modelo | Datos y reglas del negocio | Saber cómo se muestra |
| Vista | Representar el modelo | Contener reglas |
| Controlador | Traducir la entrada en operaciones sobre el modelo y elegir la respuesta | Contener reglas de negocio |

**MVC no es una arquitectura completa.** Organiza la interacción con el usuario; no dice nada sobre cómo estructurar el negocio o la persistencia. "Nuestro sistema es MVC" describe solo el borde.

### Variantes que conviene distinguir

- **MVC clásico (Smalltalk, escritorio):** la vista observa al modelo y se actualiza sola.
- **MVC web (Spring MVC, Rails):** petición y respuesta. El controlador recibe la petición, invoca al modelo y entrega una vista o datos. No hay observación.
- **MVP y MVVM:** variantes para interfaces ricas; en MVVM la vista se enlaza a un modelo de vista (Angular, frameworks móviles).

### En Spring MVC

```
Petición → DispatcherServlet → HandlerMapping → Controlador → Servicio (modelo)
                                                     ↓
Respuesta ← HttpMessageConverter (JSON) o ViewResolver (HTML) ← resultado
```

`DispatcherServlet` es un *front controller*: un punto de entrada único que reparte. En una API REST la "vista" es la representación JSON; el patrón sigue ahí, con otro tipo de vista.

```java
@RestController
@RequestMapping("/pedidos")
class PedidoController {

    private final ConsultaPedidos consulta;

    @GetMapping("/{id}")
    ResponseEntity<PedidoResponse> buscar(@PathVariable Long id) {
        return consulta.buscar(id)
                .map(PedidoResponse::de)            // la "vista": cómo se representa
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

## El error más común: el controlador gordo

```java
@PostMapping("/pedidos/{id}/cancelar")
public ResponseEntity<?> cancelar(@PathVariable Long id) {
    Pedido pedido = repositorio.findById(id).orElseThrow();
    if (pedido.getEstado().equals("ENVIADO")) {                     // regla de negocio
        return ResponseEntity.badRequest().body("Ya fue enviado");
    }
    if (ChronoUnit.HOURS.between(pedido.getFecha(), LocalDateTime.now()) > 24) {   // regla de negocio
        return ResponseEntity.badRequest().body("Pasaron más de 24 horas");
    }
    pedido.setEstado("CANCELADO");
    repositorio.save(pedido);
    correo.enviar(pedido.getCliente(), "Pedido cancelado");
    return ResponseEntity.ok().build();
}
```

Las reglas de cancelación quedaron atrapadas en HTTP. No se pueden invocar desde otro canal ni probar sin levantar la capa web.

**El error opuesto: el modelo confundido con la entidad JPA.** "Modelo" en MVC es todo el negocio, no la clase con `@Entity`. Reducirlo a la entidad deja las reglas sin hogar, y terminan en el controlador o en un servicio dios.

## Controlador bien hecho

Un controlador hace cuatro cosas y nada más:

1. Recibe y valida el **formato** de la entrada (`@Valid`).
2. Convierte la entrada al lenguaje de la aplicación.
3. Delega en un caso de uso.
4. Convierte el resultado (o el error, vía `@RestControllerAdvice`) a HTTP.

```java
@PostMapping("/{id}/cancelar")
@ResponseStatus(HttpStatus.NO_CONTENT)
void cancelar(@PathVariable Long id) {
    cancelarPedido.ejecutar(id);
}
```

## Trade-offs

- A favor: separa interfaz y negocio; permite varias vistas para el mismo modelo (web, móvil, API); la presentación se prueba aparte.
- En contra: no orienta sobre el resto del sistema; el controlador es un imán de lógica; en interfaces muy interactivas el MVC clásico se queda corto y se prefieren MVVM o arquitecturas de estado unidireccional.

## Cuándo no

MVC no se "elige" en el backend: viene con el framework. Lo que se decide es no dejar que su vocabulario sustituya al diseño del negocio.

## Taller

1. Toma el controlador gordo del ejemplo y reescríbelo: controlador delgado, caso de uso, y las dos reglas de cancelación dentro de `Pedido`.
2. Escribe una prueba de las reglas que no use nada de Spring y otra `@WebMvcTest` que verifique solo el código HTTP.
3. Expón la misma cancelación por un segundo canal (un consumidor de mensajes simulado). ¿Qué código compartieron los dos canales?
4. En una aplicación tuya, encuentra el controlador con más líneas y clasifica cada bloque: entrada, negocio, persistencia, salida.
5. Explica la diferencia entre MVC, MVP y MVVM, e indica cuál usa el framework de frontend que conoces.

Rama `taller/n5-03-mvc`.

## Preguntas senior

1. ¿Por qué MVC no es una arquitectura de aplicación completa?
2. ¿Qué es un *front controller* y qué ventaja da centralizar la entrada?
3. ¿Validar en el controlador o en el dominio? Distingue validación de formato y de negocio.
4. ¿Dónde debe hacerse la conversión entre DTO y dominio y por qué?
5. Si tu API devuelve entidades JPA directamente, enumera los riesgos.

Siguiente: [04 — Inversión de dependencias aplicada a arquitectura](../04-inversion-de-dependencias-en-arquitectura/).
