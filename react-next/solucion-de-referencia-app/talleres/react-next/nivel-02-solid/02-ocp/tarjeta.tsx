// Punto 2: tarjeta con huecos para encabezado, cuerpo (children) y pie.
export function Tarjeta({ encabezado, pie, children, ...resto }: Omit<React.ComponentProps<'article'>, 'title'> & {
  encabezado?: React.ReactNode;
  pie?: React.ReactNode;
}) {
  return (
    <article className="tarjeta" {...resto}>
      {encabezado && <header>{encabezado}</header>}
      <div>{children}</div>
      {pie && <footer>{pie}</footer>}
    </article>
  );
}
