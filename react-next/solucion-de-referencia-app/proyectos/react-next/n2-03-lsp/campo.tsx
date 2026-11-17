'use client';
// ---- react-next/nivel-02-solid/03-lsp/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { useId } from 'react';

type CampoProps = React.ComponentProps<'input'> & {
  etiqueta: string;
  error?: string;
};

export function Campo({ etiqueta, error, id, ...resto }: CampoProps) {
  const idGenerado = useId();
  const idCampo = id ?? idGenerado;
  const idError = `${idCampo}-error`;

  return (
    <div>
      <label htmlFor={idCampo}>{etiqueta}</label>
      <input
        id={idCampo}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? idError : undefined}
        {...resto}                                    // todo lo que acepta un input, incluida la ref
      />
      {error && <p id={idError} role="alert">{error}</p>}
    </div>
  );
}
