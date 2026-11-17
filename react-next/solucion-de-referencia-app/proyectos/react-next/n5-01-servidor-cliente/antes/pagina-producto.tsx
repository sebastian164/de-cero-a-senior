// ---- react-next/nivel-05-arquitectura/01-componentes-de-servidor-y-de-cliente/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
'use client';                                            // toda la página es de cliente

export default function PaginaProducto({ params }: { params: { id: string } }) {
  const [producto, setProducto] = useState<Producto | null>(null);
  const [resenas, setResenas] = useState<Resena[]>([]);

  useEffect(() => {
    fetch(`/api/productos/${params.id}`).then((r) => r.json()).then(setProducto);
    fetch(`/api/productos/${params.id}/resenas`).then((r) => r.json()).then(setResenas);
  }, [params.id]);

  if (!producto) return <p>Cargando…</p>;
  return (<main><h1>{producto.nombre}</h1><div dangerouslySetInnerHTML={{ __html: marked.parse(producto.descripcion, { async: false }) }} /><p>{producto.imagenes.length} imágenes</p><ul>{resenas.map((r) => <li key={r.id}>{r.texto}</li>)}</ul></main>);
}

import { useEffect, useState } from 'react';
import { marked } from 'marked';
import type { Producto, Resena } from '../tipos';
