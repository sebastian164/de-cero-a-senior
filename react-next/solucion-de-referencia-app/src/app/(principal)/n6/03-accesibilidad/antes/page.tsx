import { ControlesSoloRaton, DialogoCasero } from '@app/n6-03/teclado';

export const metadata = { title: 'Antes: solo ratón' };

export default function Pagina() {
  return (
    <main>
      <h1>Antes: controles solo de ratón</h1>
      <ControlesSoloRaton />
      <DialogoCasero />
      <input />
      {/* eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text -- es el "antes" del taller: imagen sin texto alternativo */}
      <img src="/avatar.svg" width={32} height={32} />
    </main>
  );
}
