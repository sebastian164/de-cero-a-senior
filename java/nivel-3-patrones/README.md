# Nivel 3 — Patrones de diseño

Requisito: Niveles 1 y 2 aprobados. Sin polimorfismo, composición y SOLID claros, los patrones se convierten en recetas que se aplican donde no hacen falta.

## Qué es un patrón y qué no es

Un patrón de diseño es una solución con nombre a un problema de diseño que se repite. Su valor está en dos cosas: en que alguien ya pagó el costo de descubrirla, y en que el nombre permite comunicar una idea completa en una palabra ("pon un Adapter ahí").

Un patrón **no** es una meta. Nadie gana nada por tener más patrones. Cada uno agrega clases e indirección, y solo se justifica cuando el problema que resuelve está presente.

## Cómo estudiar este nivel

El objetivo no es memorizar 23 diagramas. Es entrenar el ojo para reconocer el **problema**. Por eso cada lección empieza por el problema y las señales, y solo después muestra la solución.

Tres preguntas ante cualquier diseño:

1. ¿Qué es lo que varía aquí?
2. ¿Quién necesita no enterarse de esa variación?
3. ¿Cuál es la forma más simple de separarlos?

Casi todos los patrones son respuestas distintas a esas tres preguntas, usando composición y polimorfismo.

## Las tres familias

| Familia | Pregunta que responden |
|---|---|
| Creacionales | ¿Cómo se crean los objetos sin atar el código a clases concretas? |
| Estructurales | ¿Cómo se combinan objetos y clases en estructuras mayores? |
| De comportamiento | ¿Cómo se reparten las responsabilidades y la comunicación entre objetos? |

## Orden de estudio

### Primera vuelta: los que aparecen todas las semanas en Java empresarial

| # | Patrón | Familia | Dónde lo ves en Spring |
|---|---|---|---|
| 1 | [Strategy](comportamiento/strategy/) | Comportamiento | Inyección de implementaciones de una interfaz |
| 2 | [Factory Method](creacionales/factory-method/) | Creacional | `FactoryBean`, métodos `@Bean` |
| 3 | [Builder](creacionales/builder/) | Creacional | `ResponseEntity`, `UriComponentsBuilder`, Lombok `@Builder` |
| 4 | [Adapter](estructurales/adapter/) | Estructural | `HandlerAdapter`, adaptadores hexagonales |
| 5 | [Facade](estructurales/facade/) | Estructural | `JdbcTemplate`, servicios de aplicación |
| 6 | [Decorator](estructurales/decorator/) | Estructural | Envoltorios de `HttpServletRequest`, streams de E/S |
| 7 | [Proxy](estructurales/proxy/) | Estructural | `@Transactional`, `@Cacheable`, AOP |
| 8 | [Observer](comportamiento/observer/) | Comportamiento | `ApplicationEventPublisher`, `@EventListener` |
| 9 | [Template Method](comportamiento/template-method/) | Comportamiento | `JdbcTemplate`, `OncePerRequestFilter` |
| 10 | [Chain of Responsibility](comportamiento/chain-of-responsibility/) | Comportamiento | Cadena de filtros de Spring Security |
| 11 | [Singleton](creacionales/singleton/) | Creacional | Alcance por defecto de los beans |

### Segunda vuelta: menos frecuentes, pero hay que reconocerlos

| # | Patrón | Familia |
|---|---|---|
| 12 | [Abstract Factory](creacionales/abstract-factory/) | Creacional |
| 13 | [Prototype](creacionales/prototype/) | Creacional |
| 14 | [Composite](estructurales/composite/) | Estructural |
| 15 | [Bridge](estructurales/bridge/) | Estructural |
| 16 | [Flyweight](estructurales/flyweight/) | Estructural |
| 17 | [Command](comportamiento/command/) | Comportamiento |
| 18 | [State](comportamiento/state/) | Comportamiento |
| 19 | [Iterator](comportamiento/iterator/) | Comportamiento |
| 20 | [Mediator](comportamiento/mediator/) | Comportamiento |
| 21 | [Memento](comportamiento/memento/) | Comportamiento |
| 22 | [Visitor](comportamiento/visitor/) | Comportamiento |
| 23 | [Interpreter](comportamiento/interpreter/) | Comportamiento |

Al terminar: [Taller integrador de patrones](taller-integrador/).

## Formato de cada lección

- El problema y cómo reconocerlo.
- Código antes (fragmento) y después (programa completo: `java Main.java`).
- Alternativas más simples.
- Cómo aparece en Spring.
- Ventajas, desventajas y trade-offs.
- Errores comunes.
- Cuándo no usarlo.
- Taller, sin solución.

## Una advertencia sobre Java moderno

Varios patrones nacieron para compensar lo que los lenguajes de los años noventa no tenían. Con lambdas, `record`, `enum` e interfaces `sealed`, algunos se reducen a una línea. Cada lección lo señala: conocer el patrón clásico sirve para leer código existente; conocer la versión moderna, para no escribir de más.
