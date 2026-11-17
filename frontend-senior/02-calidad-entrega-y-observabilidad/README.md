# 02 — Calidad, entrega y observabilidad en el frontend

Sesión común a las rutas de Angular y de React/Next. Los fundamentos de integración continua y de observabilidad están en los niveles [10](../../java/nivel-10-cloud/04-ci-cd/) y [9](../../java/nivel-09-sistemas-distribuidos/06-observabilidad-distribuida/) de Java.

## Idea

El backend falla en tus servidores, donde puedes mirar. El frontend falla en el teléfono de otra persona, con su navegador, su red y sus extensiones. Si no instrumentas la aplicación, te enteras de los errores cuando un usuario se queja, y solo de los que se quejan.

## Lo esencial

### Puertas de calidad automáticas

Lo que una máquina puede revisar no debe depender de una persona:

| Puerta | Detecta |
|---|---|
| Tipos estrictos | Errores de contrato |
| *Linter* (incluidas reglas de accesibilidad y de límites de arquitectura) | Errores comunes y dependencias indebidas |
| Formateador | Discusiones de estilo |
| Pruebas unitarias y de componentes | Regresiones de comportamiento |
| Pruebas de extremo a extremo | Recorridos críticos rotos |
| Presupuesto de tamaño del paquete | Crecimiento del peso de la aplicación |
| Auditoría de dependencias | Vulnerabilidades conocidas |
| Comprobación de accesibilidad | Problemas detectables automáticamente |
| Presupuesto de rendimiento (Lighthouse en CI) | Regresiones de métricas web |

Todas corren en cada *pull request*. Lo que no pasa, no se fusiona.

### Entrega

- **Entornos de vista previa:** cada *pull request* se despliega en una URL temporal. Diseño y producto revisan lo real, no una captura.
- **Artefacto inmutable:** se construye una vez; la configuración por ambiente se inyecta en ejecución o al desplegar.
- **Archivos con huella en el nombre** (*hash*): caché larga para los recursos estáticos; el HTML no se guarda en caché, para que apunte siempre a los archivos nuevos.
- **Versiones conviviendo:** tras un despliegue hay usuarios con la versión anterior abierta. Los fragmentos antiguos deben seguir disponibles un tiempo, y la API debe ser compatible con ambos.
- **Banderas de función:** separar desplegar de liberar; activar por porcentaje o por grupo; apagar sin desplegar.
- **Reversa** en un paso.

### Observabilidad del frontend

| Qué | Para qué | Herramientas |
|---|---|---|
| Seguimiento de errores | Excepciones no controladas, con traza legible (*source maps*), versión, navegador y pasos previos | Sentry y similares |
| Monitoreo de usuarios reales (RUM) | LCP, INP y CLS medidos en dispositivos reales, por página y por percentil | Biblioteca `web-vitals`, servicios de RUM |
| Monitoreo sintético | Recorridos críticos ejecutados cada pocos minutos desde fuera | Pruebas programadas |
| Analítica de producto | Qué se usa y dónde se abandona | Herramienta de analítica |
| Trazas | Unir la acción del usuario con la petición al backend mediante el identificador de traza | OpenTelemetry |

Datos de laboratorio (Lighthouse en tu máquina) y datos de campo (usuarios reales) cuentan historias distintas. Las decisiones se toman con los de campo.

### Qué registrar y qué no

Sí: la versión de la aplicación, la ruta, el tipo de error, el identificador de traza. No: contraseñas, tokens, datos personales, contenido de formularios. La privacidad y el consentimiento son requisitos.

### Manejo de errores en la interfaz

- Un manejador global que reporta lo no controlado.
- **Límites de error** por sección: si falla un componente, se muestra un mensaje en esa zona y el resto de la página sigue funcionando.
- Errores esperados (validación, sin permiso) no se reportan como fallos.
- Al usuario: qué pasó, qué puede hacer. Nunca una traza.

### Dependencias

Cada dependencia es peso, superficie de ataque y trabajo de mantenimiento. Antes de agregar una: ¿cuánto pesa?, ¿se mantiene?, ¿se puede resolver con la plataforma o con veinte líneas propias? Actualizaciones pequeñas y frecuentes (automatizadas) en lugar de una migración enorme cada dos años.

## Ejemplo: flujo de integración continua

```yaml
name: calidad
on:
  pull_request:
  push:
    branches: [main]

jobs:
  calidad:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-node@v5
        with: { node-version: 26, cache: npm }
      - run: npm ci
      - run: npm run lint
      - run: npm run typecheck
      - run: npm test -- --coverage
      - run: npm run build # falla si se supera el presupuesto de tamaño
      - run: npm audit --audit-level=high
  e2e:
    needs: calidad
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-node@v5
        with: { node-version: 26, cache: npm }
      - run: npm ci
      - run: npx playwright install --with-deps chromium
      - run: npm run build
      - run: npx playwright test # contra la aplicación construida (next start); accesibilidad con axe en cada pantalla
      - run: npm run lighthouse # arranca y apaga su propio next start; falla si baja el presupuesto de rendimiento
  desplegar:
    needs: e2e
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - run: echo "Desplegar el artefacto ya construido" # el paso concreto depende de la plataforma
```

Cada trabajo corre en una máquina limpia: por eso cada uno clona el repositorio (`checkout`) e instala dependencias, y un trabajo sin `runs-on` ni pasos no es válido. Angular falla en `build` si se supera el presupuesto definido en `angular.json`; Next.js no trae presupuestos, así que el *script* `build` debe ejecutar, después de `next build`, una comprobación propia del tamaño (o una herramienta como size-limit).

## Errores comunes

- Calidad que depende de que alguien se acuerde.
- *Source maps* sin subir: errores ilegibles en producción.
- Mirar solo Lighthouse en la máquina propia.
- Sin presupuesto de tamaño: el paquete crece hasta que alguien nota la lentitud.
- Guardar en caché el HTML y dejar a los usuarios con una versión rota.
- Borrar los fragmentos antiguos en cada despliegue: errores de carga para quien tenía la pestaña abierta.
- Reportar como errores los fallos esperados: ruido que tapa lo importante.
- Registrar datos personales.
- Dependencias sin actualizar hasta que la migración es un proyecto.

## Taller

1. Monta el flujo de integración continua de una aplicación tuya con todas las puertas de la tabla.
2. Configura entornos de vista previa por *pull request*.
3. Integra un servicio de seguimiento de errores con *source maps* y provoca un error para verlo.
4. Mide las métricas web en campo con la biblioteca `web-vitals` y envíalas a donde puedas consultarlas.
5. Agrega límites de error a dos secciones.
6. Implementa una bandera de función y úsala para liberar a un porcentaje.
7. Revisa tus dependencias: peso, mantenimiento y cuáles podrías eliminar.

Rama `taller/fe-02-calidad-entrega`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Cómo te enteras de un error que solo ocurre en ciertos dispositivos?
2. Datos de laboratorio frente a datos de campo.
3. ¿Qué pasa con los usuarios que tienen abierta la versión anterior tras un despliegue?
4. ¿Qué automatizarías antes de permitir una fusión?
5. ¿Cómo decides si agregar una dependencia?
