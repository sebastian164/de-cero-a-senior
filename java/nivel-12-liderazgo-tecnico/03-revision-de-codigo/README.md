# 03 — Revisión de código

## Idea

Un texto importante se revisa antes de publicarse: quien lo escribió ya no ve sus propios errores. La revisión de código cumple esa función y otra más valiosa: es el momento en que el conocimiento del sistema se reparte entre el equipo.

## Lo esencial

### Para qué sirve

1. Encontrar defectos y problemas de diseño antes de producción.
2. **Repartir el conocimiento:** que nadie sea el único que entiende una parte.
3. Mantener la coherencia del código.
4. Enseñar y aprender, en ambas direcciones.

No sirve para demostrar quién sabe más, ni para imponer preferencias personales.

### Qué revisar, por orden de importancia

| Prioridad | Qué | Pregunta |
|---|---|---|
| 1 | Corrección | ¿Hace lo que debe? ¿Casos límite, errores, concurrencia? |
| 2 | Diseño | ¿Está en el lugar correcto? ¿Las responsabilidades son claras? |
| 3 | Pruebas | ¿Verifican el comportamiento? ¿Fallarían si el código estuviera mal? |
| 4 | Seguridad | ¿Entradas validadas, autorización, datos sensibles? |
| 5 | Legibilidad | ¿Lo entendería alguien nuevo? ¿Nombres? |
| 6 | Operación | ¿Registros, métricas, migraciones, reversa? |
| 7 | Estilo | Lo decide una herramienta automática, no una persona |

Formato, orden de importaciones y convenciones de nombres se automatizan (formateador, *linter*). Discutirlos en una revisión es desperdiciar la atención humana.

### Cómo dar retroalimentación

- **Comenta el código, no a la persona:** "este método hace dos cosas" en lugar de "hiciste esto mal".
- **Explica el porqué** y, si puedes, propón la alternativa.
- **Pregunta antes de afirmar:** "¿qué pasa si la lista llega vacía?".
- **Distingue lo bloqueante de lo opcional.** Una convención útil: prefijar con `bloqueante:`, `sugerencia:`, `pregunta:` o `detalle:`.
- **Reconoce lo bueno.** También se aprende de lo que está bien hecho.
- **Si la discusión se alarga, habla.** Cinco minutos de llamada reemplazan veinte comentarios.

### Cómo pedir una revisión

- **Cambios pequeños.** Hasta unas 300 o 400 líneas se revisan bien; más allá, la atención cae y se aprueba sin leer.
- **Una sola intención por cambio:** no mezclar refactor y funcionalidad.
- **Una descripción que explique el porqué,** cómo probarlo y qué mirar con cuidado.
- **Revísalo tú primero.**
- **No lo tomes como algo personal.** Se revisa el código, no tu valor.

### Como equipo

- **Respuesta rápida:** una revisión que tarda dos días bloquea y fomenta cambios enormes. Un día hábil como máximo es un buen acuerdo.
- **Acordar las convenciones por escrito,** para no rediscutirlas.
- **Aprobar con comentarios menores** en lugar de exigir otra vuelta por un detalle.
- **Todos revisan,** incluidos los más nuevos: preguntan lo que los demás dan por sabido.

## Ejemplo

Comentario poco útil:

> Esto está mal. Usa un Map.

Comentario útil:

> **sugerencia:** aquí se recorre `clientes` dentro del bucle de `pedidos`, que es O(n × m). Con los volúmenes del reporte mensual (unos 50.000 pedidos) puede tardar bastante. ¿Qué te parece construir antes un `Map<Long, Cliente>` por identificador? Si prefieres dejarlo así por ahora, agreguemos al menos una prueba con volumen.

El segundo dice qué pasa, por qué importa, propone una salida y deja margen.

## Errores comunes

- Aprobar sin leer ("LGTM" a un cambio de 2.000 líneas).
- Bloquear por preferencias de estilo.
- Convertir la revisión en un examen.
- Comentarios sarcásticos o imperativos sin explicación.
- Revisar solo lo superficial y no ver el problema de diseño.
- Rediseñar la solución completa en los comentarios, cuando esa conversación debió ocurrir antes de programar.
- Dejar los *pull requests* envejecer.
- Que solo una persona pueda aprobar.

## Taller

1. Revisa tres *pull requests* de un proyecto de código abierto que conozcas (sin publicar los comentarios). Escribe tus observaciones siguiendo el orden de prioridades y la convención de prefijos.
2. Toma cinco comentarios de revisión reales, tuyos o recibidos, y reescríbelos para que sean más útiles.
3. Divide un cambio grande tuyo en tres o cuatro más pequeños y revisables. Explica el orden.
4. Escribe una plantilla de descripción de *pull request* para tu equipo.
5. Redacta una guía de revisión de una página: qué se revisa, qué se automatiza, tiempos de respuesta y cómo se resuelven los desacuerdos.
6. En las entregas de este curso, aplica todo lo anterior a tus propios *pull requests*.

Rama `taller/n12-03-revision`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué buscas primero al revisar un cambio?
2. ¿Cómo das una retroalimentación difícil a alguien con más experiencia que tú?
3. ¿Qué haces cuando no estás de acuerdo con un comentario sobre tu código?
4. ¿Cómo evitas que las revisiones se vuelvan un cuello de botella?
5. ¿Qué automatizarías para que las revisiones se concentren en lo importante?
