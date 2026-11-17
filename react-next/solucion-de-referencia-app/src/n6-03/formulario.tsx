'use client';
import { useState } from 'react';
import { Campo } from '@/react-next/n6-03-accesibilidad/campo';

export function FormularioAccesible() {
  const [errores, setErrores] = useState<{ correo?: string; nombre?: string }>({});
  const [enviado, setEnviado] = useState(false);
  return (
    <form noValidate onSubmit={(e) => {
      e.preventDefault();
      const d = new FormData(e.currentTarget);
      const nuevos: typeof errores = {};
      if (!String(d.get('nombre')).trim()) nuevos.nombre = 'Escribe tu nombre';
      if (!/^\S+@\S+\.\S+$/.test(String(d.get('correo')))) nuevos.correo = 'Escribe un correo válido';
      setErrores(nuevos); setEnviado(Object.keys(nuevos).length === 0);
    }}>
      <Campo etiqueta="Nombre" name="nombre" error={errores.nombre} autoComplete="name" />
      <Campo etiqueta="Correo" name="correo" type="email" error={errores.correo} autoComplete="email" />
      <button type="submit">Enviar</button>
      <p role="status">{enviado ? 'Formulario enviado' : ''}</p>
    </form>
  );
}
