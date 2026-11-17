'use client';
// Escucha los avisos que emite avisar() (README nivel-05/02) y los muestra como alerta.
import { useEffect, useState } from 'react';
import { avisos } from '@/react-next/n5-02-datos-cache/avisos';

export function AvisoDeError() {
  const [mensaje, setMensaje] = useState('');
  useEffect(() => {
    const oyente = (e: Event) => setMensaje((e as CustomEvent<string>).detail);
    avisos.addEventListener('aviso', oyente);
    return () => avisos.removeEventListener('aviso', oyente);
  }, []);
  return mensaje ? <p role="alert">{mensaje}</p> : null;
}
