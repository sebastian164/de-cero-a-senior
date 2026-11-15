# 01 — Estructura por funcionalidades y carga diferida

Los principios (separación de responsabilidades, modularidad) están en el [Nivel 5 de Java](../../../java/nivel-05-arquitectura/). Aquí, cómo se concretan en una aplicación Angular.

## Idea

En una ferretería bien organizada, todo lo de plomería está en un pasillo. No hay un pasillo de "cosas metálicas" y otro de "cosas de plástico". Organizar por lo que se busca junto, no por el material del que está hecho.

## Lo esencial

### Por tipo técnico o por funcionalidad

```
Por tipo (evitar)             Por funcionalidad (preferir)
src/app/                      src/app/
├── components/               ├── pedidos/
├── services/                 │   ├── datos/          API, adaptadores, DTO
├── models/                   │   ├── dominio/        modelos y reglas puras
├── pipes/                    │   ├── estado/         almacén
└── guards/                   │   ├── ui/             componentes de presentación
                              │   ├── paginas/        contenedores con ruta
                              │   ├── pedidos.rutas.ts
                              │   └── index.ts        lo único público
                              ├── catalogo/
                              ├── compartido/         interfaz y utilidades genéricas
                              └── nucleo/             sesión, configuración, interceptores
```

Por tipo, agregar un campo a "pedido" toca cinco carpetas. Por funcionalidad, todo lo que cambia junto está junto, y una funcionalidad puede moverse, cargarse de forma diferida o eliminarse entera.

### Reglas de dependencia

| Carpeta | Puede depender de | No puede depender de |
|---|---|---|
| Una funcionalidad | `compartido`, `nucleo`, la API pública de otra funcionalidad | El interior de otra funcionalidad |
| `compartido` | Nada de la aplicación | Funcionalidades, `nucleo` |
| `nucleo` | `compartido` | Funcionalidades |
| `ui` dentro de una funcionalidad | `dominio`, `compartido` | `datos`, `estado` |
| `dominio` | Nada | Angular, `datos`, `ui` |

Sin ciclos entre funcionalidades. Estas reglas se **verifican automáticamente** con reglas de límites de ESLint o con las restricciones de módulos de Nx; una convención sin verificación se erosiona.

### API pública

Cada funcionalidad exporta por su `index.ts` solo lo que otras necesitan (normalmente sus rutas y, a lo sumo, algún tipo o servicio). Lo demás es interno.

### Carga diferida

```ts
export const rutas: Routes = [
  { path: '', loadComponent: () => import('./inicio/inicio.component') },
  { path: 'pedidos', loadChildren: () => import('./pedidos/pedidos.rutas') },
  { path: 'admin', canMatch: [requiereRol('admin')], loadChildren: () => import('./admin/admin.rutas') },
];

// pedidos/pedidos.rutas.ts
export default [
  {
    path: '',
    providers: [AlmacenPedidos],                       // vive mientras la funcionalidad esté activa
    children: [
      { path: '', component: ListaPedidosPagina },
      { path: ':id', component: DetallePedidoPagina },
    ],
  },
] satisfies Routes;
```

El código de cada funcionalidad se descarga cuando se navega a ella. Dentro de una pantalla, `@defer` difiere partes pesadas (una gráfica, un editor) hasta que son visibles o se necesitan.

### `compartido` bajo control

`compartido` tiende a convertirse en el cajón donde va todo. Criterio de entrada: lo usan al menos dos funcionalidades, no contiene lógica de negocio y no conoce ninguna funcionalidad.

## Errores comunes

- Carpetas por tipo técnico en la raíz.
- Importaciones profundas al interior de otra funcionalidad.
- `compartido` con lógica de negocio.
- Todo cargado de entrada: el paquete inicial pesa megas.
- Servicios `providedIn: 'root'` para estado que es de una sola funcionalidad.
- Dependencias cíclicas entre funcionalidades.
- Archivos de exportación (*barrels*) que reexportan todo y rompen la eliminación de código no usado.

## Taller

1. Reorganiza una aplicación tuya (o diseña la estructura de una tienda en línea) por funcionalidades.
2. Dibuja el grafo de dependencias entre funcionalidades; elimina los ciclos.
3. Configura la verificación automática de límites y haz que una importación indebida falle.
4. Carga de forma diferida todas las funcionalidades y mide el tamaño del paquete inicial antes y después.
5. Difiere con `@defer` un componente pesado.
6. Escribe los criterios de entrada a `compartido` para tu equipo.

Rama `taller/ng-n5-01-estructura`.

## Preguntas senior

1. ¿Por qué organizar por funcionalidad y no por tipo?
2. ¿Cómo impides que las funcionalidades se enreden con el tiempo?
3. ¿Qué va en `compartido` y qué no?
4. ¿Qué decide dónde se provee un servicio?
5. ¿Cómo reduces el tamaño del paquete inicial?
