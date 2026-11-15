# 02 — Hooks personalizados

## El problema

Varios componentes necesitan la misma lógica con estado: paginar, controlar un formulario, saber si un elemento está visible, manejar un diálogo. Copiarla lleva a divergencias; meterla en un componente envoltorio enreda el árbol.

## El patrón

Un hook personalizado extrae lógica con estado a una función reutilizable. Comparte **lógica**, no **estado**: cada componente que lo llama obtiene su propia copia.

Es la forma en que React resuelve lo que en otros lenguajes harían Strategy, Template Method o una clase base: comportamiento que se compone llamando funciones.

### Un buen hook

| Característica | Por qué |
|---|---|
| Nombre que dice qué hace: `usePaginacion`, no `useUtilidades` | Se entiende sin abrirlo |
| Hace una sola cosa | Se compone con otros |
| API pequeña y estable | Quien lo usa no depende de su interior |
| Oculta el cómo | Se puede cambiar la implementación |
| Limpia lo que crea | Sin fugas |
| Se puede probar | Con `renderHook`, o mejor: la lógica pura afuera y el hook delgado |

## Ejemplo 1: lógica de interfaz

```tsx
interface Paginacion<T> {
  pagina: number;
  totalPaginas: number;
  elementos: readonly T[];
  siguiente: () => void;
  anterior: () => void;
  irA: (pagina: number) => void;
}

export function usePaginacion<T>(todos: readonly T[], porPagina = 10): Paginacion<T> {
  const [pagina, setPagina] = useState(1);
  const totalPaginas = Math.max(1, Math.ceil(todos.length / porPagina));
  const actual = Math.min(pagina, totalPaginas);         // si la lista se acorta, no queda fuera de rango

  const irA = (destino: number) => setPagina(Math.min(Math.max(1, destino), totalPaginas));

  return {
    pagina: actual,
    totalPaginas,
    elementos: todos.slice((actual - 1) * porPagina, actual * porPagina),
    siguiente: () => irA(actual + 1),
    anterior: () => irA(actual - 1),
    irA,
  };
}
```

## Ejemplo 2: fachada sobre una dependencia

```tsx
// El componente no sabe qué biblioteca de datos hay debajo
export function usePedidos(filtro: FiltroPedidos) {
  const consulta = useQuery({
    queryKey: ['pedidos', filtro],
    queryFn: ({ signal }) => listarPedidos(filtro, signal),
  });
  return {
    pedidos: consulta.data ?? [],
    cargando: consulta.isPending,
    error: consulta.error ? 'No se pudieron cargar los pedidos' : null,
    recargar: consulta.refetch,
  };
}
```

Si mañana se cambia de biblioteca, se modifica este archivo y ningún componente.

## Ejemplo 3: estrategia como parámetro

```tsx
export function useOrdenado<T>(elementos: readonly T[], comparar: (a: T, b: T) => number) {
  return useMemo(() => elementos.toSorted(comparar), [elementos, comparar]);
}

// La estrategia se define fuera del componente para que su referencia sea estable
const porFechaDescendente = (a: Pedido, b: Pedido) => b.creadoEn.getTime() - a.creadoEn.getTime();
const ordenados = useOrdenado(pedidos, porFechaDescendente);
```

## Separar la lógica pura

La parte más valiosa de un hook suele ser lógica que no necesita React. Extráela:

```ts
// paginacion.ts — pura, probable con una línea
export const paginasTotales = (total: number, porPagina: number) => Math.max(1, Math.ceil(total / porPagina));
export const rebanar = <T,>(lista: readonly T[], pagina: number, porPagina: number) =>
  lista.slice((pagina - 1) * porPagina, pagina * porPagina);
```

El hook queda como un conector delgado entre esa lógica y el estado de React.

## Alternativas

- **Una función pura**, si no hay estado ni efectos. No todo lo reutilizable es un hook.
- **Un componente**, si lo que se comparte es interfaz.
- **Contexto o un almacén**, si varios componentes deben compartir el **mismo** estado.
- **Componentes de servidor**, para la obtención de datos.

## Errores comunes

- Hooks que son un cajón con todo lo de una pantalla.
- Llamar `use…` a funciones que no usan hooks.
- Esperar que dos componentes compartan estado por usar el mismo hook.
- Devolver objetos o funciones nuevos en cada renderizado cuando quien los consume los usa como dependencias.
- Hooks con diez parámetros de configuración.
- Efectos dentro del hook para derivar estado.
- Esconder en un hook obtención de datos con `useEffect` casera.

## Taller

1. Escribe `useInterruptor`, `useAlmacenLocal`, `useDebounce` y `useVisibleEnPantalla` (con `IntersectionObserver`).
2. Escribe `useFormulario` mínimo (valores, errores, tocado, enviar) y úsalo en dos formularios distintos. Luego compáralo con una biblioteca.
3. Toma un componente tuyo con mucha lógica y extráela: primero a funciones puras, luego a hooks.
4. Prueba la lógica pura sin React y un hook con `renderHook`.
5. Envuelve tu biblioteca de datos en hooks de dominio, de modo que ningún componente la importe directamente.

Rama `taller/rx-n3-02-hooks`.

## Preguntas senior

1. ¿Qué comparte un hook personalizado: lógica o estado?
2. ¿Cuándo algo debe ser un hook y cuándo una función pura?
3. ¿Qué ventaja tiene ocultar una biblioteca tras un hook propio?
4. ¿Cómo evitas renderizados innecesarios por lo que devuelve un hook?
