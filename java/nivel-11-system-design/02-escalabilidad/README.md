# 02 — Bloques de escalabilidad

## Idea

Un restaurante que se llena tiene varias salidas: contratar a un cocinero más rápido, abrir más cocinas, tener platos preparados de antemano, o separar la barra de jugos de la cocina caliente. Cada una resuelve un cuello de botella distinto. Escalar un sistema es saber cuál aplicar y dónde.

## Lo esencial

### Primero, medir

Antes de escalar hay que saber qué se satura: CPU, memoria, base de datos, red o una dependencia externa. Escalar lo que no es el cuello de botella no mejora nada.

### Bloques

| Bloque | Qué resuelve | Costo |
|---|---|---|
| **Escalado vertical** | Poco margen en una máquina | Tiene techo; un único punto de fallo |
| **Escalado horizontal + balanceador** | Más capacidad de cómputo | La aplicación debe ser sin estado |
| **Caché** | Lecturas repetidas y costosas | Invalidación; datos posiblemente desactualizados |
| **CDN** | Contenido estático y latencia geográfica | Invalidación del contenido |
| **Réplicas de lectura** | Muchas lecturas sobre la base | Retraso de replicación |
| **Particionamiento (*sharding*)** | Datos o escrituras que no caben en un nodo | Consultas entre particiones, rebalanceo, complejidad alta |
| **Colas y procesamiento asíncrono** | Picos y tareas lentas | Consistencia eventual |
| **Desnormalización y vistas precalculadas** | Consultas complejas frecuentes | Mantener las copias al día |
| **Límite de tasa** | Abuso y sobrecarga | Rechazar peticiones legítimas |

### Balanceo de carga

- Reparte peticiones entre instancias: por turnos, por menos conexiones, o por *hash* de una clave.
- Comprueba la salud y retira las instancias que fallan.
- **Sesiones pegajosas:** atan al usuario a una instancia. Mejor evitarlas y guardar la sesión afuera.

### Caché por niveles

Navegador → CDN → caché de la aplicación → caché de la base. Cuanto más cerca del usuario, más barata la respuesta.

### Escalar la base de datos, en orden

1. Índices y consultas bien escritas.
2. Pool de conexiones bien dimensionado.
3. Caché.
4. Réplicas de lectura.
5. Escalado vertical.
6. Separar por dominio (una base por módulo o servicio).
7. Particionamiento horizontal: el último recurso.

**Clave de partición:** debe repartir de forma uniforme y permitir que la mayoría de las consultas vayan a una sola partición. Una mala clave produce particiones calientes.

**Hashing consistente:** reparte las claves entre nodos de modo que agregar o quitar un nodo mueva solo una fracción de ellas.

### Asincronía

Lo que no necesita respuesta inmediata se encola: correos, reportes, procesamiento de imágenes. La cola absorbe los picos y los trabajadores procesan a su ritmo.

## Ejemplo: evolución de un sistema

| Usuarios | Arquitectura | Qué se agregó y por qué |
|---|---|---|
| Cientos | Una máquina: aplicación y base | — |
| Miles | Aplicación y base separadas | La base compite por recursos |
| Decenas de miles | Balanceador, varias instancias, sesión externa | Disponibilidad y cómputo |
| Cientos de miles | Caché, CDN, réplicas de lectura | Las lecturas dominan |
| Millones | Colas, servicios separados, particionamiento selectivo | Picos, equipos, escrituras |

Cada paso responde a un cuello de botella **medido**. Ninguno se da por adelantado.

## Errores comunes

- Particionar la base antes de optimizar las consultas.
- Agregar una caché sin estrategia de invalidación.
- Estado en las instancias que impide escalar horizontalmente.
- Leer de una réplica justo después de escribir.
- Una clave de partición que concentra la carga.
- Escalar la aplicación cuando el cuello de botella es la base.
- Pool de conexiones enorme que satura la base.
- Asumir que más máquinas arreglan un algoritmo malo.

## Taller

1. Con una herramienta de carga (k6, Gatling o JMeter), encuentra el punto de saturación de una aplicación sencilla e identifica el recurso que se agota.
2. Aplica mejoras en este orden, midiendo tras cada una: índice, caché, segunda instancia con balanceador. Registra cuánto ganó cada paso.
3. Demuestra el problema de la sesión local con dos instancias y corrígelo.
4. Diseña en papel el particionamiento de una tabla de mensajes de un chat: elige la clave, justifícala y señala qué consulta queda difícil.
5. Mueve una operación lenta a una cola y compara el tiempo de respuesta percibido.
6. Para un sistema que conozcas, propón el siguiente paso de escalado y la métrica que lo justificaría.

Rama `taller/n11-02-escalabilidad`.

## Preguntas senior

1. ¿Cómo encuentras el cuello de botella de un sistema?
2. ¿En qué orden escalas una base de datos y por qué?
3. ¿Cómo eliges una clave de partición?
4. ¿Qué exige el escalado horizontal a la aplicación?
5. ¿Qué es el hashing consistente y qué problema resuelve?
6. ¿Cuándo una cola mejora la escalabilidad y cuándo solo traslada el problema?
