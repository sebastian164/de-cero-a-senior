# 01 — Diseño de sistemas frontend

Sesión común a las rutas de Angular y de React/Next. El método general está en el [Nivel 11 de Java](../../java/nivel-11-system-design/01-metodo/); aquí se adapta a la interfaz.

## Idea

Diseñar un sistema frontend no es elegir un framework. Es decidir cómo se cargan los datos, dónde vive el estado, cómo se divide la interfaz, qué pasa cuando la red falla y cómo se mantiene rápido cuando crece. Las mismas preguntas valen para Angular, React o lo que venga después.

## El método

### 1. Requisitos

- **Funcionales:** qué puede hacer el usuario. Los tres o cuatro recorridos principales.
- **No funcionales:** dispositivos y redes objetivo, navegadores, accesibilidad exigida, idiomas, SEO, uso sin conexión, tiempo real, volumen de datos en pantalla, número de equipos que lo construirán.

### 2. Arquitectura de alto nivel

- **Estrategia de renderizado** por tipo de página: cliente, servidor, estático.
- **División de la interfaz:** árbol de componentes principales; qué es contenedor y qué es presentación.
- **Rutas** y su carga diferida.
- **Organización del código:** funcionalidades, bibliotecas, y si aplica, microfrontends.

### 3. Datos

- **Contrato con el backend:** endpoints o consultas que necesita cada pantalla. ¿REST, GraphQL, un BFF?
- **Estado:** clasificar cada dato (local, URL, servidor, compartido) y decidir dónde vive.
- **Caché:** qué se guarda, cuánto vive, cómo se invalida.
- **Paginación:** por desplazamiento o por cursor; desplazamiento infinito o páginas.
- **Tiempo real:** sondeo, SSE o WebSocket.
- **Optimismo:** qué operaciones se reflejan antes de la confirmación.

### 4. Profundización: lo que diferencia a un senior

| Tema | Preguntas |
|---|---|
| Rendimiento | ¿Qué se carga primero? ¿Qué se difiere? ¿Presupuesto de tamaño? ¿Listas largas? |
| Fallos | ¿Qué ve el usuario sin red, con error del servidor, con datos parciales? |
| Accesibilidad | ¿Teclado, lector de pantalla, gestión del foco? |
| Seguridad | ¿Dónde vive la sesión? ¿Qué entrada del usuario se muestra como HTML? |
| Concurrencia | ¿Dos pestañas? ¿Dos usuarios editando lo mismo? ¿Respuestas que llegan desordenadas? |
| Observabilidad | ¿Cómo sabré que algo falla en el navegador del usuario? |
| Evolución | ¿Cómo se agregan funcionalidades sin tocar las existentes? ¿Cómo se prueba? |

### 5. Compromisos

Cada decisión con su alternativa y su costo. No hay respuesta única.

## Ejemplo: un buscador con autocompletado

**Requisitos.** Sugerencias mientras se escribe; resultados con filtros; funciona en móvil con red lenta; accesible por teclado.

**Decisiones.**

| Decisión | Elegido | Alternativa y por qué no |
|---|---|---|
| Frecuencia de peticiones | Esperar 300 ms tras la última tecla, mínimo 2 caracteres | Una por tecla: satura el servidor y la red |
| Respuestas desordenadas | Cancelar la petición anterior | Ignorar por identificador: desperdicia red |
| Caché | Por término, en memoria, pocos minutos | Sin caché: repetir al borrar una letra |
| Estado de los filtros | En la URL | En memoria: no se puede compartir ni volver atrás |
| Resultados | Paginación por cursor con desplazamiento infinito y lista virtual | Cargar todo: inviable con miles |
| Accesibilidad | Patrón *combobox*: flechas, Enter, Escape, anuncio del número de resultados | Lista de `div`: inservible con teclado |
| Sin resultados o error | Estados explícitos, con reintento | Lista vacía indistinguible de un fallo |
| Renderizado | Página de resultados en servidor (SEO); sugerencias en cliente | Todo en cliente: sin indexación |

**Componentes.** Campo de búsqueda (presentación), lista de sugerencias (presentación), panel de filtros, lista de resultados virtual, y un contenedor de página que conecta con la URL y con la capa de datos.

## Problemas clásicos para practicar

| Sistema | Dificultad central |
|---|---|
| Muro de noticias con desplazamiento infinito | Lista virtual, caché, actualizaciones nuevas sin saltos |
| Chat | Tiempo real, orden de mensajes, envío optimista, reconexión |
| Editor colaborativo | Resolución de conflictos, estado local primero |
| Tablero con gráficas en tiempo real | Frecuencia de actualización, rendimiento del pintado |
| Carga de archivos grandes | Trozos, reanudación, progreso, cancelación |
| Formulario de varios pasos | Estado entre pasos, validación, guardado parcial, retomar |
| Aplicación que funciona sin conexión | Almacenamiento local, cola de sincronización, conflictos |
| Tienda con carrito | Consistencia entre pestañas, precios obsoletos, inventario |

## Errores comunes

- Empezar por el framework o la biblioteca de estado.
- Diseñar solo el caso feliz, con red rápida.
- Todo el estado en un almacén global.
- Ignorar accesibilidad y fallos hasta el final.
- No estimar: cuántos elementos habrá en pantalla, qué tamaño tendrá el paquete.
- Diseños que solo funcionan con un equipo, cuando serán cinco.

## Taller

Aplica el método, por escrito y con diagramas, a tres de los problemas clásicos. Para cada uno: requisitos y lo que queda fuera, arquitectura, modelo de datos y contrato con el backend, clasificación del estado, árbol de componentes, cinco decisiones con sus alternativas, y qué ve el usuario en cada modo de fallo. Implementa uno de los tres en la ruta que estés siguiendo.

Rama `taller/fe-01-diseno-de-sistemas`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Diseña un desplazamiento infinito: ¿qué problemas aparecen con 50.000 elementos?
2. ¿Cómo decides qué estado va en la URL, en el servidor y en el cliente?
3. ¿Cómo diseñas para una red lenta e inestable?
4. ¿Cómo manejas la edición simultánea del mismo dato por dos usuarios?
5. ¿Qué cambia en tu diseño si lo van a construir cinco equipos?
