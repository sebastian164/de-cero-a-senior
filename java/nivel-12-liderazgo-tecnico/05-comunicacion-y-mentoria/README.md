# 05 — Comunicación, mentoría y defensa de una solución

## Idea

Una buena solución que nadie entiende no se adopta. Y un equipo donde solo una persona sabe es un equipo frágil. La última habilidad de un senior no es técnica: es lograr que las ideas se entiendan y que los demás crezcan.

## Lo esencial

### Comunicar según la audiencia

| Audiencia | Le interesa | Evita |
|---|---|---|
| Gerencia y negocio | Resultado, costo, riesgo, plazo | Jerga, detalles de implementación |
| Producto | Qué se puede hacer, cuándo, a cambio de qué | Decir solo "no se puede" |
| Otros desarrolladores | El porqué, las alternativas, los compromisos | Dar por sabido el contexto |
| Operaciones | Cómo se despliega, se observa y se revierte | Entregar sin instrucciones |

**Lo principal primero.** Empieza por la conclusión o la petición; el detalle va después, para quien lo necesite. Quien lee solo la primera línea debe llevarse lo esencial.

### Escribir

En equipos distribuidos, escribir bien es una ventaja enorme: lo escrito escala, no depende de la memoria y se puede buscar.

- Documentos de diseño cortos: problema, propuesta, alternativas, riesgos.
- Descripciones de cambios que expliquen el porqué.
- Un `README` que permita arrancar el proyecto sin preguntar.
- Procedimientos (*runbooks*) para lo que se hace bajo presión.

Prueba útil: ¿alguien que no estuvo en la reunión entendería la decisión leyendo esto?

### Defender una solución técnica

Es el objetivo final de este curso. Una defensa sólida:

1. **Empieza por el problema** y los criterios, no por la solución.
2. **Presenta la solución** con un diagrama y un recorrido de un caso real.
3. **Muestra las alternativas** y por qué se descartaron.
4. **Declara los costos y riesgos** antes de que te los señalen. Genera más confianza que ocultarlos.
5. **Sostén las afirmaciones con evidencia:** mediciones, pruebas, referencias.
6. **Distingue lo que sabes de lo que supones.**
7. **Escucha las objeciones.** Una buena objeción mejora el diseño; cambiar de opinión ante un buen argumento es una fortaleza.
8. **Di "no lo sé"** cuando no lo sabes, y cómo lo averiguarías.

### Mentoría

- **Enseña a pensar, no la respuesta.** Pregunta: "¿qué has probado?", "¿qué opciones ves?". Dar la solución resuelve hoy; hacer la pregunta correcta resuelve los próximos cien casos. Es exactamente la regla de los talleres de este curso.
- **Ajusta el apoyo al nivel:** instrucciones claras para quien empieza, autonomía para quien ya puede.
- **Delega con contexto:** el objetivo y el porqué, no solo la tarea.
- **Deja equivocarse en lo que es seguro,** y acompaña en lo que no lo es.
- **Trabajo en pareja** para transferir conocimiento tácito.
- **Retroalimentación frecuente, concreta y oportuna:** situación, comportamiento observado e impacto. No esperes a la evaluación anual.
- **Crea seguridad:** un equipo donde preguntar o admitir un error no tiene costo aprende más rápido.

### Conversaciones difíciles

- Con datos, no con juicios.
- En privado lo que es crítica; en público lo que es reconocimiento.
- Sobre comportamientos y resultados, no sobre la persona.
- Buscando entender antes de responder.

### Seguir aprendiendo

- Fundamentos antes que herramientas: las herramientas cambian cada pocos años; los principios de este curso, no.
- Aprender construyendo y enseñando.
- Leer código ajeno bien hecho.
- Tener profundidad en un área y conocimiento general de las demás.

## Ejemplo: el mismo mensaje para dos audiencias

**Para el equipo técnico:**

> Propongo mover el cálculo de comisiones a un proceso asíncrono con Outbox. Hoy va dentro de la transacción de la venta y, cuando el servicio de reglas tarda, retiene conexiones del pool; así fue el incidente del martes. Alternativas: subir el tiempo límite (no resuelve la causa) o una caché de reglas (reduce pero no elimina). Costo: la comisión aparecerá con unos segundos de retraso y hay que hacer idempotente el consumidor.

**Para gerencia:**

> El martes las ventas estuvieron caídas 40 minutos. La causa fue un cálculo que hoy bloquea la venta mientras espera a otro sistema. Proponemos separarlos: la venta se registra de inmediato y la comisión se calcula segundos después. Son dos semanas de trabajo y elimina esa causa de caída. El único cambio visible es que la comisión tardará unos segundos en aparecer en el reporte.

## Errores comunes

- Explicar la solución antes que el problema.
- La misma explicación para todas las audiencias.
- Enterrar la conclusión al final de un texto largo.
- Ocultar los riesgos para "vender" la propuesta.
- Defender una idea por orgullo cuando ya hay un argumento mejor.
- Resolverle todo a quien se está formando.
- Retroalimentación vaga ("mejora la calidad") o tardía.
- Guardarse el conocimiento.

## Taller final del curso

1. **Defensa.** Toma el diseño del taller integrador de arquitectura (Nivel 5) y prepara su defensa en diez minutos: problema, propuesta, alternativas, costos. Grábala o preséntala a alguien y recoge tres objeciones.
2. **Dos audiencias.** Escribe la misma propuesta técnica para un equipo de desarrollo y para gerencia.
3. **Mentoría.** Explica un concepto de este curso (por ejemplo, la inversión de dependencias) a alguien que no lo conoce, sin darle la definición primero. Anota qué preguntas funcionaron.
4. **Retroalimentación.** Redacta tres comentarios de retroalimentación reales con el esquema situación, comportamiento e impacto.
5. **Documento.** Escribe el `README` de un proyecto tuyo de modo que una persona nueva lo pueda arrancar sin ayuda. Compruébalo con alguien.
6. **Balance.** Recorre el [progreso](../../../PROGRESO.md) del curso: qué niveles dominas, cuáles necesitan repaso y cuál es tu plan para los próximos seis meses.

Rama `taller/n12-05-comunicacion`.

## Preguntas senior

1. Explícame algo técnico complejo como si yo no fuera técnico.
2. ¿Cómo defiendes una decisión impopular?
3. Cuéntame de una vez en que cambiaste de opinión por el argumento de otra persona.
4. ¿Cómo ayudas a crecer a alguien con menos experiencia sin resolverle los problemas?
5. ¿Cómo das una retroalimentación negativa?
6. ¿Cómo te mantienes al día sin perseguir cada novedad?
