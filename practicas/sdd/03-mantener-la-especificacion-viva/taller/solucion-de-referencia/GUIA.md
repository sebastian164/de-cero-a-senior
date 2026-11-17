# Guía del equipo: cómo usamos la especificación (SDD)

## Para qué cambios

Usamos especificación **cuando el cambio altera lo que otro ve o usa**: una ruta o su respuesta, un formato de datos, una regla de negocio, o cuando lo que se pide cabe en una frase ambigua. Regla práctica: más de unos tres criterios, o un cliente que pudiera notarlo.

**No la usamos** para: un refactor sin efecto externo, un cambio de texto o de estilo, una constante sin efecto visible, ni para explorar una idea (se explora con código desechable y, si se queda, se escribe la especificación). La urgencia tiene su propio camino (abajo).

## Secciones obligatorias

Propósito, criterios de aceptación (en notación EARS, cada uno comprobable), casos límite, **fuera de alcance** (al menos tres cosas que alguien podría querer añadir), restricciones y preguntas abiertas (cada una con respuesta por defecto). Las decisiones de implementación van al plan, no a la especificación.

## Qué se revisa en cada puerta

| Puerta | Quien revisa mira |
|---|---|
| Especificación | ¿Dos personas escribirían las mismas pruebas? ¿Hay un "cómo" escondido? ¿Falta algo en "fuera de alcance"? |
| Plan | ¿Cada decisión tiene razón y alternativa? ¿Se inventó algún requisito? ¿Respeta las restricciones? |
| Tareas | ¿Cada una cabe en una sentada, nombra un criterio y tiene una forma de comprobarse? ¿Queda algún criterio sin tarea? |
| Código | Se lee completo. ¿Hay algo que ningún criterio pidió? Las pruebas existían antes y están en verde |

## Cómo se enlazan criterios y pruebas

Cada criterio se numera y cada escenario de prueba lleva la etiqueta `@criterio-N`. Un script (`deriva.sh`) falla si un criterio no tiene prueba o si un número de la especificación (un límite, una longitud) difiere del código. Un criterio sin prueba automática lleva escrita la razón. Un cambio de criterio y de prueba va en el mismo pull request, en este orden: documento de cambio, especificación, pruebas en rojo, código.

## En una urgencia

Se arregla primero, con una prueba que reproduzca el error. La urgencia **no se da por cerrada** hasta que el mismo día o el siguiente: la especificación refleja el comportamiento nuevo, hay un documento de cambio retroactivo, y otra persona revisó el arreglo. Si no se hace, la especificación miente.

## Quién arregla una especificación rota

Quien cambió el código. Quien revisa el pull request pregunta primero: "¿qué criterio cambia esto?".
