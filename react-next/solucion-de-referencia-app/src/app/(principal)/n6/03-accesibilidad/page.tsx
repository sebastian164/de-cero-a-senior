import Link from 'next/link';
import { Bloque } from '@app/bloque';
import { ControlesConTeclado, DialogoNativo } from '@app/n6-03/teclado';
import { FormularioAccesible } from '@app/n6-03/formulario';

export const metadata = { title: 'nivel-06/03 · Accesibilidad' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-06/03 · Accesibilidad e internacionalización</h1>
      <Bloque titulo="Controles con el elemento correcto" origen="src/n6-03/teclado.tsx"><ControlesConTeclado /></Bloque>
      <Bloque titulo="Diálogo con &lt;dialog&gt; nativo" origen="src/n6-03/teclado.tsx"><DialogoNativo /></Bloque>
      <Bloque titulo="Campo del README (useId, aria-invalid, aria-describedby)" origen="proyectos/react-next/n6-03-accesibilidad/campo.tsx"><FormularioAccesible /></Bloque>
      <nav aria-label="Más pantallas de la sesión">
        <ul>
          <li><Link href="/n6/03-accesibilidad/antes">Antes: controles solo de ratón (para ver lo que arregla el taller)</Link></li>
          <li><Link href="/n6/03-accesibilidad/hidratacion/bien">Fecha con zona horaria fija</Link></li>
          <li><Link href="/es/productos">Productos en español</Link> · <Link href="/en/productos">Products in English</Link></li>
        </ul>
      </nav>
    </main>
  );
}
