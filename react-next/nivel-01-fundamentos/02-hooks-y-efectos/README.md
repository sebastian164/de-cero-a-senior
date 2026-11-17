# 02 — Hooks y efectos

## Idea

Un componente es una función que se ejecuta muchas veces. Los *hooks* son la forma de que esa función "recuerde" cosas entre una ejecución y otra y se conecte con el mundo exterior. El más malentendido es `useEffect`: no es "código que corre cuando cambia algo", sino una forma de **sincronizar** el componente con un sistema externo.

## Lo esencial

| Hook | Para qué |
|---|---|
| `useState` | Estado local |
| `useReducer` | Estado con transiciones complejas |
| `useEffect` | Sincronizar con algo externo a React: suscripciones, temporizadores, APIs del navegador |
| `useRef` | Un valor que persiste sin provocar renderizado; o una referencia a un nodo del DOM |
| `useMemo` / `useCallback` | Guardar un cálculo o una función entre renderizados |
| `useContext` | Leer un valor provisto por un ancestro |
| `useId` | Identificadores estables (accesibilidad) |
| `use` | Leer una promesa o un contexto durante el renderizado |
| `useTransition`, `useOptimistic`, `useActionState` | Transiciones, interfaz optimista y estado de acciones |

### Reglas de los hooks

Se llaman solo en el nivel superior de un componente o de otro hook, nunca dentro de condiciones, bucles o funciones anidadas. React los identifica por el orden de llamada.

### `useEffect`: cuándo sí y cuándo no

**Sí:** conectarse a algo que React no controla: un WebSocket, un oyente de eventos de `window`, una biblioteca de mapas, un temporizador.

**No** (los casos más frecuentes de mal uso):

| Quiero | En lugar de un efecto |
|---|---|
| Calcular algo a partir de propiedades o estado | Calcularlo durante el renderizado |
| Reaccionar a una acción del usuario | Hacerlo en el manejador del evento |
| Reiniciar el estado cuando cambia una propiedad | Darle una `key` distinta al componente |
| Obtener datos | Un componente de servidor, o una biblioteca de datos (sesiones del Nivel 5) |
| Avisar al padre de un cambio | Llamar al padre en el mismo manejador que cambia el estado |

Todo efecto debe **limpiarse**: la función que devuelve se ejecuta antes de la siguiente ejecución y al desmontar. Y su lista de dependencias debe incluir todo lo que usa: no se "ajusta" para que corra menos.

### Hooks personalizados

Una función cuyo nombre empieza por `use` y que usa otros hooks. Es la forma de reutilizar **lógica con estado** entre componentes, sin herencia ni componentes envoltorio.

## Antes

```tsx
function Buscador({ categoria }: { categoria: string }) {
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState<Producto[]>([]);
  const [cantidad, setCantidad] = useState(0);

  useEffect(() => {
    setCantidad(resultados.length);                       // estado derivado sincronizado con un efecto
  }, [resultados]);

  useEffect(() => {
    fetch(`/api/productos?q=${texto}&c=${categoria}`)     // una petición por tecla
      .then((r) => r.json())
      .then(setResultados);                               // una respuesta lenta pisa a una reciente
  }, [texto]);                                            // falta 'categoria': datos obsoletos

  return <input value={texto} onChange={(e) => setTexto(e.target.value)} />;
}
```

## Después

```tsx
function useValorRetrasado<T>(valor: T, milisegundos: number): T {
  const [retrasado, setRetrasado] = useState(valor);
  useEffect(() => {
    const temporizador = setTimeout(() => setRetrasado(valor), milisegundos);
    return () => clearTimeout(temporizador);              // limpieza
  }, [valor, milisegundos]);
  return retrasado;
}

function useProductos(texto: string, categoria: string) {
  const [resultados, setResultados] = useState<readonly Producto[]>([]);

  useEffect(() => {
    if (texto.length < 2) { setResultados([]); return; }
    const control = new AbortController();
    buscarProductos(texto, categoria, control.signal)
      .then(setResultados)
      .catch((error) => { if (error.name !== 'AbortError') setResultados([]); });
    return () => control.abort();                         // cancela la petición anterior
  }, [texto, categoria]);                                 // todas las dependencias

  return resultados;
}

function Buscador({ categoria }: { categoria: string }) {
  const [texto, setTexto] = useState('');
  const resultados = useProductos(useValorRetrasado(texto, 300), categoria);
  const cantidad = resultados.length;                     // derivado, sin estado ni efecto

  return (
    <>
      <label>Buscar <input value={texto} onChange={(e) => setTexto(e.target.value)} /></label>
      <p role="status">{cantidad} resultados</p>
      <ul>{resultados.map((p) => <li key={p.id}>{p.nombre}</li>)}</ul>
    </>
  );
}
```

En una aplicación real, `useProductos` se reemplaza por una biblioteca de datos que además resuelve caché, deduplicación y reintentos. Escribirlo a mano una vez sirve para entender qué problemas resuelve.

## Errores comunes

- Efectos para derivar estado.
- Dependencias omitidas o silenciadas con un comentario del *linter*.
- Efectos sin limpieza: fugas y actualizaciones tras desmontar.
- Cadenas de efectos que se disparan unos a otros.
- Obtener datos con `useEffect` sin cancelar ni manejar carreras.
- `useMemo` y `useCallback` por todas partes "por si acaso" (el compilador de React, donde está activo, memoriza por ti).
- `useRef` para guardar algo que debería provocar un renderizado.
- Hooks dentro de condiciones.
- Sorprenderse porque en desarrollo los efectos corren dos veces: el modo estricto lo hace a propósito para revelar limpiezas faltantes.

## Taller

1. Escribe `useAlmacenLocal(clave, inicial)` que sincronice un estado con `localStorage`, con limpieza y tipos.
2. Escribe `useEnLinea()` que informe si hay conexión, con oyentes y su limpieza.
3. Toma un componente tuyo y elimina todos los efectos que no sincronizan con algo externo. Anota qué reemplazó a cada uno.
4. Reproduce una carrera entre peticiones y corrígela con `AbortController`.
5. Reinicia el estado de un formulario al cambiar de registro usando `key`, sin efecto.
6. Explica qué problema revela el doble montaje del modo estricto.

Rama `taller/rx-n1-02-hooks`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Para qué sirve `useEffect` y para qué no?
2. ¿Por qué los hooks no pueden llamarse dentro de condiciones?
3. ¿Qué pasa si omites una dependencia de un efecto?
4. ¿Cuándo extraes un hook personalizado?
5. ¿Cuándo `useMemo` aporta algo y cuándo solo añade ruido?
