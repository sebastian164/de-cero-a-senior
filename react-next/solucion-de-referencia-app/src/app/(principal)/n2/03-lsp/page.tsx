import { Bloque } from '@app/bloque';
import { DemoLsp } from '@talleres/react-next/nivel-02-solid/03-lsp/demo';

export const metadata = { title: 'nivel-02/03 · LSP' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-02/03 · LSP en React</h1>
      <Bloque titulo="Taller: Boton, Campo y Enlace con el contrato del elemento nativo" origen="talleres/react-next/nivel-02-solid/03-lsp/envoltorios.tsx">
        <DemoLsp />
      </Bloque>
    </main>
  );
}
