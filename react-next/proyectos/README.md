# Proyectos de aplicación — ruta React y Next.js

Cinco aplicaciones de dificultad creciente. Cada una integra los niveles indicados.

Reglas comunes: repositorio o carpeta propia; `README` con cómo ejecutarla; `DECISIONES.md` con las decisiones y sus alternativas; TypeScript estricto; pruebas desde el proyecto 2. No hay solución de referencia: la revisión se hace sobre tu entrega.

## 1. Gestor de tareas

**Después de:** Nivel 1. **Tecnología:** React (con Vite), sin servidor.

- Crear, editar, completar y eliminar tareas; etiquetas; fecha límite; filtros y búsqueda.
- Persistencia en `localStorage` mediante un hook propio.
- Contadores y progreso como valores derivados.

**Se evalúa:** componentes pequeños con flujo unidireccional, estado mínimo e inmutable, ningún efecto innecesario, lógica en funciones puras y tipos sin `any`.

## 2. Catálogo y carrito de compras

**Después de:** Niveles 2 y 3. **Tecnología:** Next.js.

- Catálogo desde una API pública o simulada: listado y ficha en componentes de servidor; búsqueda, filtros y paginación en la URL.
- Carrito en el cliente con reductor y contexto, visible en la cabecera y en su página; cupones; modalidades de envío.
- Capa de datos con esquema de validación y adaptadores.
- `loading`, `error` y `not-found` en cada segmento; límites de `Suspense` justificados.

**Se evalúa:** frontera entre servidor y cliente, composición en lugar de propiedades booleanas, componentes que respetan el contrato del elemento nativo, y que agregar una modalidad de envío no modifique código existente.

## 3. Portal de trámites con formularios

**Después de:** Nivel 4.

- Asistente de varios pasos para una solicitud, con pasos condicionales.
- Un esquema de validación compartido entre cliente y servidor; acciones de servidor; errores del servidor mostrados en su campo; listas dinámicas; carga de archivos.
- Guardado de borrador y recuperación.
- Funciona con teclado y con lector de pantalla; el envío básico funciona sin JavaScript.
- Pruebas: dominio y esquemas unitarios, componentes como usuario con red simulada, acciones de servidor y dos recorridos de extremo a extremo.

**Se evalúa:** estados imposibles eliminados con tipos, validación y autorización en cada acción, calidad de las pruebas y accesibilidad real.

## 4. Aplicación completa con base de datos

**Después de:** Nivel 5.

Una plataforma de reservas (salas, canchas o citas) construida entera en Next.js, con su propia base de datos.

- Autenticación con sesión en cookie y autorización por rol y por recurso.
- Dominio con reglas reales (sin solapamientos, plazos de cancelación, lista de espera) en funciones puras; casos de uso con puertos; capa de acceso a datos con `server-only`.
- Acciones de servidor delgadas; invalidación de caché por etiqueta; interfaz optimista.
- Estrategia de renderizado decidida y documentada por página.
- Estructura por funcionalidades con límites verificados.
- Una prueba de concurrencia que demuestre que no hay doble reserva.

**Se evalúa:** dirección de las dependencias, reglas probadas sin framework, seguridad de acciones y datos, decisiones de caché verificadas en la construcción de producción, y tres ADR.

## 5. Proyecto final: aplicación lista para producción

**Después de:** Niveles 6 y 7.

Lleva el proyecto 4 (u otro equivalente) a estado de producción:

- Presupuesto de rendimiento cumplido en perfil móvil; métricas web medidas en campo.
- Auditoría de accesibilidad sin hallazgos graves; dos idiomas con rutas por idioma.
- Revisión de seguridad: acciones invocadas directamente, datos que cruzan al cliente, cabeceras, dependencias.
- Integración continua con todas las puertas de calidad, vistas previas por cambio y despliegue automático; desplegada en una plataforma gestionada o en contenedor.
- Seguimiento de errores y banderas de función.
- Documento de diseño del sistema.

**Entrega final:** una defensa de quince minutos: el problema, la arquitectura, las tres decisiones más importantes con sus alternativas, lo que harías distinto y cómo evolucionaría con cinco equipos.
