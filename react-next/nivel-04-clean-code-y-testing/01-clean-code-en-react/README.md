# 01 — Clean code en React

Los principios generales están en el [Nivel 4 de Java](../../../java/nivel-04-clean-code-testing/). Aquí, lo propio de React.

## Idea

Un componente limpio se lee de arriba hacia abajo: qué recibe, qué recuerda, qué calcula, qué puede hacer el usuario y qué se muestra. Cuando hay efectos que encadenan efectos y banderas que se contradicen, ya no se puede leer: hay que ejecutarlo mentalmente.

## Reglas

| Regla | Mal | Bien |
|---|---|---|
| Estado mínimo | `items`, `total` y `hayItems` como estado | Solo `items`; lo demás se calcula |
| Sin efectos para derivar | `useEffect(() => setTotal(...), [items])` | `const total = sumar(items)` |
| Lógica de evento en el evento | Efecto que observa una bandera `enviado` | Código en `onSubmit` |
| Estados imposibles imposibles | `cargando`, `error` y `datos` como tres `useState` | Una unión discriminada o un reductor |
| Condiciones con nombre | `{u && u.rol === 'admin' && !p.cerrado && <Boton />}` | `{puedeEditar && <Boton />}` |
| Retornos tempranos | Ternarios anidados en el JSX | `if (cargando) return <Esqueleto />` |
| Claves estables | `key={index}` | `key={pedido.id}` |
| Manejadores con nombre | `onClick={() => { /* 15 líneas */ }}` | `onClick={confirmarPedido}` |
| Propiedades tipadas y pocas | `props: any`, doce propiedades | Tipos precisos; agrupar o componer |
| Componentes fuera de componentes | Definir `Fila` dentro de `Tabla` | Al nivel del módulo |
| Cuidado con `&&` | `{lista.length && <Lista />}` pinta un `0` | `{lista.length > 0 && <Lista />}` |

### Estado que no puede contradecirse

```tsx
// Antes: ocho combinaciones, la mayoría sin sentido
const [cargando, setCargando] = useState(false);
const [error, setError] = useState<string | null>(null);
const [datos, setDatos] = useState<Pedido[] | null>(null);

// Después: solo los estados que existen
type Estado =
  | { tipo: 'inactivo' }
  | { tipo: 'cargando' }
  | { tipo: 'error'; mensaje: string }
  | { tipo: 'listo'; pedidos: readonly Pedido[] };
```

### Anatomía de un componente

```tsx
export function ResumenPedido({ pedido, onCancelar }: ResumenPedidoProps) {
  // 1. Hooks: estado y contexto
  const [confirmando, setConfirmando] = useState(false);
  const { puedeCancelar } = usePermisos();

  // 2. Valores derivados
  const total = calcularTotal(pedido);
  const cancelable = puedeCancelar && sePuedeCancelar(pedido);

  // 3. Manejadores
  function confirmarCancelacion() {
    setConfirmando(false);
    onCancelar(pedido.id);
  }

  // 4. Retornos tempranos
  if (pedido.lineas.length === 0) return <p>El pedido no tiene productos.</p>;

  // 5. Interfaz
  return (
    <article>
      <h2>Pedido {pedido.numero}</h2>
      <p>{formatoMoneda.format(total)}</p>
      {cancelable && <button type="button" onClick={() => setConfirmando(true)}>Cancelar</button>}
      {confirmando && <Confirmacion onAceptar={confirmarCancelacion} onCerrar={() => setConfirmando(false)} />}
    </article>
  );
}
```

`calcularTotal` y `sePuedeCancelar` viven fuera, en el dominio, y se prueban sin React.

### Memorización

`useMemo`, `useCallback` y `memo` añaden código y dependencias que mantener. Úsalos cuando una medición muestre un problema, o cuando una referencia estable sea necesaria para un efecto. Con el compilador de React activado, la memorización manual sobra en la mayoría de los casos.

### Organización

Archivos por funcionalidad; un componente principal por archivo; nombres que dicen qué es (`TarjetaPedido`, no `Card2`); convención de nombres de archivo única en el equipo.

### Herramientas

TypeScript estricto, ESLint con las reglas de hooks (no las silencies), reglas de accesibilidad para JSX y un formateador automático.

## Errores comunes

- Efectos en cadena.
- Estado duplicado o derivado.
- Copiar propiedades al estado.
- JSX con cálculos y ternarios anidados.
- Silenciar la regla de dependencias exhaustivas.
- Componentes de 400 líneas.
- `useMemo` en todo, sin medir.
- Objetos de estilo y funciones en línea pasados a componentes memorizados, que anulan la memorización.
- `index.ts` que reexportan todo y dificultan seguir el código.

## Taller

1. Toma un componente tuyo y elimina todo estado derivado.
2. Elimina los efectos que no sincronizan con algo externo.
3. Reemplaza las banderas de carga por una unión discriminada.
4. Extrae condiciones y manejadores con nombre.
5. Activa las reglas de hooks y de accesibilidad del *linter* y corrige lo que aparezca sin silenciar nada.
6. Quita toda la memorización manual, mide, y repón solo la que haga falta.

Rama `taller/rx-n4-01-clean-code`.

## Preguntas senior

1. ¿Qué señales indican que un `useEffect` sobra?
2. ¿Cómo eliminas los estados imposibles?
3. ¿Cuándo vale la pena memorizar?
4. ¿Qué orden interno le das a un componente y por qué?
