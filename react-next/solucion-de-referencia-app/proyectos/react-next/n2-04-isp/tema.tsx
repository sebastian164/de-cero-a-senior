'use client';
// ---- react-next/nivel-02-solid/04-isp/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { createContext, useContext, useState } from 'react';

export type Tema = 'claro' | 'oscuro';

function Avatar({ src, alt, tamano = 40 }: { src: string; alt: string; tamano?: number }) {
  return <img src={src} alt={alt} width={tamano} height={tamano} />;
}

// Un contexto por tema, y estado separado de acciones
const ContextoTema = createContext<Tema>('claro');
const ContextoCambiarTema = createContext<(tema: Tema) => void>(() => {});

export function ProveedorDeTema({ children }: { children: React.ReactNode }) {
  const [tema, setTema] = useState<Tema>('claro');
  return (
    <ContextoCambiarTema value={setTema}>
      <ContextoTema value={tema}>{children}</ContextoTema>
    </ContextoCambiarTema>
  );
}

export const useTema = () => useContext(ContextoTema);
export const useCambiarTema = () => useContext(ContextoCambiarTema);

export { Avatar };
