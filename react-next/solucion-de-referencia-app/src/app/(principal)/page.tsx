import Link from 'next/link';
import { PANTALLAS } from '@app/rutas';

export default function Indice() {
  const porSesion = Map.groupBy(PANTALLAS, (p) => p.sesion);
  return (
    <main>
      <h1>Ruta React y Next.js: ejemplos y talleres</h1>
      <p className="suave">Cada enlace abre una pantalla real construida con el código del curso o con la solución de su taller.</p>
      {[...porSesion].map(([sesion, pantallas]) => (
        <section key={sesion} className="bloque">
          <h2>{sesion}</h2>
          <ul>
            {pantallas.map((p) => (
              <li key={p.ruta}><Link href={p.ruta}>{p.titulo}</Link> <code className="suave">{p.ruta}</code></li>
            ))}
          </ul>
        </section>
      ))}
    </main>
  );
}
