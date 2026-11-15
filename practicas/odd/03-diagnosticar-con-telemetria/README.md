# 03 — Diagnosticar con telemetría y probar la observabilidad

## Idea

Un simulacro de incendio. Tener extintores, alarmas y un plano de evacuación no dice nada hasta que suena la alarma un martes cualquiera y se ve qué pasa: que la puerta de emergencia estaba con llave, que la alarma no se oye en el sótano. Nadie descubre eso leyendo el plan. La observabilidad también se prueba así: rompiendo algo a propósito y comprobando si lo que instrumentaste te lleva a la causa.

## Lo esencial

### La observabilidad también se prueba

La instrumentación es código, y como todo código puede estar mal: el campo que nunca se llena, la traza que se parte, la alerta que no suena. La diferencia es que su fallo no se nota hasta el peor momento.

| Qué | Cómo se prueba |
|---|---|
| Que el evento se emite con sus campos | Una prueba que ejecuta la operación y lee lo emitido |
| Que la traza está completa | Una prueba basada en trazas: lanza una petición y comprueba la estructura de la traza resultante |
| Que la alerta suena | Provocar la condición en un ambiente de pruebas |
| Que se puede diagnosticar | Un simulacro: alguien rompe algo y otro lo encuentra solo con la telemetría |

### El método de diagnóstico

Es el mismo de fuera hacia dentro del [taller de diagnóstico de Kubernetes](../../../docker-kubernetes/kubernetes/09-diagnostico/), ahora con datos:

1. **¿Qué sufre el usuario?** El indicador: qué rutas, desde cuándo, cuántos.
2. **¿Qué cambió?** Despliegues, configuración, tráfico. La versión como atributo responde esto en segundos.
3. **¿A quién afecta?** Cortar por dimensiones: ruta, cliente, región, versión. Buscar qué tienen en común las peticiones malas que no tengan las buenas.
4. **¿Dónde se va el tiempo o dónde falla?** Una traza de una petición afectada.
5. **¿Por qué?** Los eventos y registros de ese tramo.
6. **Confirmar.** Una hipótesis que explique todos los síntomas, no solo uno.

El paso 3 es el que más se salta y el que más ahorra.

### Alertas que sirven

| Buena alerta | Mala alerta |
|---|---|
| Algo que afecta al usuario **ahora** | "CPU al 85 %" |
| Requiere que alguien actúe | Se resuelve sola en cinco minutos |
| Dice qué mirar primero | "Error en producción" |
| Suena poco | Suena tanto que el equipo la silenció |

Cada alerta debería enlazar a una guía corta: qué significa, cómo confirmarlo, primera acción.

### Costo

| Palanca | Qué hace |
|---|---|
| Muestreo | Guardar una parte de las trazas. Mejor todavía: todas las que tienen error o son lentas, y una muestra del resto |
| Retención | Menos tiempo para el detalle, más para los agregados |
| Cardinalidad | Vigilar las etiquetas de las métricas |
| Niveles | El detalle fino se activa cuando hace falta, no siempre |

### ODD en el día a día

| Momento | Qué se hace |
|---|---|
| Al planear | "¿Cómo sabremos que funciona?" es un criterio de aceptación |
| Al programar | La instrumentación va en el mismo cambio |
| Al revisar | Quien revisa pregunta qué se va a ver en producción |
| Al desplegar | Quien despliega mira sus datos durante un rato. No "si nadie se queja, salió bien" |
| Tras un incidente | Además de arreglar la causa: ¿qué telemetría nos habría llevado ahí en cinco minutos? Se añade |

## Ejemplo

### Antes

Incidente: desde las diez, algunos usuarios no pueden guardar notas. El equipo abre los registros y busca "error". Hay miles de líneas. Alguien ve un `NullPointerException` y pasa una hora persiguiéndolo: llevaba meses ahí y no tenía nada que ver. Otro reinicia los pods; mejora diez minutos y vuelve. A la una, por descarte, alguien mira el disco.

### Análisis

| Problema | Consecuencia |
|---|---|
| Se empezó por los registros, buscando una palabra | Se encontró ruido que ya existía |
| Nadie preguntó a quién afectaba | Habrían visto que solo fallaban las peticiones atendidas por dos de los seis pods |
| Reiniciar destruyó evidencia | Y dio una mejora falsa que retrasó el diagnóstico |
| No había un método | Tres horas, y la causa se encontró por casualidad |

### Después

El mismo incidente con el método:

| Paso | Qué se mira | Qué se ve |
|---|---|---|
| 1. ¿Qué sufre el usuario? | El indicador de "guardar una nota" | Cayó del 99,8 % al 67 % a las 10:02. Las lecturas están bien |
| 2. ¿Qué cambió? | Atributo de versión y despliegues | Ningún despliegue. Mismo tráfico |
| 3. ¿A quién afecta? | Los errores agrupados por `host` | El 100 % de los fallos viene de dos pods. Los otros cuatro no fallan |
| 4. ¿Dónde falla? | Una traza de una petición fallida en uno de esos pods | El tramo de escritura en disco termina con error |
| 5. ¿Por qué? | El evento de ese tramo | `motivo: "No space left on device"` |
| 6. Confirmar | Qué tienen en común esos dos pods | Están en el mismo nodo, cuyo disco se llenó a las 10:01 |

Quince minutos. El paso 3 redujo el problema de "la aplicación falla" a "dos pods fallan", y a partir de ahí cada paso fue una consulta.

## Errores comunes

- Empezar por los registros buscando la palabra "error".
- Reiniciar antes de mirar.
- No preguntar qué tienen en común los casos malos.
- Quedarse con la primera anomalía que aparece.
- Alertas que nadie atiende porque suenan siempre.
- Dar por buena la instrumentación sin haberla usado nunca para diagnosticar algo.
- Cerrar un incidente sin añadir la telemetría que faltó.
- Guardar todo para siempre.

## Taller integrador de ODD

**Objetivo:** demostrar, con un simulacro, que tu instrumentación de Bitácora sirve para diagnosticar, y dejar el proceso escrito.

Necesitas lo de las sesiones 01 y 02 y, si puedes, a otra persona. Si trabajas solo, deja pasar un día entre preparar los fallos y diagnosticarlos, y sortea cuál se activa.

1. **Pruebas de la instrumentación.** Escribe una prueba que ejecute "guardar una nota" y compruebe que se emite el evento con todos sus campos. Y otra que compruebe que la traza de esa petición tiene los tramos esperados.
2. **Prepara cinco fallos**, activables por configuración sin tocar el código de negocio. Por ejemplo: una ruta lenta solo para notas largas; errores en una de cada cinco escrituras; el disco de una de las copias en solo lectura; una copia que tarda en arrancar; una fuga que hace cada petición un poco más lenta que la anterior. Inventa al menos dos tuyos.
3. **Simulacro.** Con tres copias de la API detrás de nginx y tráfico continuo, alguien activa un fallo sin decir cuál. Quien diagnostica solo puede usar la interfaz de observabilidad: ni el código, ni la configuración, ni entrar a los contenedores.
4. Para cada fallo, anota: cuánto tardaste, qué pasos del método seguiste, qué consulta te dio la pista y qué te faltó.
5. **Cierra los huecos.** Por cada cosa que te faltó, añade la instrumentación y repite ese fallo. ¿Cuánto tardas ahora?
6. **Alertas.** Define tres. Para cada una: qué condición, por qué requiere acción y una guía de cinco líneas. Dispara una de verdad con uno de tus fallos.
7. Escribe una alerta mala a propósito (sobre CPU, por ejemplo) y muestra un caso en que suena sin que el usuario sufra y otro en que el usuario sufre y no suena.
8. **Costo.** Mide cuánta telemetría genera una hora de tráfico. Configura un muestreo que conserve todas las trazas con error o lentas y una parte del resto. ¿Cuánto bajó el volumen? ¿Sigues pudiendo diagnosticar los cinco fallos?
9. **Informe posterior.** Escribe el informe de uno de los simulacros como si hubiera sido real: cronología, impacto medido con el indicador, causa, cómo se detectó, qué se cambia. Sin culpables.
10. **El proceso.** Escribe para tu equipo, en una página: qué se pregunta al planear, qué se revisa en un pull request, qué se mira al desplegar y qué se añade después de un incidente.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Los cinco fallos se diagnostican solo con la interfaz | La tabla del paso 4 |
| Cada hueco encontrado se cerró y se volvió a probar | Paso 5, con tiempos antes y después |
| La instrumentación tiene pruebas | Paso 1 |
| Cada alerta tiene guía | Paso 6 |
| El muestreo no impide diagnosticar | Paso 8 |

**Entrega:** rama `taller/odd-03`, con el código, los paneles y los documentos en `taller/solucion/`, y `ANALISIS.md`.

## En el trabajo real

Las cuatro partes de esta ruta responden a la misma pregunta desde sitios distintos: **¿cómo sé que esto hace lo que debe?** TDD lo responde para una función en tu máquina. Karate, para el servicio visto desde fuera. SDD, para lo que se pidió frente a lo que se construyó. ODD, para el sistema real con usuarios reales. Un equipo senior no elige una: sabe cuál usar para cada duda.

## Preguntas senior

1. ¿Cómo sabes que tu instrumentación sirve antes de un incidente real?
2. Describe tu método para diagnosticar "el sistema está lento".
3. ¿Qué hace buena a una alerta?
4. ¿Cómo controlas el costo de la telemetría sin quedarte ciego?
5. ¿Qué cambia en el flujo de trabajo de un equipo que adopta ODD?
