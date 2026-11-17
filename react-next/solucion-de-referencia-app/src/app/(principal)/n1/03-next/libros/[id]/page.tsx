import { notFound } from 'next/navigation';
import { obtenerLibro } from '@talleres/react-next/nivel-01-fundamentos/03-fundamentos-de-next/datos';

export default async function DetalleLibro({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ fallar?: string }> }) {
  const [{ id }, { fallar }] = await Promise.all([params, searchParams]);
  if (fallar === '1') throw new Error('Fallo provocado para ver error.tsx');
  const libro = await obtenerLibro(id);
  if (!libro) notFound();
  return (
    <main>
      <h1>{libro.titulo}</h1>
      <p>{libro.autor}, {libro.anio}</p>
    </main>
  );
}
