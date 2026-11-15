# 01 — SRP en React

Explicación completa del principio: [SRP en la ruta Java](../../../java/nivel-2-solid/01-srp/).

## Idea

Un componente debería tener un solo motivo para cambiar. Si el mismo archivo cambia cuando cambia el diseño, cuando cambia la API y cuando cambia una regla de negocio, tiene tres dueños.

## Dónde va cada responsabilidad

| Responsabilidad | Hogar |
|---|---|
| Cómo se ve | Componente de presentación |
| Lógica con estado reutilizable | Hook personalizado |
| Reglas y cálculos | Funciones puras, fuera de React |
| Hablar con el servidor | Capa de datos (funciones, acciones de servidor, biblioteca de datos) |
| Traducir el formato de la API | Adaptadores |
| Coordinar una pantalla | Componente de página |

Señales: más de 200 líneas, muchos `useState` y `useEffect`, JSX con cálculos, pruebas que necesitan simular la red para comprobar un texto.

## Antes

```tsx
'use client';
function Factura({ id }: { id: string }) {
  const [factura, setFactura] = useState<any>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    fetch(`/api/facturas/${id}`).then((r) => r.json()).then((dto) => {
      setFactura({ ...dto, fecha: new Date(dto.fecha_emision) });
      setCargando(false);
    });
  }, [id]);

  if (cargando) return <p>Cargando…</p>;
  const subtotal = factura.lineas.reduce((s: number, l: any) => s + l.valor, 0);
  const total = subtotal * 1.19;                                      // regla tributaria en el JSX

  function exportarCsv() { /* arma el CSV y lo descarga */ }

  return (
    <div>
      <h1>Factura {factura.numero}</h1>
      <p>{factura.fecha.getDate()}/{factura.fecha.getMonth() + 1}/{factura.fecha.getFullYear()}</p>
      <p>Total: {total}</p>
      <button onClick={exportarCsv}>Exportar</button>
    </div>
  );
}
```

## Después

```ts
// facturas/dominio.ts — reglas puras
export const subtotal = (f: Factura) => f.lineas.reduce((s, l) => s + l.valor, 0);
export const total = (f: Factura) => subtotal(f) * (1 + TARIFA_IVA);

// facturas/datos.ts — servidor y traducción
export async function obtenerFactura(id: string): Promise<Factura> {
  const respuesta = await fetch(`${API}/facturas/${id}`);
  if (!respuesta.ok) throw new FacturaNoEncontrada(id);
  return aFactura(await respuesta.json());
}

// facturas/exportar.ts — una sola tarea
export function aCsv(factura: Factura): string { /* ... */ }
```

```tsx
// app/facturas/[id]/page.tsx — coordina (componente de servidor)
export default async function PaginaFactura({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const factura = await obtenerFactura(id);
  return <DetalleFactura factura={factura} />;
}

// facturas/ui/detalle-factura.tsx — presenta
export function DetalleFactura({ factura }: { factura: Factura }) {
  return (
    <article>
      <h1>Factura {factura.numero}</h1>
      <time dateTime={factura.fecha.toISOString()}>{formatoFecha.format(factura.fecha)}</time>
      <p>Total: {formatoMoneda.format(total(factura))}</p>
      <BotonExportar factura={factura} />
    </article>
  );
}
```

Cambiar la tarifa del IVA toca un archivo que no importa React.

## Cuándo no dividir

Un componente pequeño que muestra una cosa no necesita cinco archivos. Extrae cuando aparezca el segundo motivo de cambio o la segunda vez que necesites esa lógica.

## Errores comunes

- Componentes dios de página completa.
- Hooks personalizados gigantes que hacen de todo: el problema solo se mudó.
- Reglas de negocio dentro del JSX o de efectos.
- `utils.ts` con funciones sin relación.
- Fragmentar en componentes de cinco líneas sin motivo.

## Taller

1. Lista los actores de un componente tuyo de más de 200 líneas.
2. Sepáralo en dominio, datos, hooks y presentación.
3. Prueba una regla sin importar React.
4. Indica qué dejaste junto y por qué.

Rama `taller/rx-n2-01-srp`.

## Preguntas senior

1. ¿Qué responsabilidades son legítimas en un componente?
2. ¿Cuándo extraes un hook y cuándo una función pura?
3. ¿Cómo evitas que un hook personalizado se vuelva un cajón de sastre?
