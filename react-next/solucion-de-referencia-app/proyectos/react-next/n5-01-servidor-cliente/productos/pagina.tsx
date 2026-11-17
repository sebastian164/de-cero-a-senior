// ---- react-next/nivel-05-arquitectura/01-componentes-de-servidor-y-de-cliente/README.md (bloque 2, desde '// app/productos/[id]/page.tsx'), copiado tal cual entre el contexto agregado ----
import { Suspense } from 'react';
import { notFound } from 'next/navigation';
import { Galeria } from '../galeria';
import { Descripcion, EsqueletoResenas, Resenas } from '../contexto';
import { BotonAgregarAlCarrito } from './ui/boton-agregar';
import { obtenerProducto } from './datos';

// app/productos/[id]/page.tsx — servidor
export default async function PaginaProducto({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const producto = await obtenerProducto(id);            // directo, sin endpoint intermedio
  if (!producto) notFound();

  return (
    <main>
      <h1>{producto.nombre}</h1>
      <Galeria imagenes={producto.imagenes} />            {/* cliente: carrusel */}
      <Descripcion markdown={producto.descripcion} />      {/* servidor: la biblioteca no viaja */}
      <BotonAgregarAlCarrito productoId={producto.id} precio={producto.precio} />   {/* cliente */}
      <Suspense fallback={<EsqueletoResenas />}>
        <Resenas productoId={producto.id} />              {/* servidor, se transmite cuando esté lista */}
      </Suspense>
    </main>
  );
}
