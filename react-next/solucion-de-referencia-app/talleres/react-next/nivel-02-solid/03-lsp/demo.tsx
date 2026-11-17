'use client';
import { useRef, useState } from 'react';
import { Boton, Campo, Enlace } from './envoltorios';

export function DemoLsp() {
  const ref = useRef<HTMLInputElement>(null);
  const [correo, setCorreo] = useState('');
  const error = correo && !correo.includes('@') ? 'Escribe un correo válido' : undefined;
  return (
    <form onSubmit={(e) => e.preventDefault()}>
      <Campo ref={ref} etiqueta="Correo" type="email" name="correo" autoComplete="email" required value={correo} onChange={(e) => setCorreo(e.target.value)} error={error} ayuda="Nunca lo compartimos" />
      <Boton variante="primario" type="submit">Enviar</Boton>{' '}
      <Boton onClick={() => ref.current?.focus()}>Enfocar el campo (ref)</Boton>{' '}
      <Boton disabled>Deshabilitado</Boton>
      <p><Enlace href="/n2/02-ocp">Enlace interno</Enlace> · <Enlace externo href="https://react.dev">react.dev</Enlace></p>
    </form>
  );
}
