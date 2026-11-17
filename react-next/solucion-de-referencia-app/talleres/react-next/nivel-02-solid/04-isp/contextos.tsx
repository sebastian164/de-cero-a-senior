'use client';
// Punto 2: un contexto grande frente a contextos pequeños, con un contador de renderizados.
import { createContext, useContext, useState } from 'react';

type Tema = 'claro' | 'oscuro';
interface Todo { tema: Tema; setTema: (t: Tema) => void; carrito: number; agregar: () => void }

export const renderizados = { interruptorUnico: 0, interruptorSeparado: 0 };

// --- Un contexto con todo ---
const ContextoApp = createContext<Todo | null>(null);
export function ProveedorUnico({ children }: { children: React.ReactNode }) {
  const [tema, setTema] = useState<Tema>('claro');
  const [carrito, setCarrito] = useState(0);
  return <ContextoApp value={{ tema, setTema, carrito, agregar: () => setCarrito((c) => c + 1) }}>{children}</ContextoApp>;
}
export function InterruptorUnico() {
  const { tema, setTema } = useContext(ContextoApp)!;
  // eslint-disable-next-line react-hooks/immutability -- contador de renderizados: es justo lo que el taller mide (la alternativa sin mutar es <Profiler onRender>, ver nivel-03/03 punto 4)
  renderizados.interruptorUnico++;
  return <button type="button" onClick={() => setTema(tema === 'claro' ? 'oscuro' : 'claro')}>Tema único: {tema}</button>;
}
export function BotonCarritoUnico() {
  const { carrito, agregar } = useContext(ContextoApp)!;
  return <button type="button" onClick={agregar}>Carrito único ({carrito})</button>;
}

// --- Contextos separados por tema, y estado separado de acciones ---
const ContextoTema = createContext<Tema>('claro');
const ContextoCambiarTema = createContext<(t: Tema) => void>(() => {});
const ContextoCarrito = createContext(0);
const ContextoAgregar = createContext<() => void>(() => {});

export function ProveedoresSeparados({ children }: { children: React.ReactNode }) {
  const [tema, setTema] = useState<Tema>('claro');
  const [carrito, setCarrito] = useState(0);
  const [agregar] = useState(() => () => setCarrito((c) => c + 1)); // referencia estable
  return (
    <ContextoCambiarTema value={setTema}>
      <ContextoTema value={tema}>
        <ContextoAgregar value={agregar}>
          <ContextoCarrito value={carrito}>{children}</ContextoCarrito>
        </ContextoAgregar>
      </ContextoTema>
    </ContextoCambiarTema>
  );
}
export function InterruptorSeparado() {
  const tema = useContext(ContextoTema);
  const setTema = useContext(ContextoCambiarTema);
  // eslint-disable-next-line react-hooks/immutability -- contador de renderizados: es justo lo que el taller mide (la alternativa sin mutar es <Profiler onRender>, ver nivel-03/03 punto 4)
  renderizados.interruptorSeparado++;
  return <button type="button" onClick={() => setTema(tema === 'claro' ? 'oscuro' : 'claro')}>Tema separado: {tema}</button>;
}
export function BotonCarritoSeparado() {
  const carrito = useContext(ContextoCarrito);
  const agregar = useContext(ContextoAgregar);
  return <button type="button" onClick={agregar}>Carrito separado ({carrito})</button>;
}
