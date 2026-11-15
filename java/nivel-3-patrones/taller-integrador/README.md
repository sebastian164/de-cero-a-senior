# Taller integrador de patrones

El objetivo no es usar muchos patrones. Es reconocer qué problemas hay y elegir la solución más simple para cada uno, que a veces será un patrón y a veces no.

## Mapa rápido: del síntoma al patrón

| Síntoma | Candidatos |
|---|---|
| `if`/`switch` que elige un algoritmo | Strategy, lambda, `enum` |
| `if`/`switch` por estado en muchos métodos | State, `enum` con tabla |
| `new` de clases concretas repartido | Factory Method, inyección |
| Constructor con muchos parámetros | Builder, agrupar en objetos |
| API ajena que no encaja | Adapter |
| Secuencia de llamadas repetida | Facade |
| Agregar comportamiento combinable | Decorator |
| Controlar acceso, caché, transacciones | Proxy, AOP |
| "Cuando pase X, avisar a varios" | Observer, eventos |
| Mismo esqueleto, pasos distintos | Template Method, funciones |
| Etapas que pueden cortar | Chain of Responsibility |
| Estructura en árbol | Composite |
| Dos dimensiones de variación | Bridge, composición |
| Acción que se guarda o se deshace | Command, Memento |
| Muchos objetos que se coordinan | Mediator |
| Operaciones nuevas sobre tipos fijos | Visitor, `sealed` + `switch` |
| Reglas como datos | Interpreter, lenguaje de expresiones |

## El sistema

Una plataforma de cursos en línea:

- Hay cursos compuestos por módulos, y módulos compuestos por lecciones (video, lectura, examen).
- El precio de un curso depende de promociones vigentes: descuento por lanzamiento, por paquete, por cupón. Se combinan según reglas.
- Una inscripción pasa por estados: pendiente de pago, activa, suspendida, finalizada, reembolsada.
- El pago se hace con pasarelas externas distintas según el país, cada una con su propia API.
- Al completar un curso se emite un certificado, se avisa al estudiante, se actualizan estadísticas y se desbloquean recomendaciones.
- Antes de entregar una lección se verifica: sesión válida, inscripción activa, lección desbloqueada y límite de dispositivos.
- El progreso de un examen se puede guardar y retomar.

## Parte 1 — Análisis antes de programar

En `ANALISIS.md`, para cada requisito:

1. Qué problema de diseño contiene (usa la tabla).
2. Qué patrón o alternativa simple elegirías y por qué.
3. Qué descartaste y por qué.

Como mínimo dos requisitos deben resolverse **sin** patrón, justificando por qué no hace falta.

## Parte 2 — Implementación

Implementa el sistema en Java puro, simulando la infraestructura. Un `Main` debe recorrer un escenario completo: crear un curso, inscribir a un estudiante, pagar, consumir lecciones, completar y certificar.

## Parte 3 — Los cambios

Aplica y documenta qué tocaste:

1. Nueva pasarela de pago.
2. Nuevo tipo de lección: taller en vivo, con cupo.
3. Nueva promoción: 2×1 en cursos de la misma categoría.
4. Nuevo estado: inscripción en periodo de prueba de siete días.
5. Nueva consecuencia al completar: publicar el logro en el perfil público, solo si el estudiante lo autorizó.

## Parte 4 — Autocrítica

1. Señala el patrón de tu solución que menos se justifica. ¿Lo quitarías?
2. ¿Qué cambio de la Parte 3 fue el más costoso y qué revela sobre tu diseño?
3. Si otra persona leyera tu código sin conocer patrones, ¿qué parte le resultaría más difícil de seguir?

## Entrega

Rama `taller/n3-integrador`. Su revisión cierra el Nivel 3.

Siguiente: [Nivel 4 — Clean Code, refactorización y testing](../../nivel-4-clean-code-testing/).
