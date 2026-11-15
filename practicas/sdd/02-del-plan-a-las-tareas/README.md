# 02 — De la especificación al plan y a las tareas

## Idea

Una receta de restaurante no es "haz un risotto". Es la lista de ingredientes, el orden de los pasos y lo que se prepara antes del servicio. El chef la escribe una vez y cualquier cocinero de la línea la ejecuta igual un martes que un sábado. Y el chef **prueba el plato** antes de que salga: tener la receta no lo exime de eso.

## Lo esencial

### Las fases

| Fase | Pregunta | Produce | Quién decide |
|---|---|---|---|
| **Especificar** | ¿Qué y para qué? | La especificación | Tú, con el negocio |
| **Aclarar** | ¿Qué quedó ambiguo? | Preguntas respondidas | Tú |
| **Planear** | ¿Cómo, con esta tecnología y estas restricciones? | El plan técnico | Tú, con propuestas del asistente |
| **Dividir** | ¿En qué pasos pequeños y comprobables? | La lista de tareas | El asistente propone, tú revisas |
| **Implementar** | — | Código y pruebas | El asistente ejecuta, tú revisas |
| **Verificar** | ¿Hace lo que dice la especificación? | Evidencia | Tú |

Entre fase y fase hay una **puerta**: una persona lee, corrige y aprueba. Quitar las puertas es lo que convierte esto en generar código a ciegas, solo que con más archivos.

### El plan

Traduce el qué en un cómo: qué archivos se tocan, qué estructuras de datos, qué decisiones técnicas y **por qué**, qué riesgos. Aquí sí van las bibliotecas y los nombres. Un plan puede rehacerse entero sin tocar la especificación.

### Las tareas

| Una buena tarea | Una mala tarea |
|---|---|
| Se hace y se revisa en una sentada | "Implementar la búsqueda" |
| Dice qué criterio de la especificación cumple | No se sabe para qué sirve |
| Tiene una forma de comprobar que está hecha | "Queda listo cuando funcione" |
| Deja el sistema funcionando al terminar | Rompe todo hasta que llegue la tarea siete |

La relación entre tareas y criterios se llama **trazabilidad**: cada criterio tiene al menos una tarea y una prueba; cada tarea apunta a un criterio. Una tarea que no apunta a ninguno es trabajo que nadie pidió.

### Las pruebas van primero

El orden natural dentro de cada tarea es el de TDD: la prueba que expresa el criterio, y luego el código. Así, lo que el asistente genera se comprueba contra algo que **tú** definiste, no contra pruebas que escribió él para que su propio código pase.

### Principios del proyecto

Las reglas que valen para todo, escritas una vez: estilo, qué se prueba y cómo, qué no se permite. Las herramientas lo llaman constitución o archivo de reglas. Evita repetir en cada especificación lo que es cierto siempre.

### Herramientas

| Enfoque | Cómo es |
|---|---|
| Kit de órdenes sobre tu asistente (por ejemplo, GitHub Spec Kit) | Añade plantillas y órdenes para cada fase; funciona con muchos asistentes; todo son archivos de texto en tu repositorio |
| Entorno construido alrededor del flujo (por ejemplo, Kiro) | Requisitos, diseño y tareas como objetos del propio editor |
| A mano | Tres archivos de texto y disciplina |

Los nombres de las órdenes y de los archivos cambian con cada herramienta y cada versión. Lo que no cambia es la secuencia y las puertas. Aprende eso; la herramienta se aprende en una tarde.

## Ejemplo

Continuación de la búsqueda de notas de la sesión anterior.

### Antes

Se pega la especificación en el asistente con un "impleméntalo". Vuelven cuatrocientas líneas: la búsqueda, una clase nueva de utilidades, un índice en memoria "para rendimiento", paginación "por buenas prácticas" y diez pruebas que pasan. Se revisa por encima y se fusiona.

### Análisis

| Problema | Consecuencia |
|---|---|
| No hubo plan revisado | El índice en memoria fue decisión del asistente. Nadie evaluó si hacía falta ni qué pasa cuando se guarda una nota |
| La paginación estaba fuera de alcance | Cambió la forma de la respuesta y rompió a los clientes existentes: justo lo que la restricción prohibía |
| Las pruebas las escribió quien escribió el código | Comprueban lo que el código hace, no lo que la especificación pide. El criterio de las tildes no tiene prueba |
| Cuatrocientas líneas en una revisión | Nadie las leyó de verdad |

### Después

<!-- file: ejemplo/plan.md -->
```markdown
# Plan: búsqueda de notas

Especificación: `especificacion.md`

## Decisiones

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| El filtro se aplica en `GET /api/notas`, leyendo `q` de la consulta | Criterios 1 y 9: misma ruta y misma forma de respuesta | Una ruta `/api/notas/buscar`: duplicaría la forma de respuesta |
| Se filtra en memoria sobre las líneas que ya se leen del archivo | Restricción: sin dependencias; hoy ya se lee el archivo entero en cada petición | Un índice: complejidad sin un problema medido |
| Normalizar es una función pura `normalizar(texto)`: minúsculas y sin marcas diacríticas | Criterio 3; se prueba sin levantar el servidor | Expresiones regulares con banderas: el texto buscado no es un patrón (caso límite 1) |
| La comparación es `normalizar(nota).contains(normalizar(q))` | Criterio 4 y caso límite 1: texto literal | — |

## Archivos que cambian

- `Servidor.java`: el manejador de `/api/notas` y dos funciones nuevas.

## Riesgos

- El parámetro llega codificado en la URL: hay que decodificarlo antes de normalizar.
- La lectura del archivo completo en cada búsqueda no escala. Está aceptado por la restricción y anotado en preguntas abiertas.

## Lo que este plan no hace

Nada de "fuera de alcance". En particular, no cambia la forma de la respuesta.
```

<!-- file: ejemplo/tareas.md -->
```markdown
# Tareas: búsqueda de notas

Cada tarea: primero la prueba, después el código. Al terminar cada una, todo lo anterior sigue pasando.

| # | Tarea | Criterios | Cómo se comprueba |
|---|---|---|---|
| 1 | Función pura `normalizar`: minúsculas, sin tildes, sin espacios en los extremos | 3, caso límite 2 | Pruebas unitarias: "Café" y "CAFÉ" dan "cafe"; " pan " da "pan" |
| 2 | Función pura `coincide(nota, consulta)` | 3, 4, caso límite 1 | Pruebas unitarias: parcial, tildes, y `a.b` no coincide con "axb" |
| 3 | Leer `q` de la consulta, decodificado | 2 | Prueba de API: `?q=caf%C3%A9` encuentra "café" |
| 4 | Sin `q`, vacío o solo espacios: respuesta idéntica a la actual | 1, 7 | Las pruebas de API existentes pasan sin tocarlas, más dos nuevas |
| 5 | Filtrar conservando el orden | 2, 5 | Prueba de API: tres notas, dos coinciden, llegan en el orden guardado |
| 6 | Sin coincidencias: 200 y lista vacía | 6, caso límite 3 | Prueba de API |
| 7 | `q` de más de 100 caracteres: 400 con cuerpo de error | 8 | Prueba de API con 100 y con 101 caracteres |
| 8 | Verificación final contra la especificación | Todos | Tabla criterio por criterio con su prueba |

## Trazabilidad

| Criterio | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|
| Tareas | 4 | 3, 5 | 1, 2 | 2 | 5 | 6 | 4 | 7 | 4, 5, 6 |
```

- Las decisiones técnicas están a la vista, con su razón, y se revisan **antes** de que exista código.
- Ocho tareas pequeñas, cada una con su comprobación y sus criterios.
- La tabla de trazabilidad muestra que ningún criterio quedó sin tarea.
- La tarea 4 usa las pruebas existentes como red: es la restricción "ningún cliente debe notar el cambio" convertida en algo ejecutable.

## Errores comunes

- Saltarse la revisión del plan porque "se ve razonable".
- Tareas enormes.
- Dejar que el asistente escriba las pruebas después del código y darlas por buenas.
- Aceptar código que nadie leyó porque las pruebas pasan.
- No comprobar al final criterio por criterio.
- Aceptar extras que nadie pidió.
- Tres archivos de mil líneas generados para un cambio de veinte.
- Corregir el código a mano sin actualizar la especificación ni el plan.

## Taller

**Objetivo:** llevar tu especificación de etiquetas hasta código que funciona, con puertas de revisión y trazabilidad.

Elige cómo trabajar: una herramienta de SDD, tu asistente habitual con archivos de texto, o sin IA. Anota cuál y qué versión. Si usas una herramienta, sigue **su** documentación para instalarla.

1. Escribe los principios del proyecto en un archivo corto: cómo se prueba, qué no se permite (por ejemplo, dependencias nuevas), cómo se nombran las cosas.
2. **Aclarar.** Pídele al asistente, o a un compañero, que haga preguntas sobre tu especificación antes de proponer nada. Responde y actualiza la especificación. ¿Cuántas eran dudas reales?
3. **Planear.** Genera o escribe el plan. Revísalo como revisarías un diseño: ¿hay decisiones sin razón?, ¿se inventó requisitos?, ¿respeta las restricciones? Anota cada corrección que hiciste.
4. **Dividir.** Genera o escribe las tareas. Aplica la tabla de "buena tarea" a cada una y corrige. Construye la tabla de trazabilidad.
5. **Las pruebas, tuyas.** Antes de implementar, escribe tú las pruebas de aceptación de cada criterio (los escenarios de Karate de la sesión anterior son un buen comienzo). Deben fallar.
6. **Implementar**, tarea por tarea. Tras cada una: ejecuta las pruebas, lee el cambio completo y haz un commit que nombre la tarea y el criterio.
7. Lleva la cuenta: cuántas veces el resultado se desvió de la especificación, cuántas añadió algo no pedido, cuántas tuviste que corregir el plan.
8. **Verificar.** Construye la tabla final: cada criterio, su prueba y su resultado. Añade una revisión manual de los casos límite.
9. Haz el experimento de control: en una rama aparte, pide la misma funcionalidad con una sola frase y sin especificación. Compara las dos ramas con tus pruebas de aceptación y con la lista de "fuera de alcance".
10. ¿Cuánto tiempo dedicaste a cada fase? ¿Dónde se habría descubierto más barato cada error que encontraste?

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Hay evidencia de revisión en cada puerta | Las correcciones anotadas en los pasos 2 a 4 |
| Cada criterio tiene tarea y prueba | Tabla de trazabilidad |
| Las pruebas de aceptación existían antes que el código | El historial |
| Nada de "fuera de alcance" está en el código | Lectura del cambio |
| El experimento de control está documentado | Paso 9 |

**Entrega:** rama `taller/sdd-02`, con especificación, plan, tareas y código en `taller/solucion/`, y `ANALISIS.md`.

## En el trabajo real

El valor no está en los archivos sino en **cuándo** aparece cada desacuerdo. Un malentendido que se descubre leyendo un plan de una página cuesta diez minutos. El mismo malentendido descubierto revisando cuatrocientas líneas generadas, o peor, en producción, cuesta días. Las puertas existen para mover los errores hacia la izquierda.

## Preguntas senior

1. ¿Para qué sirven las puertas de revisión y qué pasa si se eliminan?
2. ¿Qué hace buena a una tarea?
3. ¿Por qué las pruebas de aceptación no deberían venir de quien generó el código?
4. ¿Qué es la trazabilidad y qué problemas detecta?
5. ¿Cómo revisas código generado por una IA?
