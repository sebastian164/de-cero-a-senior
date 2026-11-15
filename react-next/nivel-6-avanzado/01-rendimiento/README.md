# 01 — Rendimiento en React y Next.js

El método (medir, encontrar el cuello de botella, corregir una cosa, volver a medir) y las métricas web (LCP, INP, CLS) están explicados en la [sesión de rendimiento de la ruta Angular](../../../angular/nivel-6-angular-avanzado/01-deteccion-de-cambios-y-rendimiento/). Aquí, lo propio de React.

## Idea

React vuelve a ejecutar tus componentes cada vez que cambia el estado. Casi siempre eso es barato y no hay que hacer nada. Los problemas reales suelen estar en otro lado: demasiado JavaScript enviado, cascadas de peticiones, listas enormes, o estado puesto demasiado arriba en el árbol.

## Lo esencial

### Cómo decide React qué volver a renderizar

Un componente se renderiza cuando cambia su estado, cuando su padre se renderiza, o cuando cambia un contexto que consume. Renderizar no es lo mismo que tocar el DOM: React compara y solo aplica las diferencias.

### Orden de ataque

Primero lo que más pesa, que rara vez es la memorización:

| # | Problema | Solución |
|---|---|---|
| 1 | Demasiado JavaScript | Componentes de servidor; `'use client'` en las hojas; carga diferida con `dynamic()` o `lazy`; revisar dependencias pesadas |
| 2 | Cascadas de datos | Consultar en el servidor, en paralelo; transmisión con `Suspense` |
| 3 | Recursos pesados | `next/image`, `next/font`, estrategia para scripts de terceros |
| 4 | Estado demasiado arriba | Bajar el estado al componente que lo usa; pasar contenido como `children` |
| 5 | Contextos grandes | Dividir; almacén con selectores |
| 6 | Listas largas | Virtualización; paginación |
| 7 | Cálculos caros en el renderizado | `useMemo`, o moverlos al servidor |
| 8 | Renderizados costosos repetidos | `memo` con propiedades estables |
| 9 | Interacciones que bloquean | `useTransition`, `useDeferredValue` |

### Bajar el estado: la optimización gratuita

```tsx
// Antes: cada tecla vuelve a renderizar la tabla pesada
function Pagina() {
  const [texto, setTexto] = useState('');
  return (<><input value={texto} onChange={(e) => setTexto(e.target.value)} /><TablaPesada /></>);
}

// Después: el estado vive donde se usa; la tabla no se entera
function Pagina() {
  return (<><Buscador /><TablaPesada /></>);
}
```

Y cuando un componente con estado envuelve contenido costoso, recibirlo como `children` evita volver a renderizarlo: el elemento ya fue creado por el padre y su referencia no cambia.

### Memorización

`memo` evita renderizar un componente si sus propiedades no cambiaron; `useMemo` guarda un cálculo; `useCallback`, una función. Cuestan código y comparaciones, y se anulan si se pasa un objeto o una función nuevos en cada renderizado.

El **compilador de React** aplica esta memorización automáticamente al construir, respetando las reglas de React. Con él activo, la memorización manual sobra en la mayoría de los casos. Revisa si tu proyecto lo usa antes de escribir `useMemo` por costumbre.

### Mantener la interfaz respondiendo

```tsx
const [texto, setTexto] = useState('');
const textoDiferido = useDeferredValue(texto);          // el campo responde ya; la lista, cuando pueda
const resultados = useMemo(() => filtrar(productos, textoDiferido), [productos, textoDiferido]);

const [pendiente, iniciar] = useTransition();
iniciar(() => setPestana('reportes'));                  // actualización no urgente: no bloquea la escritura
```

### Carga diferida

```tsx
const Grafica = dynamic(() => import('./grafica-de-ventas'), {
  loading: () => <EsqueletoGrafica />,
  ssr: false,                                           // solo si depende del navegador
});
```

### Medir

- **Analizador del paquete:** qué ocupa el JavaScript enviado.
- **React DevTools, perfilador:** qué se renderiza, por qué y cuánto tarda.
- **Pestaña de rendimiento del navegador,** con CPU y red limitadas.
- **Lighthouse** (laboratorio) y **métricas de usuarios reales** (campo): Next.js permite recogerlas con `useReportWebVitals`.
- Siempre sobre la construcción de producción.

## Errores comunes

- Memorizar todo sin medir.
- `memo` con propiedades que son objetos o funciones nuevos en cada renderizado.
- `'use client'` en lo alto del árbol.
- Bibliotecas enteras importadas para una función.
- Estado global para lo que es local.
- Listas de miles de elementos sin virtualizar.
- Imágenes sin dimensiones ni optimización.
- Medir en modo de desarrollo o solo en un equipo potente.
- Efectos que provocan renderizados en cadena.
- Scripts de terceros cargados de forma bloqueante.

## Taller

1. Analiza el paquete de una aplicación tuya y reduce el JavaScript inicial; documenta cada cambio con su medición.
2. Perfila una pantalla lenta e identifica qué se renderiza de más y por qué.
3. Resuelve un caso bajando el estado y otro pasando `children`, sin memorización.
4. Pinta 10.000 filas, mide y aplica virtualización.
5. Usa `useDeferredValue` en un filtro costoso y compara la respuesta al escribir.
6. Activa el compilador de React en un proyecto de prueba y quita la memorización manual; mide.
7. Recoge las métricas web en campo y compáralas con Lighthouse.

Rama `taller/rx-n6-01-rendimiento`.

## Preguntas senior

1. ¿Cuándo se vuelve a renderizar un componente?
2. ¿Qué harías antes de recurrir a `memo`?
3. ¿Qué cambia el compilador de React?
4. ¿Cómo reduces el JavaScript que llega al navegador en Next.js?
5. ¿Para qué sirven `useTransition` y `useDeferredValue`?
