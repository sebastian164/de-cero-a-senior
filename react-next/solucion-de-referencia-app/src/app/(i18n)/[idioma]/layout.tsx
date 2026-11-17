import { notFound } from 'next/navigation';
import '../../globals.css';
import { IDIOMAS, esIdioma } from '@app/i18n/diccionarios';

export function generateStaticParams() { return IDIOMAS.map((idioma) => ({ idioma })); }

// Diseño raíz propio de la sección internacionalizada: el atributo lang sigue al idioma de la ruta.
export default async function DisenoIdioma({ params, children }: { params: Promise<{ idioma: string }>; children: React.ReactNode }) {
  const { idioma } = await params;
  if (!esIdioma(idioma)) notFound();
  return <html lang={idioma}><body>{children}</body></html>;
}
