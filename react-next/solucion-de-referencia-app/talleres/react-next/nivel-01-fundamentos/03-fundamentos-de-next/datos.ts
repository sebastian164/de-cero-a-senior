// Punto 2: los datos vienen de un archivo (libros.json) y se leen en componentes de servidor.
import 'server-only';
import iniciales from './libros.json';

export interface Libro { readonly id: string; readonly titulo: string; readonly autor: string; readonly anio: number; readonly resumen?: string }

const libros: Libro[] = [...iniciales];

export async function listarLibros(): Promise<readonly Libro[]> {
  return [...libros];
}

export async function obtenerLibro(id: string): Promise<Libro | null> {
  await new Promise((r) => setTimeout(r, 400)); // lento a propósito: deja ver loading.tsx
  return libros.find((l) => l.id === id) ?? null;
}

export async function guardarLibro(datos: Omit<Libro, 'id'>): Promise<Libro> {
  const libro = { ...datos, id: String(libros.length + 1) };
  libros.push(libro);
  return libro;
}
