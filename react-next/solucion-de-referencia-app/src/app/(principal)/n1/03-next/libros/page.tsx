import Link from 'next/link';
import { marked } from 'marked';
import { listarLibros } from '@talleres/react-next/nivel-01-fundamentos/03-fundamentos-de-next/datos';
import { FormularioLibro } from '@talleres/react-next/nivel-01-fundamentos/03-fundamentos-de-next/formulario-libro';

export const metadata = { title: 'nivel-01/03 · Libros' };

// Componente de servidor (marked se ejecuta aquí y no viaja al navegador): obtiene los datos con await, sin useEffect. Solo el formulario es de cliente.
export default async function PaginaLibros() {
  const libros = await listarLibros();
  return (
    <main>
      <h1>Libros</h1>
      <ul>
        {libros.map((l) => <li key={l.id}><Link href={`/n1/03-next/libros/${l.id}`}>{l.titulo}</Link> — {l.autor} ({l.anio})
          {l.resumen && <div dangerouslySetInnerHTML={{ __html: marked.parse(l.resumen, { async: false }) }} />}
        </li>)}
      </ul>
      <h2>Nuevo libro</h2>
      <FormularioLibro />
    </main>
  );
}
