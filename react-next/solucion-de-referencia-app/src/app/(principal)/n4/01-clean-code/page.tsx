import { Bloque } from '@app/bloque';
import { DemoPanel, DemoReloj } from '@app/demos-n4';

export const metadata = { title: 'nivel-04/01 · Clean code en React' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-04/01 · Clean code en React</h1>
      <Bloque titulo="Taller: panel de pedidos, versión limpia" origen="talleres/…/01-clean-code-en-react/despues/">
        <DemoPanel version="despues" />
      </Bloque>
      <Bloque titulo="Taller: el panel original (antes, mal a propósito)" origen="talleres/…/01-clean-code-en-react/antes/">
        <DemoPanel version="antes" />
      </Bloque>
      <Bloque titulo="Taller punto 6: lista costosa sin y con memo" origen="talleres/…/01-clean-code-en-react/memorizacion.tsx">
        <DemoReloj />
      </Bloque>
    </main>
  );
}
