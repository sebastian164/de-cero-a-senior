'use client';
// Taller nivel-03/01, puntos 1 a 4.
import { createContext, useContext, useId, useRef, useState } from 'react';

/* ---------------- Punto 1: pestañas del README + navegación por teclado ---------------- */
interface EstadoPestanas { activa: string; activar: (id: string) => void; idBase: string }
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

function Lista({ children, etiqueta }: { children: React.ReactNode; etiqueta: string }) {
  const { activar } = usePestanas();
  const ref = useRef<HTMLDivElement>(null);
  function alPulsar(evento: React.KeyboardEvent) {
    const pestanas = [...(ref.current?.querySelectorAll<HTMLButtonElement>('[role="tab"]:not([disabled])') ?? [])];
    const actual = pestanas.findIndex((p) => p === document.activeElement);
    if (actual === -1) return;
    const destino = { ArrowRight: actual + 1, ArrowLeft: actual - 1, Home: 0, End: pestanas.length - 1 }[evento.key];
    if (destino === undefined) return;
    evento.preventDefault();
    const siguiente = pestanas[(destino + pestanas.length) % pestanas.length]!;
    siguiente.focus();
    activar(siguiente.dataset.id!); // activación automática al mover el foco
  }
  return <div role="tablist" aria-label={etiqueta} ref={ref} onKeyDown={alPulsar}>{children}</div>;
}

function Pestana({ id, children }: { id: string; children: React.ReactNode }) {
  const { activa, activar, idBase } = usePestanas();
  const seleccionada = activa === id;
  return (
    <button type="button" role="tab" data-id={id} id={`${idBase}-tab-${id}`} aria-selected={seleccionada}
      aria-controls={`${idBase}-panel-${id}`} tabIndex={seleccionada ? 0 : -1} onClick={() => activar(id)}>
      {children}
    </button>
  );
}

function Panel({ id, children }: { id: string; children: React.ReactNode }) {
  const { activa, idBase } = usePestanas();
  if (activa !== id) return null;
  return <div role="tabpanel" tabIndex={0} id={`${idBase}-panel-${id}`} aria-labelledby={`${idBase}-tab-${id}`}>{children}</div>;
}

Pestanas.Lista = Lista;
Pestanas.Pestana = Pestana;
Pestanas.Panel = Panel;

/* ---------------- Punto 2: acordeón como componentes compuestos ---------------- */
interface EstadoAcordeon { abiertos: ReadonlySet<string>; alternar: (id: string) => void; idBase: string }
const ContextoAcordeon = createContext<EstadoAcordeon | null>(null);
const ContextoItem = createContext<string | null>(null);
function useAcordeon() {
  const a = useContext(ContextoAcordeon);
  const item = useContext(ContextoItem);
  if (!a || item === null) throw new Error('Usa Acordeon.Encabezado y Acordeon.Panel dentro de <Acordeon.Item>');
  return { ...a, item, abierto: a.abiertos.has(item) };
}

export function Acordeon({ multiple = false, children }: { multiple?: boolean; children: React.ReactNode }) {
  const [abiertos, setAbiertos] = useState<ReadonlySet<string>>(new Set());
  const idBase = useId();
  function alternar(id: string) {
    setAbiertos((previos) => {
      const siguientes = new Set(multiple ? previos : [...previos].filter((x) => x === id));
      if (siguientes.has(id)) siguientes.delete(id); else siguientes.add(id);
      return siguientes;
    });
  }
  return <ContextoAcordeon value={{ abiertos, alternar, idBase }}><div>{children}</div></ContextoAcordeon>;
}
function Item({ id, children }: { id: string; children: React.ReactNode }) {
  return <ContextoItem value={id}>{children}</ContextoItem>;
}
function Encabezado({ children }: { children: React.ReactNode }) {
  const { abierto, alternar, item, idBase } = useAcordeon();
  return (
    <h3>
      <button type="button" aria-expanded={abierto} aria-controls={`${idBase}-${item}`} id={`${idBase}-${item}-boton`} onClick={() => alternar(item)}>
        {children}
      </button>
    </h3>
  );
}
function PanelAcordeon({ children }: { children: React.ReactNode }) {
  const { abierto, item, idBase } = useAcordeon();
  return <div role="region" id={`${idBase}-${item}`} aria-labelledby={`${idBase}-${item}-boton`} hidden={!abierto}>{children}</div>;
}
Acordeon.Item = Item;
Acordeon.Encabezado = Encabezado;
Acordeon.Panel = PanelAcordeon;

/* ---------------- Punto 4: el mismo acordeón con API de arreglo ---------------- */
export interface SeccionAcordeon { readonly id: string; readonly titulo: string; readonly contenido: React.ReactNode }
export function AcordeonDeArreglo({ secciones, multiple }: { secciones: readonly SeccionAcordeon[]; multiple?: boolean }) {
  return (
    <Acordeon multiple={multiple}>
      {secciones.map((s) => (
        <Acordeon.Item key={s.id} id={s.id}>
          <Acordeon.Encabezado>{s.titulo}</Acordeon.Encabezado>
          <Acordeon.Panel>{s.contenido}</Acordeon.Panel>
        </Acordeon.Item>
      ))}
    </Acordeon>
  );
}

/* ---------------- Punto 3: diálogo con huecos sobre <dialog> nativo ---------------- */
export function Dialogo({ disparador, titulo, acciones, children }: {
  disparador: string; titulo: React.ReactNode; acciones?: (cerrar: () => void) => React.ReactNode; children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const idTitulo = useId();
  const cerrar = () => ref.current?.close();
  return (
    <>
      <button type="button" onClick={() => ref.current?.showModal()}>{disparador}</button>
      {/* showModal: foco atrapado, fondo inerte, Escape cierra y el foco vuelve al botón al cerrar */}
      <dialog ref={ref} aria-labelledby={idTitulo}>
        <h2 id={idTitulo}>{titulo}</h2>
        <div>{children}</div>
        {/* eslint-disable-next-line react-hooks/refs -- falso positivo: `cerrar` solo toca la referencia cuando la persona pulsa, no durante el renderizado */}
        <footer>{acciones ? acciones(cerrar) : <button type="button" onClick={cerrar}>Cerrar</button>}</footer>
      </dialog>
    </>
  );
}
