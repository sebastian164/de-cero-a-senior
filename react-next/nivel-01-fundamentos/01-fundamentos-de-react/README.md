# 01 — Fundamentos de React

## Idea

En React, la interfaz es una función del estado: dados unos datos, la pantalla se ve de cierta manera. Tú no dices "agrega una fila a la tabla"; dices "la tabla muestra estas filas", cambias los datos y React se encarga de actualizar lo necesario. Pensar así, de forma declarativa, es el cambio mental más importante.

## Lo esencial

| Concepto | Qué es |
|---|---|
| **Componente** | Una función que recibe propiedades y devuelve lo que se debe mostrar |
| **JSX** | Sintaxis parecida a HTML dentro de TypeScript; se compila a llamadas de función |
| **Propiedades (*props*)** | Datos que el padre le pasa al hijo. Son de solo lectura |
| **Estado** | Datos que el componente recuerda entre renderizados. Cambiarlo provoca un nuevo renderizado |
| **Renderizado** | React llama a tu función para saber qué mostrar; luego compara y aplica solo las diferencias al DOM |
| **Eventos** | Funciones pasadas como propiedades: `onClick={…}` |
| **Listas** | `array.map(…)`, con una `key` estable y única por elemento |
| **`children`** | Lo que se anida dentro del componente: la base de la composición |

### Tres reglas

1. **Los componentes son funciones puras de sus propiedades y su estado.** No modifican lo que reciben ni hacen efectos durante el renderizado.
2. **El estado es inmutable.** No se modifica: se reemplaza con una copia. `setItems([...items, nuevo])`, nunca `items.push(nuevo)`.
3. **El flujo de datos es de una sola dirección.** Los datos bajan por propiedades; los eventos suben por funciones. Si dos componentes necesitan el mismo dato, el estado se **eleva** a su ancestro común.

### Dónde vive el estado

Lo más cerca posible de donde se usa. Y solo es estado lo que no se puede calcular: si algo se deriva de las propiedades o de otro estado, se calcula durante el renderizado.

## Antes

```tsx
function Pedidos() {
  const [pedidos, setPedidos] = useState<any[]>([]);
  const [total, setTotal] = useState(0);              // derivado: no debería ser estado

  function agregar(pedido: any) {
    pedidos.push(pedido);                             // modifica el estado en el sitio
    setPedidos(pedidos);                              // misma referencia: React no vuelve a pintar
    setTotal(total + pedido.valor);                   // y hay que acordarse de sincronizarlo
  }

  return (
    <div>
      {pedidos.map((p, i) => (
        <div key={i} onClick={() => alert(p.id)}>     {/* índice como clave; div clicable */}
          {p.cliente} - {p.valor}
        </div>
      ))}
      <p>Total: {total}</p>
    </div>
  );
}
```

## Después

```tsx
interface Pedido {
  readonly id: string;
  readonly cliente: string;
  readonly valor: number;
}

const sumar = (pedidos: readonly Pedido[]) => pedidos.reduce((suma, p) => suma + p.valor, 0);

function PaginaPedidos() {
  const [pedidos, setPedidos] = useState<readonly Pedido[]>([]);
  const total = sumar(pedidos);                       // derivado: se calcula al renderizar

  function agregar(pedido: Pedido) {
    setPedidos((actuales) => [...actuales, pedido]);  // nuevo arreglo; forma funcional
  }

  function eliminar(id: string) {
    setPedidos((actuales) => actuales.filter((p) => p.id !== id));
  }

  return (
    <section>
      <ListaPedidos pedidos={pedidos} onEliminar={eliminar} />
      <p>Total: {total.toLocaleString('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })}</p>
    </section>
  );
}

interface ListaPedidosProps {
  readonly pedidos: readonly Pedido[];
  readonly onEliminar: (id: string) => void;
}

function ListaPedidos({ pedidos, onEliminar }: ListaPedidosProps) {
  if (pedidos.length === 0) return <p>No hay pedidos.</p>;

  return (
    <ul>
      {pedidos.map((pedido) => (
        <li key={pedido.id}>
          {pedido.cliente}
          <button type="button" onClick={() => onEliminar(pedido.id)}>Eliminar</button>
        </li>
      ))}
    </ul>
  );
}
```

`ListaPedidos` es un componente de presentación: recibe datos y avisa de eventos. Se puede usar en cualquier pantalla y probar sin nada más.

## Errores comunes

- Modificar el estado en el sitio.
- Guardar como estado lo que se puede calcular.
- El índice como `key` en listas que cambian: React confunde los elementos y el estado de cada fila se mezcla.
- Copiar propiedades al estado (`useState(props.valor)`) y esperar que se actualicen.
- Leer el estado justo después de cambiarlo y esperar el valor nuevo: la actualización se aplica en el siguiente renderizado.
- Componentes definidos dentro de otros componentes: se recrean y pierden su estado en cada renderizado.
- `any` en propiedades.
- Un solo componente de 500 líneas.

## Taller

Construye una lista de tareas:

1. Agregar, completar, eliminar y filtrar (todas, pendientes, hechas).
2. El contador de pendientes y la lista filtrada deben ser valores derivados.
3. Al menos tres componentes: página (estado), lista y elemento (presentación).
4. Todo tipado, sin `any`.
5. Introduce a propósito el error del índice como clave en una lista con campos de texto y describe qué se rompe.
6. Extrae la lógica (filtrar, contar, alternar) a funciones puras y pruébalas sin React.

Rama `taller/rx-n1-01-react`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué significa que la interfaz es una función del estado?
2. ¿Por qué el estado debe ser inmutable?
3. ¿Qué hace `key` y qué falla al usar el índice?
4. ¿Cuándo algo debe ser estado y cuándo un valor derivado?
5. ¿Qué es elevar el estado y cuándo se hace?
