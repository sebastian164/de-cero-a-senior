// Solución del taller de nivel-06/03 (punto 6: comprobaciones de accesibilidad dentro de las pruebas unitarias, con axe-core en jsdom).
import axe from 'axe-core';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Campo } from '@/react-next/n6-03-accesibilidad/campo';
import { ejemplos } from '@/react-next/n6-03-accesibilidad/formatos';
import { FormularioAccesible } from '@app/n6-03/formulario';
import { ControlesSoloRaton } from '@app/n6-03/teclado';
import { Cantidad } from '@app/i18n/cantidad';
import { cargarDiccionario } from '@app/i18n/diccionarios';

async function violaciones(contenedor: Element) {
  // color-contrast no funciona en jsdom (no hay diseño ni estilos calculados): se deja para axe en el navegador.
  const r = await axe.run(contenedor, { rules: { 'color-contrast': { enabled: false } } });
  return r.violations.map((v) => v.id);
}

describe('Campo del README', () => {
  it('asocia etiqueta, error y descripción con identificadores únicos', () => {
    render(<><Campo etiqueta="Nombre" error="Obligatorio" /><Campo etiqueta="Correo" /></>);
    const nombre = screen.getByLabelText('Nombre');
    expect(nombre).toHaveAttribute('aria-invalid', 'true');
    const error = screen.getByRole('alert');
    expect(nombre.getAttribute('aria-describedby')).toBe(error.id);
    expect(screen.getByLabelText('Correo')).not.toHaveAttribute('aria-invalid');
    expect(screen.getByLabelText('Correo')).not.toHaveAttribute('aria-describedby');
    expect(nombre.id).not.toBe(screen.getByLabelText('Correo').id);
  });
  it('axe no encuentra violaciones, con y sin error', async () => {
    const { container, rerender } = render(<Campo etiqueta="Nombre" />);
    expect(await violaciones(container)).toEqual([]);
    rerender(<Campo etiqueta="Nombre" error="Obligatorio" />);
    expect(await violaciones(container)).toEqual([]);
  });
  it('el formulario completo anuncia los errores y se corrige con el teclado', async () => {
    const u = userEvent.setup();
    const { container } = render(<FormularioAccesible />);
    await u.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(screen.getAllByRole('alert').map((a) => a.textContent)).toEqual(['Escribe tu nombre', 'Escribe un correo válido']);
    expect(await violaciones(container)).toEqual([]);
    await u.type(screen.getByLabelText('Nombre'), 'Ana');
    await u.type(screen.getByLabelText('Correo'), 'ana@correo.example');
    await u.keyboard('{Enter}');
    expect(screen.getByText('Formulario enviado')).toBeInTheDocument();
  });
});

describe('lo que axe NO puede ver: el div clicable', () => {
  it('axe no reporta el div clicable (pasa), pero no es alcanzable con Tab: el teclado es una prueba aparte', async () => {
    const u = userEvent.setup();
    const { container } = render(<ControlesSoloRaton />);
    expect(await violaciones(container)).toEqual([]);          // pasa axe...
    await u.tab();
    expect(document.body).toHaveFocus();                        // ...y el control no recibe foco
  });
});

describe('internacionalización', () => {
  const fecha = new Date('2026-04-02T02:30:00Z');
  it('las líneas del README se ejecutan y dan lo esperado', () => {
    expect(() => ejemplos(fecha, 1)).not.toThrow();
    expect(new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' }).format(120000).replace(/\s/g, ' ')).toBe('$ 120.000');
    expect(new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeZone: 'America/Bogota' }).format(fecha)).toBe('1 de abril de 2026');
    expect(new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeZone: 'UTC' }).format(fecha)).toBe('2 de abril de 2026');   // la zona cambia el día
    expect([0, 1, 2].map((n) => new Intl.PluralRules('es').select(n))).toEqual(['other', 'one', 'other']);
  });
  it('los dos diccionarios tienen las mismas claves (nada sin traducir)', async () => {
    const [es, en] = [await cargarDiccionario('es'), await cargarDiccionario('en')];
    const claves = (o: object, p = ''): string[] => Object.entries(o).flatMap(([k, v]) => (typeof v === 'object' ? claves(v, `${p}${k}.`) : [`${p}${k}`])).sort();
    expect(claves(en)).toEqual(claves(es));
  });
  it('los plurales usan la regla del idioma y mensajes con variable', async () => {
    const t = await cargarDiccionario('en');
    const u = userEvent.setup();
    render(<Cantidad locale="en-US" mensajes={{ ...t.cantidad, agregar: t.agregar, quitar: t.quitar }} />);
    expect(screen.getByRole('status')).toHaveTextContent('No items in the cart');
    await u.click(screen.getByRole('button', { name: 'Add' }));
    expect(screen.getByRole('status')).toHaveTextContent('1 item in the cart');
    await u.click(screen.getByRole('button', { name: 'Add' }));
    expect(screen.getByRole('status')).toHaveTextContent('2 items in the cart');
  });
});
