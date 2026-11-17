'use client';
// Taller nivel-06/01, punto 7: métricas web en campo con la biblioteca web-vitals, enviadas al colector propio.
import { useEffect } from 'react';
import { onCLS, onFCP, onINP, onLCP, onTTFB, type Metric } from 'web-vitals';

function enviar(m: Metric) {
  const cuerpo = JSON.stringify({ nombre: m.name, valor: m.value, ruta: location.pathname, id: m.id, calificacion: m.rating });
  if (!navigator.sendBeacon('/api/vitales', cuerpo)) void fetch('/api/vitales', { method: 'POST', body: cuerpo, keepalive: true });
}
export function ReporteDeVitales() {
  useEffect(() => {
    for (const f of [onLCP, onCLS, onINP, onFCP, onTTFB]) f(enviar, { reportAllChanges: true });
  }, []);
  return (
    <p>
      Las métricas se envían a <code>/api/vitales</code>.
    </p>
  );
}
