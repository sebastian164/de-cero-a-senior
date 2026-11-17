'use client';
// ---- react-next/nivel-01-fundamentos/02-hooks-y-efectos/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { useEffect, useState } from 'react';

// Contexto: el producto y la función de acceso que el README da por supuestos (consulta /api/n1-02/productos).
export interface Producto { readonly id: string; readonly nombre: string }
export async function buscarProductos(texto: string, categoria: string, signal: AbortSignal): Promise<Producto[]> {
  const respuesta = await fetch(`/api/n1-02/productos?${new URLSearchParams({ q: texto, c: categoria })}`, { signal });
  if (!respuesta.ok) throw new Error(`Error ${respuesta.status}`);
  return respuesta.json();
}

function useValorRetrasado<T>(valor: T, milisegundos: number): T {
  const [retrasado, setRetrasado] = useState(valor);
  useEffect(() => {
    const temporizador = setTimeout(() => setRetrasado(valor), milisegundos);
    return () => clearTimeout(temporizador);              // limpieza
  }, [valor, milisegundos]);
  return retrasado;
}

function useProductos(texto: string, categoria: string) {
  const [resultados, setResultados] = useState<readonly Producto[]>([]);

  useEffect(() => {
    if (texto.length < 2) { setResultados([]); return; }
    const control = new AbortController();
    buscarProductos(texto, categoria, control.signal)
      .then(setResultados)
      .catch((error) => { if (error.name !== 'AbortError') setResultados([]); });
    return () => control.abort();                         // cancela la petición anterior
  }, [texto, categoria]);                                 // todas las dependencias

  return resultados;
}

function Buscador({ categoria }: { categoria: string }) {
  const [texto, setTexto] = useState('');
  const resultados = useProductos(useValorRetrasado(texto, 300), categoria);
  const cantidad = resultados.length;                     // derivado, sin estado ni efecto

  return (
    <>
      <label>Buscar <input value={texto} onChange={(e) => setTexto(e.target.value)} /></label>
      <p role="status">{cantidad} resultados</p>
      <ul>{resultados.map((p) => <li key={p.id}>{p.nombre}</li>)}</ul>
    </>
  );
}

export { Buscador, useValorRetrasado, useProductos };
