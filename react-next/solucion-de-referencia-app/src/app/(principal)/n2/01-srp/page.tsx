import Link from 'next/link';
import { Bloque } from '@app/bloque';
import { EditorFacturaAntes } from '@talleres/react-next/nivel-02-solid/01-srp/antes/editor-factura';
import { EditorFactura } from '@talleres/react-next/nivel-02-solid/01-srp/despues/editor-factura';

export const metadata = { title: 'nivel-02/01 · SRP' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-02/01 · SRP en React</h1>
      <p><Link href="/n2/01-srp/facturas/2">Ejemplo “Después” del README: /n2/01-srp/facturas/2 (componente de servidor)</Link></p>
      <Bloque titulo="Taller: el componente de partida (más de 200 líneas)" origen="talleres/react-next/nivel-02-solid/01-srp/antes/editor-factura.tsx">
        <EditorFacturaAntes id="1" />
      </Bloque>
      <Bloque titulo="Taller: separado en dominio, datos, hook y presentación" origen="talleres/react-next/nivel-02-solid/01-srp/despues/">
        <EditorFactura id="2" />
      </Bloque>
    </main>
  );
}
