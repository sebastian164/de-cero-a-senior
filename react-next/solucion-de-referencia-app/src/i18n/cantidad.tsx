'use client';
import { useState } from 'react';

interface Mensajes { zero: string; one: string; other: string; agregar: string; quitar: string }

/** Plurales con Intl.PluralRules y mensajes con variable: nunca se concatenan fragmentos. */
export function Cantidad({ locale, mensajes }: { locale: string; mensajes: Mensajes }) {
  const [n, setN] = useState(0);
  const regla = new Intl.PluralRules(locale).select(n);
  const plantilla = n === 0 ? mensajes.zero : regla === 'one' ? mensajes.one : mensajes.other;
  return (
    <p>
      <button type="button" onClick={() => setN(n + 1)}>{mensajes.agregar}</button>{' '}
      <button type="button" disabled={n === 0} onClick={() => setN(n - 1)}>{mensajes.quitar}</button>{' '}
      <span role="status" data-testid="cantidad">{plantilla.replace('{n}', new Intl.NumberFormat(locale).format(n))}</span>
    </p>
  );
}
