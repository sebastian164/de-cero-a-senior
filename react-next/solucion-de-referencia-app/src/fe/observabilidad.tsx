'use client';
// Taller frontend-senior/02: manejador global de errores, límites de error por sección y banderas.
import { useEffect, useState } from 'react';
import { ErrorBoundary, type FallbackProps } from 'react-error-boundary';
import { provocarFallaNoControlada } from './falla';

const VERSION = process.env.NEXT_PUBLIC_VERSION ?? 'sin-version';

/** Datos que se envían: versión, ruta, tipo, traza. Nada de formularios, cookies ni datos personales. */
function enviarError(error: unknown, esperado = false) {
  const e = error instanceof Error ? error : new Error(String(error));
  const cuerpo = JSON.stringify({ mensaje: e.message, ruta: location.pathname, version: VERSION, pila: e.stack, esperado });
  if (!navigator.sendBeacon('/api/errores', cuerpo)) void fetch('/api/errores', { method: 'POST', body: cuerpo, keepalive: true });
}

/** Manejador global: lo que nadie atrapó. */
export function ManejadorGlobal() {
  useEffect(() => {
    const alError = (ev: ErrorEvent) => enviarError(ev.error ?? ev.message);
    const alRechazo = (ev: PromiseRejectionEvent) => enviarError(ev.reason);
    window.addEventListener('error', alError);
    window.addEventListener('unhandledrejection', alRechazo);
    return () => {
      window.removeEventListener('error', alError);
      window.removeEventListener('unhandledrejection', alRechazo);
    };
  }, []);
  return null;
}

function Alternativa({ resetErrorBoundary }: FallbackProps, seccion: string) {
  return (
    <div role="alert">
      <p>No pudimos mostrar {seccion}. El resto de la página sigue funcionando.</p>
      <button type="button" onClick={resetErrorBoundary}>
        Reintentar {seccion}
      </button>
    </div>
  );
}
/** Límite de error por sección. Los errores de validación (esperados) no pasan por aquí: no se reportan como fallos. */
export function Seccion({ nombre, children }: { nombre: string; children: React.ReactNode }) {
  return (
    <ErrorBoundary fallbackRender={(p) => Alternativa(p, nombre)} onError={(e) => enviarError(e)}>
      {children}
    </ErrorBoundary>
  );
}

export function PanelQuePuedeRomperse({ nombre }: { nombre: string }) {
  const [roto, setRoto] = useState(false);
  if (roto) throw new Error(`La sección ${nombre} falló al renderizar`);
  return (
    <div>
      <p>Sección {nombre}: funcionando</p>
      <button type="button" onClick={() => setRoto(true)}>
        Romper {nombre}
      </button>
    </div>
  );
}

export function BotonesDeFalla() {
  return (
    <div>
      <button type="button" onClick={() => setTimeout(() => provocarFallaNoControlada(), 0)}>
        Provocar error no controlado
      </button>{' '}
      <button type="button" onClick={() => void Promise.reject(new Error('Promesa rechazada sin capturar'))}>
        Provocar promesa rechazada
      </button>
    </div>
  );
}

export function NuevaFicha() {
  const [estado, setEstado] = useState<'cargando' | boolean>('cargando');
  useEffect(() => {
    let uid = localStorage.getItem('uid');
    if (!uid) {
      uid = crypto.randomUUID();
      localStorage.setItem('uid', uid);
    }
    void fetch(`/api/banderas/nueva-ficha?usuario=${encodeURIComponent(uid)}`)
      .then((r) => r.json())
      .then((d: { activa: boolean }) => setEstado(d.activa));
  }, []);
  if (estado === 'cargando') return <p>Consultando bandera…</p>;
  return <p data-testid="ficha">{estado ? 'Ficha NUEVA (bandera activa para ti)' : 'Ficha clásica'}</p>;
}
