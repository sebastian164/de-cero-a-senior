import Link from 'next/link';
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { IDIOMAS, LOCALES, MONEDAS, cargarDiccionario, esIdioma } from '@app/i18n/diccionarios';
import { Cantidad } from '@app/i18n/cantidad';

export function generateStaticParams() { return IDIOMAS.map((idioma) => ({ idioma })); }

export async function generateMetadata({ params }: { params: Promise<{ idioma: string }> }): Promise<Metadata> {
  const { idioma } = await params;
  if (!esIdioma(idioma)) return {};
  const t = await cargarDiccionario(idioma);
  return { title: t.titulo, alternates: { languages: { es: '/es/productos', en: '/en/productos' } } };
}

export default async function Pagina({ params }: { params: Promise<{ idioma: string }> }) {
  const { idioma } = await params;
  if (!esIdioma(idioma)) notFound();
  const t = await cargarDiccionario(idioma);
  const locale = LOCALES[idioma];
  const precio = new Intl.NumberFormat(locale, { style: 'currency', currency: MONEDAS[idioma], maximumFractionDigits: idioma === 'es' ? 0 : 2 }).format(idioma === 'es' ? 32000 : 8.5);
  const fecha = new Intl.DateTimeFormat(locale, { dateStyle: 'long', timeZone: 'America/Bogota' }).format(new Date('2026-04-02T02:30:00Z'));
  return (
    <main>
      <h1>{t.titulo}</h1>
      <h2>{t.ficha}</h2>
      <p>{t.precioEtiqueta}: <span data-testid="precio">{precio}</span></p>
      <p>{t.fechaEtiqueta}: <time data-testid="fecha" dateTime="2026-04-02T02:30:00Z">{fecha}</time></p>
      <Cantidad locale={locale} mensajes={{ ...t.cantidad, agregar: t.agregar, quitar: t.quitar }} />
      <p><Link href={`/${idioma === 'es' ? 'en' : 'es'}/productos`} hrefLang={idioma === 'es' ? 'en' : 'es'}>{t.cambiar}</Link></p>
    </main>
  );
}
