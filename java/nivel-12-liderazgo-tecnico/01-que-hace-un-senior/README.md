# 01 — Qué hace realmente un senior

## Idea

Un aprendiz de carpintería sabe usar las herramientas. Un maestro sabe qué mueble conviene construir, cuánto va a costar, qué puede salir mal y cómo enseñar al aprendiz. La diferencia no está en la velocidad con el serrucho.

## Lo esencial

La antigüedad no hace senior a nadie. Lo que cambia entre niveles es el **alcance del impacto** y el **grado de ambigüedad** que la persona puede manejar sin ayuda.

| | Junior | Intermedio | Senior | Líder técnico / Staff |
|---|---|---|---|---|
| Recibe | Tareas definidas | Funcionalidades | Problemas | Objetivos del negocio |
| Entrega | Código que funciona | Funcionalidades completas | Soluciones mantenibles y operables | Dirección técnica |
| Ambigüedad | Necesita guía | Pregunta lo necesario | La resuelve | La reduce para otros |
| Impacto | Su tarea | Su funcionalidad | Su equipo y su sistema | Varios equipos |
| Ante un problema | Pide ayuda | Propone una solución | Compara alternativas y sus costos | Decide qué problemas vale la pena resolver |

### Lo que distingue a un senior

1. **Criterio.** Sabe cuándo aplicar una técnica y cuándo no. Todo este curso insiste en "cuándo no usarlo" por esa razón.
2. **Piensa en compromisos.** Su respuesta habitual es "depende, y depende de esto".
3. **Se hace responsable del resultado, no de la tarea.** No termina cuando se fusiona el código, sino cuando funciona en producción y resuelve el problema.
4. **Simplifica.** La solución compleja es fácil; encontrar la simple exige entender de verdad.
5. **Multiplica.** Hace mejores a los demás: revisa, enseña, documenta, desbloquea.
6. **Comunica.** Explica lo técnico a quien no es técnico, y convierte lo que el negocio necesita en decisiones técnicas.
7. **Reduce el riesgo.** Entrega por incrementos, valida supuestos temprano y sabe revertir.
8. **Dice que no** con argumentos, y propone una alternativa.
9. **Conoce sus límites.** "No lo sé, lo averiguo" es una respuesta de senior.

### El rol de líder técnico

No es el mejor programador del equipo con más reuniones. Sus responsabilidades:

- Mantener la dirección técnica coherente.
- Que las decisiones se tomen, se registren y se entiendan.
- Desbloquear al equipo.
- Cuidar la calidad y la salud del sistema a largo plazo.
- Hacer de puente con producto y con otros equipos.
- Desarrollar a las personas.

Programa menos de lo que quisiera, y esa es la parte difícil: su resultado se mide por lo que logra el equipo.

### Trampas habituales

- **El héroe:** resuelve todo él mismo. El equipo no crece y él se vuelve un cuello de botella.
- **El arquitecto de torre de marfil:** decide sin tocar el código ni vivir las consecuencias.
- **El perfeccionista:** bloquea entregas por detalles de estilo.
- **El experto en una herramienta:** todo problema se parece a lo que ya domina.
- **El que confunde complejidad con calidad.**

## Ejemplo: la misma petición en tres niveles

Petición: "necesitamos que el reporte de ventas cargue más rápido".

- **Junior:** agrega una caché al endpoint.
- **Intermedio:** analiza la consulta, encuentra que falta un índice y lo agrega.
- **Senior:** pregunta quién usa el reporte, con qué frecuencia y qué tan actualizado debe estar. Mide. Descubre que el 90 % de las consultas son del mismo rango de fechas. Propone tres opciones (índice, vista precalculada, exportación programada) con el costo y el riesgo de cada una, recomienda una, la entrega con una métrica que demuestre la mejora y deja documentado por qué.

## Taller

1. **Autoevaluación.** Con la tabla de niveles, ubícate en cada fila con un ejemplo concreto y reciente. Identifica las dos filas donde estás más lejos de senior.
2. **Caso.** Toma una tarea real que hayas resuelto y reescribe cómo la habría abordado un senior: qué preguntas, qué alternativas, qué métricas.
3. **Compromisos.** Elige una decisión técnica de tu proyecto actual y escribe sus costos, los que nadie mencionó cuando se tomó.
4. **Multiplicar.** Lista tres cosas que solo tú sabes en tu equipo y cómo dejarían de depender de ti.
5. **Plan.** Define tres objetivos de crecimiento para los próximos seis meses, cada uno con una evidencia observable.

Rama `taller/n12-01-senior`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué diferencia a un senior de un intermedio con muchos años?
2. Cuéntame de una vez en que elegiste la solución más simple en contra de la más elegante.
3. ¿Cómo le dices que no a un requerimiento?
4. ¿Cómo mides tu impacto más allá del código que escribes?
5. ¿Qué haces cuando no sabes algo?
6. ¿Qué error tuyo cambió tu forma de trabajar?
