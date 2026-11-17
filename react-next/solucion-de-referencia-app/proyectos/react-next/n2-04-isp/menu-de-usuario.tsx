'use client';
import { useState } from 'react';

export function MenuDeUsuario({ nombre, fotoUrl }: { nombre: string; fotoUrl: string }) {
  const [abierto, setAbierto] = useState(false);
  return (
    <div>
      <button type="button" aria-expanded={abierto} onClick={() => setAbierto(!abierto)}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={fotoUrl} alt="" width={24} height={24} /> {nombre}
      </button>
      {abierto && <ul><li>Mi perfil</li><li>Salir</li></ul>}
    </div>
  );
}
