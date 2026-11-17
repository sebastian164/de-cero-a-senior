'use client';
// Punto 5: la misma lista marcada entera como cliente, para comparar el JavaScript enviado.
// El resumen se formatea con marked: en el servidor la biblioteca se queda allí; aquí viaja al navegador.
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { marked } from 'marked';

interface Libro { readonly id: string; readonly titulo: string; readonly autor: string; readonly anio: number; readonly resumen?: string }

export function ListaLibrosCliente() {
  const [libros, setLibros] = useState<readonly Libro[] | null>(null);
  useEffect(() => {
    const control = new AbortController();
    fetch('/api/n1-03/libros', { signal: control.signal }).then((r) => r.json()).then(setLibros).catch(() => {});
    return () => control.abort();
  }, []);
  if (!libros) return <p>Cargando…</p>;
  return (
    <ul>{libros.map((l) => (
      <li key={l.id}>
        <Link href={`/n1/03-next/libros/${l.id}`}>{l.titulo}</Link> — {l.autor} ({l.anio})
        {l.resumen && <div dangerouslySetInnerHTML={{ __html: marked.parse(l.resumen, { async: false }) }} />}
      </li>
    ))}</ul>
  );
}
