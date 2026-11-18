# 01 — Un método para diseñar sistemas

## Idea

Un arquitecto no empieza una casa dibujando el baño. Pregunta cuántas personas vivirán, qué presupuesto hay, cómo es el terreno. Después hace un plano general y solo entonces detalla. Diseñar sistemas sigue el mismo orden: requisitos, magnitudes, esquema general, y detalle donde importa.

## El método, en seis pasos

### 1. Aclarar los requisitos

- **Funcionales:** qué hace el sistema. Acota: tres o cuatro casos de uso centrales, y qué queda por fuera.
- **No funcionales:** escala, latencia, disponibilidad, consistencia, durabilidad, seguridad, costo.
- **Preguntas que siempre hay que hacer:** ¿cuántos usuarios y cuántas operaciones?, ¿más lecturas o más escrituras?, ¿qué tan actualizado debe estar lo que se lee?, ¿qué pasa si se pierde un dato?, ¿hay picos?, ¿alcance geográfico?

Empezar a dibujar sin preguntar es el error que más descalifica.

### 2. Estimar

Órdenes de magnitud, con aritmética sencilla:

| Dato | Cálculo |
|---|---|
| Peticiones por segundo | 10 millones al día ÷ 86.400 ≈ 115 por segundo en promedio; pico de 3 a 5 veces más |
| Almacenamiento | 10 millones de registros al día × 1 KB × 365 ≈ 3,6 TB al año |
| Ancho de banda | Peticiones por segundo × tamaño de la respuesta |

Números útiles: un día tiene unos 100.000 segundos; leer de memoria toma nanosegundos, de un SSD del orden de cien microsegundos, y una llamada entre centros de datos, decenas de milisegundos.

La estimación decide el diseño: 100 peticiones por segundo caben en una máquina; 100.000 no.

### 3. Definir la interfaz

Las operaciones principales de la API, con sus entradas y salidas. Fija el contrato antes del interior.

### 4. Modelar los datos

Entidades, relaciones y, sobre todo, **patrones de acceso**: qué consultas existen y con qué frecuencia. De ahí sale la elección del almacenamiento.

### 5. Diseño de alto nivel

Un diagrama con clientes, balanceador, servicios, almacenamientos, cachés y colas. Recorre un caso de uso de principio a fin sobre el dibujo.

### 6. Profundizar y discutir compromisos

- Encuentra los **cuellos de botella** y los **puntos únicos de fallo**.
- Profundiza en los dos o tres componentes más difíciles.
- Para cada decisión: qué alternativas había, por qué esta, y qué se sacrifica.
- Qué se monitorea y cómo evoluciona el diseño si la carga se multiplica por diez.

## Principios

- **No hay respuesta correcta única.** Se evalúa el razonamiento y los compromisos.
- **Empieza simple.** Un diseño que funciona para la carga dada y un plan para crecer vale más que una arquitectura para mil millones de usuarios que nadie pidió.
- **Cada componente que agregas es algo más que puede fallar.**
- **Di los supuestos en voz alta.**

## Ejemplo: aplicar los pasos 1 y 2

Sistema: reservas de citas médicas para una red de clínicas.

- Funcional: buscar disponibilidad, reservar, cancelar, recordatorios. Fuera de alcance: historia clínica y pagos.
- No funcional: nunca dos pacientes en la misma franja (consistencia fuerte en la reserva); la búsqueda puede tolerar unos segundos de retraso; disponibilidad del 99,9 %.
- Magnitudes: 200 clínicas, 50 médicos cada una, 20 citas diarias por médico: 200.000 citas al día, unas 2 o 3 por segundo, con pico en la mañana de quizá 30. Búsquedas: diez por cada reserva.
- Conclusión: la escala es modesta. Una base relacional con réplica de lectura y una caché para la disponibilidad es suficiente. La dificultad está en la **concurrencia de la reserva**, no en el volumen.

## Errores comunes

- Proponer la solución antes de entender el problema.
- Diseñar para una escala que no existe.
- Dibujar cajas sin explicar cómo fluyen los datos.
- No mencionar compromisos: toda decisión tiene costo.
- Nombrar tecnologías sin justificar.
- Olvidar los fallos: qué pasa si cae cada caja.
- Ignorar lo operativo: despliegue, monitoreo, costo.

## Taller

Aplica los seis pasos, por escrito y con diagrama, a estos sistemas (uno por sesión, 45 minutos cada uno):

1. Un sistema de turnos para una entidad pública con 300 sedes.
2. Una plataforma de encuestas que debe soportar un pico de respuestas en una hora.
3. Un servicio de subida y consulta de documentos con control de acceso.

Para cada uno entrega: requisitos y lo que dejaste fuera, estimaciones con su aritmética, API, modelo de datos, diagrama, y tres decisiones con sus alternativas y costos.

Rama `taller/n11-01-metodo`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué preguntas haces antes de diseñar?
2. ¿Cómo estimas la carga de un sistema que todavía no existe?
3. ¿Cómo decides el nivel de consistencia de cada operación?
4. ¿Qué diferencia hay entre diseñar para la carga actual y sobrediseñar?
5. ¿Cómo comunicas un compromiso técnico a alguien no técnico?
