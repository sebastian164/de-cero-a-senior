# Proyectos de aplicación — ruta Angular

Cinco aplicaciones de dificultad creciente. Cada una integra los niveles indicados.

Reglas comunes: repositorio o carpeta propia; `README` con cómo ejecutarla; `DECISIONES.md` con las decisiones y sus alternativas; TypeScript estricto; pruebas desde el proyecto 2. No hay solución de referencia: la revisión se hace sobre tu entrega.

## 1. Gestor de tareas

**Después de:** Nivel 1.

- Crear, editar, completar y eliminar tareas; etiquetas; fecha límite; filtros y búsqueda.
- Persistencia en `localStorage`.
- Contadores y progreso como valores derivados.
- Dos rutas: lista y detalle.

**Se evalúa:** componentes pequeños con flujo unidireccional, estado mínimo con señales, lógica en funciones puras, tipos sin `any`.

## 2. Catálogo y carrito de compras

**Después de:** Niveles 2 y 3.

- Catálogo desde una API pública o simulada, con búsqueda con espera, filtros y paginación en la URL.
- Carrito compartido entre el encabezado y la página del carrito; cupones; costo de envío con varias modalidades.
- Estados de carga, vacío y error en cada pantalla.
- Adaptadores entre la API y el modelo; interceptor de errores.

**Se evalúa:** separación contenedor y presentación, almacén con señales, estrategias sin `switch`, ausencia de carreras entre peticiones, y que agregar una modalidad de envío no modifique código existente.

## 3. Portal de trámites con formularios

**Después de:** Nivel 4.

- Asistente de varios pasos para una solicitud, con pasos condicionales según las respuestas.
- Validaciones síncronas, entre campos y asíncronas; listas dinámicas; carga de archivos con progreso.
- Guardado de borrador y recuperación; aviso de cambios sin guardar.
- Accesible por teclado y con lector de pantalla.
- Pruebas: reglas y validadores unitarios, componentes como usuario, y dos recorridos de extremo a extremo.

**Se evalúa:** estados imposibles eliminados con tipos, validadores como funciones puras, calidad de las pruebas y accesibilidad real.

## 4. Panel de administración modular

**Después de:** Nivel 5.

- Al menos cuatro funcionalidades (usuarios, pedidos, inventario, reportes) cargadas de forma diferida, con límites verificados automáticamente.
- Autenticación con renovación de sesión y autorización por rol.
- Tablas con orden, filtros y paginación en servidor; exportación.
- Una funcionalidad con reglas de cliente reales, organizada en capas con puertos.
- Tipos generados desde una especificación OpenAPI.
- Tablero con gráficas cargadas con `@defer`.

**Se evalúa:** grafo de dependencias sin ciclos, clasificación correcta del estado, traducción de errores, y tres ADR.

## 5. Proyecto final: aplicación lista para producción

**Después de:** Niveles 6 y 7.

Lleva el proyecto 4 (u otro equivalente) a estado de producción:

- Renderizado híbrido: una parte pública en servidor o prerenderizada y la parte privada en cliente.
- Presupuesto de rendimiento cumplido en perfil móvil; métricas web medidas en campo.
- Auditoría de accesibilidad sin hallazgos graves; dos idiomas.
- Revisión de seguridad: sesión, CSP, dependencias.
- Integración continua con todas las puertas de calidad, vistas previas por cambio y despliegue automático.
- Seguimiento de errores y banderas de función.
- Documento de diseño del sistema.

**Entrega final:** una defensa de quince minutos: el problema, la arquitectura, las tres decisiones más importantes con sus alternativas, lo que harías distinto y cómo evolucionaría con cinco equipos.
