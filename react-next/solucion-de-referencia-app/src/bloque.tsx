// Marco visual para cada ejemplo o punto de taller dentro de una pantalla.
export function Bloque({ titulo, origen, children }: { titulo: string; origen: string; children: React.ReactNode }) {
  return (
    <section className="bloque">
      <h2>{titulo}</h2>
      <p className="suave"><small>{origen}</small></p>
      {children}
    </section>
  );
}
