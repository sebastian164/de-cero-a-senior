'use client';
// Punto 5: el error del índice como clave, a propósito, en una lista con campos de texto.
import { useState } from 'react';

interface Persona { readonly id: string; readonly nombre: string }

const INICIALES: readonly Persona[] = [
  { id: 'a', nombre: 'Ana' },
  { id: 'b', nombre: 'Luis' },
  { id: 'c', nombre: 'Eva' },
];

export function ListaConNotas({ claveIndice }: { claveIndice: boolean }) {
  const [personas, setPersonas] = useState<readonly Persona[]>(INICIALES);
  const titulo = claveIndice ? 'Clave = índice (mal)' : 'Clave = id (bien)';
  return (
    <div>
      <h3>{titulo}</h3>
      <ul>
        {personas.map((p, i) => (
          <li key={claveIndice ? i : p.id}>
            <label>
              Nota para {p.nombre} <input aria-label={`Nota para ${p.nombre}`} defaultValue="" />
            </label>{' '}
            <button type="button" onClick={() => setPersonas((ps) => ps.filter((x) => x.id !== p.id))}>Quitar a {p.nombre}</button>
          </li>
        ))}
      </ul>
    </div>
  );
}
