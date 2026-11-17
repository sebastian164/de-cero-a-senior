// ---- react-next/nivel-06-avanzado/03-accesibilidad-e-internacionalizacion/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
import { useId } from 'react';
import type { CampoProps } from './contexto';

function Campo({ etiqueta, error, ...resto }: CampoProps) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id}>{etiqueta}</label>
      <input id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined} {...resto} />
      {error && <p id={`${id}-error`} role="alert">{error}</p>}
    </div>
  );
}

export { Campo };
