# 02 — OCP en React

Explicación completa: [OCP en la ruta Java](../../../java/nivel-02-solid/02-ocp/).

## Idea

Un componente bien diseñado se extiende **desde afuera**, componiéndolo, sin abrirlo para agregar otra propiedad booleana u otro `if`.

## Mecanismos de extensión en React

| Mecanismo | Extiende |
|---|---|
| `children` | El contenido |
| Propiedades que reciben elementos (`icono={<Icono />}`) | Huecos con nombre (*slots*) |
| Propiedades que reciben componentes o funciones de renderizado | Cómo se pinta cada elemento |
| Componentes compuestos (`<Tabs><Tabs.Tab/></Tabs>`) | La estructura interna |
| Reenviar el resto de las propiedades (`{...resto}`) | Atributos del elemento nativo |
| Mapa de tipo a componente | Variantes |
| Hooks | El comportamiento |

## Antes

```tsx
interface BotonProps {
  texto: string;
  primario?: boolean;
  peligro?: boolean;
  conIcono?: boolean;
  iconoDerecha?: boolean;
  cargando?: boolean;
  comoEnlace?: boolean;
  href?: string;
  // cada necesidad nueva agrega una propiedad y un if
}

function Boton(p: BotonProps) {
  if (p.comoEnlace) return <a href={p.href} className={p.primario ? 'btn primario' : 'btn'}>{p.texto}</a>;
  return (
    <button className={p.peligro ? 'btn peligro' : p.primario ? 'btn primario' : 'btn'}>
      {p.conIcono && !p.iconoDerecha && <IconoGuardar />}
      {p.cargando ? 'Cargando…' : p.texto}
      {p.conIcono && p.iconoDerecha && <IconoGuardar />}
    </button>
  );
}
```

Siete propiedades, combinaciones que no tienen sentido (`primario` y `peligro` a la vez) y un único ícono posible.

## Después

```tsx
type BotonProps = React.ComponentProps<'button'> & {
  variante?: 'primario' | 'secundario' | 'peligro';      // una unión: sin combinaciones imposibles
  inicio?: React.ReactNode;                              // huecos: cualquier contenido
  fin?: React.ReactNode;
};

export function Boton({ variante = 'secundario', inicio, fin, children, className, ...resto }: BotonProps) {
  return (
    <button type="button" className={clsx('btn', `btn-${variante}`, className)} {...resto}>
      {inicio}
      {children}
      {fin}
    </button>
  );
}

// Uso: se extiende componiendo, sin tocar Boton
<Boton variante="primario" inicio={<IconoGuardar />} disabled={guardando}>
  {guardando ? 'Guardando…' : 'Guardar'}
</Boton>
```

Variantes por tipo sin `if`, con verificación del compilador:

```tsx
const VISTAS: Record<Notificacion['tipo'], React.ComponentType<{ datos: any }>> = {
  pedido: NotificacionPedido,
  pago: NotificacionPago,
  alerta: NotificacionAlerta,
};

function VistaNotificacion({ notificacion }: { notificacion: Notificacion }) {
  const Vista = VISTAS[notificacion.tipo];
  return <Vista datos={notificacion} />;
}
```

Si se agrega un tipo a la unión, `Record` obliga a registrar su componente.

Lista genérica con función de renderizado:

```tsx
interface ListaProps<T> {
  elementos: readonly T[];
  clave: (elemento: T) => string;
  children: (elemento: T) => React.ReactNode;
  vacio?: React.ReactNode;
}

export function Lista<T>({ elementos, clave, children, vacio = <p>Sin elementos.</p> }: ListaProps<T>) {
  if (elementos.length === 0) return <>{vacio}</>;
  return <ul>{elementos.map((e) => <li key={clave(e)}>{children(e)}</li>)}</ul>;
}
```

## Cuándo no

Con dos o tres variantes estables, una propiedad `variante` basta. La composición total (todo son huecos) hace componentes flexibles pero difíciles de usar bien: equilibra.

## Errores comunes

- Propiedades booleanas que se multiplican.
- Componentes "configurables por objeto" con un esquema propio en lugar de JSX.
- No reenviar las propiedades nativas (`disabled`, `aria-*`, `ref`).
- Abstraer antes de la segunda variante.
- Copiar y pegar un componente para cambiarle un detalle.

## Taller

1. Rediseña un componente tuyo con más de cinco propiedades booleanas usando composición.
2. Construye una tarjeta con huecos para encabezado, cuerpo y pie.
3. Construye la lista genérica y úsala con dos tipos de datos.
4. Implementa el mapa de notificaciones y agrega un tipo.
5. Señala un caso donde una simple propiedad `variante` era mejor.

Rama `taller/rx-n2-02-ocp`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Por qué las propiedades booleanas escalan mal?
2. `children`, huecos con nombre y funciones de renderizado: ¿cuándo cada uno?
3. ¿Cómo haces que agregar una variante sea verificado por el compilador?
