'use client';
// Puntos 3, 4 y 5 del taller.
import { useCallback, useEffect, useState } from 'react';
import { ErrorBoundary, type FallbackProps } from 'react-error-boundary';
import { reportar } from '@/react-next/n3-05-suspense/reporte';

/* ---------- Punto 3: límite de error por sección, con reintento y reporte ---------- */
function Alternativa({ resetErrorBoundary }: FallbackProps, seccion: string) {
  return (
    <div role="alert">
      <p>No pudimos cargar {seccion}.</p>
      <button type="button" onClick={resetErrorBoundary}>Reintentar {seccion}</button>
    </div>
  );
}

export function Seccion({ nombre, onReintentar, children }: { nombre: string; onReintentar?: () => void; children: React.ReactNode }) {
  return (
    <ErrorBoundary fallbackRender={(p) => Alternativa(p, nombre)} onError={(error) => reportar(error)} onReset={onReintentar}>
      {children}
    </ErrorBoundary>
  );
}

let servicioCaido = true; // el servicio está caído hasta que el usuario reintenta (y "ya se recuperó")
export function Recomendaciones() {
  if (servicioCaido) throw new Error('Servicio de recomendaciones caído');
  return <ul><li>Café de Huila</li><li>Panela orgánica</li></ul>;
}
export function repararRecomendaciones() { servicioCaido = false; }
export function tumbarRecomendaciones() { servicioCaido = true; }

export function Inventario() {
  return <p>Inventario: 42 unidades</p>;
}

/* ---------- Punto 4: un error en un manejador de eventos no lo atrapa el límite ---------- */
export function BotonQueFallaSinManejo() {
  function guardar() {
    throw new Error('Fallo dentro del manejador');
  }
  return <button type="button" onClick={guardar}>Guardar sin manejo</button>;
}

export function BotonQueFallaConManejo({ accion }: { accion: () => Promise<void> }) {
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  async function guardar() {
    setError(null);
    try {
      await accion();
      setOk(true);
    } catch (e) {
      reportar(e);                              // inesperado: se reporta
      setError('No se pudo guardar. Intenta de nuevo.'); // y se muestra donde ocurrió
    }
  }
  return (
    <div>
      <button type="button" onClick={guardar}>Guardar con manejo</button>
      {error && <p role="alert">{error}</p>}
      {ok && <p role="status">Guardado</p>}
    </div>
  );
}

/* ---------- Punto 5: HOC y propiedad de renderizado convertidos a hooks ---------- */
interface Posicion { x: number; y: number }

// Antes: propiedad de renderizado
export function Raton({ children }: { children: (p: Posicion) => React.ReactNode }) {
  const [posicion, setPosicion] = useState<Posicion>({ x: 0, y: 0 });
  useEffect(() => {
    const mover = (e: MouseEvent) => setPosicion({ x: e.clientX, y: e.clientY });
    window.addEventListener('mousemove', mover);
    return () => window.removeEventListener('mousemove', mover);
  }, []);
  return <>{children(posicion)}</>;
}

// Después: hook
export function useRaton(): Posicion {
  const [posicion, setPosicion] = useState<Posicion>({ x: 0, y: 0 });
  useEffect(() => {
    const mover = (e: MouseEvent) => setPosicion({ x: e.clientX, y: e.clientY });
    window.addEventListener('mousemove', mover);
    return () => window.removeEventListener('mousemove', mover);
  }, []);
  return posicion;
}

export const CoordenadasConRenderProp = () => <Raton>{(p) => <output>{p.x},{p.y}</output>}</Raton>;
export function CoordenadasConHook() {
  const p = useRaton();
  return <output>{p.x},{p.y}</output>;
}

// Antes: HOC que inyecta un registro de visitas
export function conRegistro<P extends object>(Componente: React.ComponentType<P & { registrar: (e: string) => void }>, destino: string[]) {
  return function ConRegistro(props: P) {
    const registrar = useCallback((e: string) => destino.push(e), []);
    return <Componente {...props} registrar={registrar} />;
  };
}
// Después: hook
export function useRegistro(destino: string[]) {
  return useCallback((e: string) => destino.push(e), [destino]);
}
