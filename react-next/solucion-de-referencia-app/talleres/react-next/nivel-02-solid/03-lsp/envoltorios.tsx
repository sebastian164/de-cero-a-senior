'use client';
// Punto 1: envoltorios que respetan todo el contrato del elemento nativo (propiedades, accesibilidad y ref).
import Link from 'next/link';
import clsx from 'clsx';
import { useId } from 'react';

export type BotonProps = React.ComponentProps<'button'> & { variante?: 'primario' | 'secundario' | 'peligro' };

export function Boton({ variante = 'secundario', type = 'button', className, ...resto }: BotonProps) {
  return <button type={type} className={clsx('btn', `btn-${variante}`, className)} {...resto} />;
}

export type CampoProps = React.ComponentProps<'input'> & { etiqueta: string; error?: string; ayuda?: string };

export function Campo({ etiqueta, error, ayuda, id, 'aria-describedby': descritoPor, ...resto }: CampoProps) {
  const generado = useId();
  const idCampo = id ?? generado;
  const idError = `${idCampo}-error`;
  const idAyuda = `${idCampo}-ayuda`;
  const descripciones = clsx(descritoPor, ayuda && idAyuda, error && idError) || undefined;
  return (
    <div>
      <label htmlFor={idCampo}>{etiqueta}</label>
      <input id={idCampo} aria-invalid={error ? true : undefined} aria-describedby={descripciones} {...resto} />
      {ayuda && <small id={idAyuda}>{ayuda}</small>}
      {error && <p id={idError} role="alert">{error}</p>}
    </div>
  );
}

export type EnlaceProps = React.ComponentProps<typeof Link> & { externo?: boolean };

/** Enlace interno (next/link, con precarga) o externo; en ambos casos un <a> con su contrato completo. */
export function Enlace({ externo = false, href, children, rel, target, ...resto }: EnlaceProps) {
  if (externo) {
    return (
      <a href={String(href)} target={target ?? '_blank'} rel={clsx(rel, 'noopener noreferrer')} {...resto}>
        {children}<span className="suave"> (abre en otra pestaña)</span>
      </a>
    );
  }
  return <Link href={href} rel={rel} target={target} {...resto}>{children}</Link>;
}
