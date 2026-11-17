# Sistema de diseño (`@org/design-system`)

Tokens (`src/lib/tokens.css`) y tres componentes con ejemplos documentados. Etiquetas Nx: `scope:shared`, `type:ui` (solo puede depender de `scope:shared`).

```html
<ds-tarjeta titulo="Pedido 7">
  <ds-etiqueta tono="exito">Pagado</ds-etiqueta>
  <ds-boton variante="peligro" (pulsado)="cancelar()">Cancelar</ds-boton>
</ds-tarjeta>
```

| Componente | Entradas | Salidas | Accesibilidad |
|---|---|---|---|
| `ds-boton` | `variante` (`primario` \| `secundario` \| `peligro`), `tipo`, `deshabilitado` | `pulsado` | `<button>` real, foco visible |
| `ds-etiqueta` | `tono` (`neutro` \| `exito` \| `error`) | | El significado va en el texto, no solo en el color |
| `ds-tarjeta` | `titulo` (obligatorio) | | El título es un `h3` real |

Dueño: el equipo de plataforma. Cambios con versión semántica y una prueba por componente (`nx test design-system`).
