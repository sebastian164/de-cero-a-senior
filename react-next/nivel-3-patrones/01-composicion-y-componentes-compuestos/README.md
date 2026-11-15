# 01 — Composición y componentes compuestos

El catálogo clásico de patrones está en la [ruta Java](../../../java/nivel-3-patrones/). Este nivel cubre cómo aparecen en React y los que son propios de la interfaz.

## El problema

Hay que reutilizar estructura (una tarjeta, un diálogo, unas pestañas) en muchos lugares, con contenido distinto en cada uno. Las salidas malas: copiar y pegar, o un componente con treinta propiedades para cubrir cada caso.

## El patrón

React no tiene herencia de componentes en la práctica: todo se resuelve por **composición**.

| Técnica | Qué es |
|---|---|
| `children` | El componente no sabe qué contiene |
| Huecos con nombre | Propiedades que reciben elementos: `pie={<Acciones />}` |
| **Componentes compuestos** | Varios componentes que trabajan juntos y comparten estado implícito por contexto |
| Componentes sin estilo (*headless*) | Aportan comportamiento y accesibilidad; quien los usa pone el marcado y el estilo |

Los componentes compuestos son el equivalente de `<select>` y `<option>` en HTML: piezas que solo tienen sentido juntas y cuya API es el propio JSX.

## Ejemplo: pestañas

```tsx
'use client';

interface EstadoPestanas {
  activa: string;
  activar: (id: string) => void;
  idBase: string;
}

const ContextoPestanas = createContext<EstadoPestanas | null>(null);

function usePestanas() {
  const contexto = useContext(ContextoPestanas);
  if (!contexto) throw new Error('Los componentes de Pestanas deben usarse dentro de <Pestanas>');
  return contexto;
}

export function Pestanas({ inicial, children }: { inicial: string; children: React.ReactNode }) {
  const [activa, activar] = useState(inicial);
  const idBase = useId();
  return <ContextoPestanas value={{ activa, activar, idBase }}>{children}</ContextoPestanas>;
}

function Lista({ children }: { children: React.ReactNode }) {
  return <div role="tablist">{children}</div>;
}

function Pestana({ id, children }: { id: string; children: React.ReactNode }) {
  const { activa, activar, idBase } = usePestanas();
  const seleccionada = activa === id;
  return (
    <button
      type="button"
      role="tab"
      id={`${idBase}-tab-${id}`}
      aria-selected={seleccionada}
      aria-controls={`${idBase}-panel-${id}`}
      tabIndex={seleccionada ? 0 : -1}
      onClick={() => activar(id)}
    >
      {children}
    </button>
  );
}

function Panel({ id, children }: { id: string; children: React.ReactNode }) {
  const { activa, idBase } = usePestanas();
  if (activa !== id) return null;
  return <div role="tabpanel" id={`${idBase}-panel-${id}`} aria-labelledby={`${idBase}-tab-${id}`}>{children}</div>;
}

Pestanas.Lista = Lista;
Pestanas.Pestana = Pestana;
Pestanas.Panel = Panel;
```

```tsx
<Pestanas inicial="datos">
  <Pestanas.Lista>
    <Pestanas.Pestana id="datos">Datos</Pestanas.Pestana>
    <Pestanas.Pestana id="pedidos">Pedidos <Insignia>{pendientes}</Insignia></Pestanas.Pestana>
  </Pestanas.Lista>
  <Pestanas.Panel id="datos"><DatosCliente /></Pestanas.Panel>
  <Pestanas.Panel id="pedidos"><PedidosCliente /></Pestanas.Panel>
</Pestanas>
```

Quien las usa decide el contenido, el orden y qué va en cada pestaña. El componente aporta el estado y la accesibilidad. (Una implementación completa agrega navegación con flechas; para producción, usa una biblioteca de primitivas accesibles.)

### Composición y componentes de servidor

`children` es además la herramienta que permite combinar servidor y cliente: un componente de cliente puede recibir como `children` un componente de servidor ya renderizado, sin convertirlo en cliente.

```tsx
<PanelDesplegable titulo="Historial">       {/* cliente: maneja abrir y cerrar */}
  <HistorialDePedidos clienteId={id} />     {/* servidor: consulta la base de datos */}
</PanelDesplegable>
```

## Alternativas

- Una propiedad `variante`, si las diferencias son pocas.
- Un arreglo de configuración (`pestanas={[{ id, titulo, contenido }]}`): más simple de usar, menos flexible.
- Bibliotecas de primitivas accesibles sin estilo (Radix, React Aria, Base UI): resuelven lo difícil.

## Errores comunes

- Componentes compuestos donde un arreglo de datos bastaba.
- Contexto sin comprobar que existe el proveedor: errores crípticos.
- Reimplementar a mano la accesibilidad de pestañas, menús o diálogos.
- Inspeccionar o clonar `children` para inyectar propiedades: frágil; usa contexto.
- Marcar como cliente todo el árbol en lugar de pasar el contenido de servidor como `children`.

## Taller

1. Completa las pestañas con navegación por teclado (flechas, Inicio, Fin).
2. Construye un acordeón como componentes compuestos.
3. Construye un diálogo con huecos para título, cuerpo y acciones, usando el elemento `<dialog>` nativo.
4. Implementa el mismo acordeón con una API basada en un arreglo y compara flexibilidad y simplicidad.
5. En Next.js, crea un componente de cliente que reciba contenido de servidor como `children` y verifica qué JavaScript llega al navegador.

Rama `taller/rx-n3-01-composicion`.

## Preguntas senior

1. ¿Qué ventajas tiene una API de componentes compuestos frente a una de configuración?
2. ¿Cómo comparten estado los componentes compuestos?
3. ¿Qué es un componente *headless* y cuándo conviene?
4. ¿Cómo combinas componentes de servidor dentro de uno de cliente?
