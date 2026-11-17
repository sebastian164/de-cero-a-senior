'use client';
// Taller nivel-06/03, punto 5. Una fecha fija en UTC: 2 de abril de 2026, 02:30. En Bogotá (UTC-5) aún es 1 de abril, 21:30.
const MOMENTO = new Date('2026-04-02T02:30:00Z');

/** Mal: usa la zona horaria de quien renderiza. El servidor y el navegador no coinciden, y la hidratación falla. */
export function FechaMal() {
  return <p>Fecha: <time data-testid="fecha" dateTime={MOMENTO.toISOString()}>{MOMENTO.toLocaleString('es-CO')}</time></p>;
}
/** Bien: la zona horaria se fija, y también hour12: WebKit escribe "p.m." donde Node y Chromium escriben "p. m." (React #418). Mismo texto en el servidor y en cualquier navegador. */
export function FechaBien() {
  return <p>Fecha: <time data-testid="fecha" dateTime={MOMENTO.toISOString()}>{new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeStyle: 'short', timeZone: 'America/Bogota', hour12: false }).format(MOMENTO)}</time></p>;
}
