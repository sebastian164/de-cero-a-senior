'use client';
// Taller nivel-06/03, puntos 1 y 3: controles solo de ratón frente a controles con el elemento correcto, y un diálogo nativo.
import { useRef, useState } from 'react';

/** Antes: <div> y <span> clicables. No reciben foco ni responden a Enter o Espacio. */
export function ControlesSoloRaton() {
  const [mensaje, setMensaje] = useState('');
  return (
    <div>
      {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- es el "antes" del taller */}
      <div className="falso-boton" onClick={() => setMensaje('Guardado (ratón)')} style={{ border: '1px solid currentColor', display: 'inline-block', padding: 4 }}>Guardar</div>
      <p role="status">{mensaje}</p>
    </div>
  );
}

/** Después: un botón de verdad. */
export function ControlesConTeclado() {
  const [mensaje, setMensaje] = useState('');
  return (
    <div>
      <button type="button" onClick={() => setMensaje('Guardado')}>Guardar</button>
      <p role="status">{mensaje}</p>
    </div>
  );
}

/** Diálogo con <dialog> nativo: trampa de foco, Escape y devolución del foco los resuelve el navegador. */
export function DialogoNativo() {
  const dialogo = useRef<HTMLDialogElement>(null);
  return (
    <div>
      <button type="button" onClick={() => dialogo.current?.showModal()}>Abrir diálogo</button>
      <dialog ref={dialogo} aria-labelledby="titulo-dialogo">
        <form method="dialog">
          <h3 id="titulo-dialogo">¿Eliminar el pedido?</h3>
          <p>Esta acción no se puede deshacer.</p>
          <button value="cancelar">Cancelar</button> <button value="eliminar">Eliminar</button>
        </form>
      </dialog>
    </div>
  );
}

/** Antes: "diálogo" hecho a mano con un div: sin rol, sin gestión del foco, sin Escape. */
export function DialogoCasero() {
  const [abierto, setAbierto] = useState(false);
  return (
    <div>
      {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- es el "antes" del taller */}
      <span onClick={() => setAbierto(true)} style={{ textDecoration: 'underline', cursor: 'pointer' }}>Abrir diálogo casero</span>
      {abierto && (
        <div style={{ border: '1px solid currentColor', padding: 8 }}>
          Mensaje{' '}
          {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- es el "antes" del taller */}
          <span onClick={() => setAbierto(false)}>✕</span>
        </div>
      )}
    </div>
  );
}
