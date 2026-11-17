// ---- react-next/nivel-03-patrones/01-composicion-y-componentes-compuestos/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
'use client';

interface EstadoPestanas {
  activa: string;
  activar: (id: string) => void;
  idBase: string;
}

const ContextoPestanas = createContext<EstadoPestanas | null>(null);

function usePestanas() {
  const contexto = useContext(ContextoPestanas);
  if (!contexto) throw new Error('Los componentes de Pestanas deben usarse dentro de <Pestanas>');
  return contexto;
}

export function Pestanas({ inicial, children }: { inicial: string; children: React.ReactNode }) {
  const [activa, activar] = useState(inicial);
  const idBase = useId();
  return <ContextoPestanas value={{ activa, activar, idBase }}>{children}</ContextoPestanas>;
}

function Lista({ children }: { children: React.ReactNode }) {
  return <div role="tablist">{children}</div>;
}

function Pestana({ id, children }: { id: string; children: React.ReactNode }) {
  const { activa, activar, idBase } = usePestanas();
  const seleccionada = activa === id;
  return (
    <button
      type="button"
      role="tab"
      id={`${idBase}-tab-${id}`}
      aria-selected={seleccionada}
      aria-controls={`${idBase}-panel-${id}`}
      tabIndex={seleccionada ? 0 : -1}
      onClick={() => activar(id)}
    >
      {children}
    </button>
  );
}

function Panel({ id, children }: { id: string; children: React.ReactNode }) {
  const { activa, idBase } = usePestanas();
  if (activa !== id) return null;
  return <div role="tabpanel" id={`${idBase}-panel-${id}`} aria-labelledby={`${idBase}-tab-${id}`}>{children}</div>;
}

Pestanas.Lista = Lista;
Pestanas.Pestana = Pestana;
Pestanas.Panel = Panel;

import { createContext, useContext, useId, useState } from 'react';
