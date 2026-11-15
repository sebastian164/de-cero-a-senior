# 05 — Monorepos, bibliotecas y microfrontends

Requisito: [Modularidad](../../../java/nivel-5-arquitectura/09-modularidad/) y [monolitos frente a microservicios](../../../java/nivel-5-arquitectura/10-monolitos-vs-microservicios/). La discusión es la misma, trasladada a la interfaz.

## Idea

Cuando varios equipos trabajan sobre la misma aplicación, hay dos preguntas distintas: cómo se organiza el **código** (¿un repositorio o varios?) y cómo se organiza el **despliegue** (¿una aplicación o varias?). Confundirlas lleva a soluciones caras para problemas que no se tenían.

## Lo esencial

### Tres opciones, de menor a mayor costo

| Opción | Código | Despliegue | Resuelve |
|---|---|---|---|
| Aplicación modular | Una aplicación por funcionalidades | Uno | Orden interno |
| Monorepo con bibliotecas | Varias aplicaciones y bibliotecas en un repositorio | Uno por aplicación | Compartir código entre aplicaciones; límites verificados; construcciones incrementales |
| Microfrontends | Aplicaciones independientes compuestas en ejecución | Uno por equipo | Autonomía de despliegue entre equipos |

### Monorepo

Herramientas como Nx aportan:

- **Bibliotecas** con API pública: en lugar de carpetas, paquetes internos.
- **Reglas de dependencia** por etiquetas (tipo: `ui`, `datos`, `dominio`; ámbito: `pedidos`, `compartido`) verificadas por el *linter*.
- **Grafo de dependencias** visible.
- **Solo lo afectado:** al cambiar una biblioteca, se construyen y prueban únicamente los proyectos que dependen de ella.
- **Caché de construcción** local y remota.

```
libs/
├── pedidos/
│   ├── dominio          tipo:dominio   ambito:pedidos
│   ├── datos            tipo:datos     ambito:pedidos
│   ├── ui               tipo:ui        ambito:pedidos
│   └── funcionalidad    tipo:funcionalidad
└── compartido/
    ├── ui               sistema de diseño
    └── util
apps/
├── tienda
└── administracion
```

Es el equivalente frontend del monolito modular: un repositorio, fronteras verificadas.

### Sistema de diseño

Una biblioteca de componentes de interfaz y *tokens* (colores, espaciado, tipografía) compartida. Da coherencia visual y accesibilidad resuelta una vez. Debe tener versión, documentación con ejemplos, y dueños claros.

### Microfrontends

Varias aplicaciones construidas y desplegadas por separado, que se componen en el navegador (por ejemplo con *Module Federation* o *Native Federation*): una aplicación anfitriona carga las demás en ejecución.

**Lo que dan:** cada equipo despliega cuando quiere, sin coordinar una versión común.

**Lo que cuestan:**

- Dependencias compartidas: versiones de Angular y de bibliotecas que deben ser compatibles.
- Rendimiento: más peticiones y posible código duplicado.
- Coherencia visual y de experiencia entre equipos.
- Comunicación entre microfrontends (eventos, URL) y estado compartido (sesión).
- Pruebas de integración y depuración más difíciles.
- Infraestructura de despliegue y versionado.

Resuelven un problema **organizacional** (muchos equipos bloqueándose en un despliegue común), no uno técnico. Con uno, dos o tres equipos, un monorepo con buena modularidad da casi todos los beneficios a una fracción del costo.

### Cómo decidir

| Situación | Opción |
|---|---|
| Un equipo, una aplicación | Aplicación modular |
| Varias aplicaciones que comparten código, o un equipo en crecimiento | Monorepo con bibliotecas |
| Muchos equipos autónomos, ciclos de entrega distintos, dominios bien separados | Microfrontends |
| Migración gradual de una aplicación heredada | Microfrontends como estrategia de estrangulamiento |

## Errores comunes

- Microfrontends con un equipo de cinco personas.
- Microfrontends que comparten estado y se despliegan juntos: un monolito distribuido en el navegador.
- Bibliotecas "compartidas" con lógica de negocio que acoplan a todas las aplicaciones.
- Monorepo sin reglas de dependencia: un enredo más grande.
- Sistema de diseño sin dueño, que cada equipo bifurca.
- Versiones distintas del framework conviviendo sin un plan.

## Taller

1. Convierte una aplicación en un espacio de trabajo con bibliotecas por ámbito y tipo. Define las etiquetas y las reglas, y haz fallar una dependencia indebida.
2. Visualiza el grafo de dependencias y comenta lo que encuentres.
3. Cambia una biblioteca y comprueba que solo se construye y prueba lo afectado.
4. Extrae tres componentes a una biblioteca de sistema de diseño con ejemplos documentados.
5. Monta un anfitrión y un microfrontend remoto. Anota cada problema que aparezca (dependencias, estilos, sesión).
6. Escribe un ADR: para un contexto concreto (tamaño de equipo, número de aplicaciones), ¿qué opción eliges y por qué?

Rama `taller/ng-n5-05-monorepo-microfrontends`.

## Preguntas senior

1. ¿Qué problema resuelve un monorepo y cuál los microfrontends?
2. ¿Cuándo los microfrontends son una mala decisión?
3. ¿Cómo se comunican dos microfrontends sin acoplarse?
4. ¿Cómo gobiernas un sistema de diseño usado por varios equipos?
5. ¿Qué es un monolito distribuido en el frontend?
