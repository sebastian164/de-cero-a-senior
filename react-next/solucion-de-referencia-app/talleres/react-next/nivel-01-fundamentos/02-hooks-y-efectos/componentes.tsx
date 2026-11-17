'use client';
// Puntos 3 a 6 del taller.
import { useEffect, useState } from 'react';
import { useAlmacenLocal, useEnLinea } from './hooks';

/* ---------- Punto 3: un componente con efectos innecesarios y su versión sin ellos ---------- */
// La versión con efectos innecesarios (mal a propósito) está en antes/resumen-con-efectos.tsx.
export interface Producto { readonly id: string; readonly nombre: string; readonly precio: number }

/** Después: valores derivados al renderizar; el aviso al padre va en el manejador que cambia el estado (ver Carrito). */
export function ResumenCarrito({ productos }: { productos: readonly Producto[] }) {
  const total = productos.reduce((s, p) => s + p.precio, 0);
  useEffect(() => { document.title = `Carrito (${productos.length})`; }, [productos.length]);
  return <p>{productos.length} productos · {total}</p>;
}

export function Carrito({ onCambio }: { onCambio?: (n: number) => void }) {
  const [productos, setProductos] = useState<readonly Producto[]>([]);
  function agregar() {
    const siguientes = [...productos, { id: String(productos.length + 1), nombre: 'Café', precio: 18000 }];
    setProductos(siguientes);
    onCambio?.(siguientes.length); // en el mismo manejador, sin efecto
  }
  return (<div><ResumenCarrito productos={productos} /><button type="button" onClick={agregar}>Agregar café</button></div>);
}

/* ---------- Punto 4: carrera entre peticiones ---------- */
async function buscar(texto: string, signal?: AbortSignal): Promise<string[]> {
  const r = await fetch(`/api/n1-02/productos?q=${encodeURIComponent(texto)}&c=todas`, { signal });
  const datos: { nombre: string }[] = await r.json();
  return datos.map((d) => d.nombre);
}

/** Con carrera: la respuesta lenta de un término viejo pisa la del más reciente. */
export function BuscadorConCarrera() {
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState<string[]>([]);
  useEffect(() => {
    if (texto.length < 1) return;
    buscar(texto).then(setResultados);
  }, [texto]);
  return (<div><label>Buscar con carrera <input value={texto} onChange={(e) => setTexto(e.target.value)} /></label><output aria-label="resultados con carrera">{resultados.join(', ')}</output></div>);
}

/** Corregido: cada cambio cancela la petición anterior. */
export function BuscadorSinCarrera() {
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState<string[]>([]);
  useEffect(() => {
    if (texto.length < 1) return;
    const control = new AbortController();
    buscar(texto, control.signal)
      .then(setResultados)
      .catch((e: unknown) => { if (!(e instanceof DOMException && e.name === 'AbortError')) setResultados([]); });
    return () => control.abort();
  }, [texto]);
  return (<div><label>Buscar sin carrera <input value={texto} onChange={(e) => setTexto(e.target.value)} /></label><output aria-label="resultados sin carrera">{resultados.join(', ')}</output></div>);
}

/* ---------- Punto 5: reiniciar un formulario al cambiar de registro, con key ---------- */
interface Cliente { readonly id: string; readonly nombre: string }
const CLIENTES: readonly Cliente[] = [{ id: '1', nombre: 'Ana' }, { id: '2', nombre: 'Luis' }];

function FormularioCliente({ cliente }: { cliente: Cliente }) {
  const [nombre, setNombre] = useState(cliente.nombre); // inicial; se reinicia porque el padre cambia la key
  return <label>Nombre <input value={nombre} onChange={(e) => setNombre(e.target.value)} /></label>;
}

export function EditorClientes() {
  const [seleccionado, setSeleccionado] = useState<Cliente>(CLIENTES[0]!);
  return (
    <div>
      {CLIENTES.map((c) => <button key={c.id} type="button" aria-pressed={c.id === seleccionado.id} onClick={() => setSeleccionado(c)}>{c.nombre}</button>)}
      <FormularioCliente key={seleccionado.id} cliente={seleccionado} />
    </div>
  );
}

/* ---------- Punto 6: doble montaje del modo estricto ---------- */
export const conexiones = { abiertas: 0 };

export function SuscripcionSinLimpieza() {
  useEffect(() => { conexiones.abiertas++; }, []); // falta la limpieza: el modo estricto la delata
  return null;
}

export function SuscripcionConLimpieza() {
  useEffect(() => {
    conexiones.abiertas++;
    return () => { conexiones.abiertas--; };
  }, []);
  return null;
}

/* ---------- Demostración de los hooks propios ---------- */
export function DemoHooks() {
  const [nota, setNota] = useAlmacenLocal('n1-02-nota', '');
  const enLinea = useEnLinea();
  return (
    <div>
      <p role="status">{enLinea ? 'En línea' : 'Sin conexión'}</p>
      <label>Nota guardada en localStorage <input value={nota} onChange={(e) => setNota(e.target.value)} /></label>
    </div>
  );
}
