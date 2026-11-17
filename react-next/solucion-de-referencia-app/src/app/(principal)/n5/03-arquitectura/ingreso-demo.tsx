'use client';
import { useState } from 'react';

export function IngresoDemo() {
  const [actual, setActual] = useState('nadie');
  async function ingresar(usuario: string) {
    await fetch(`/api/n5-02/ingreso?usuario=${usuario}`);
    setActual(usuario);
  }
  return (
    <p>
      <button type="button" onClick={() => ingresar('u-1')}>Ingresar como Ana (u-1)</button>{' '}
      <button type="button" onClick={() => ingresar('u-2')}>Ingresar como Luis (u-2)</button>{' '}
      <span role="status">Sesión: {actual}</span>
    </p>
  );
}
