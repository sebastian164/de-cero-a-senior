// 2. Pruebas de los componentes de presentación sin ningún servicio en TestBed: solo entradas y eventos.
import { Component, signal } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { FiltroCategorias, ResumenSeleccion, TarjetaCurso } from './ui';
import { PaginaCursos } from './contenedor';

describe('Taller n3-01 — presentación', () => {
  it('tarjeta: muestra el curso y proyecta las acciones', async () => {
    @Component({ imports: [TarjetaCurso], template: `<app-tarjeta-curso [curso]="c"><button acciones>Hola</button></app-tarjeta-curso>` })
    class Uso { c = { id: '1', nombre: 'N', categoria: 'K', precio: 1000 }; }
    await render(Uso);
    expect(screen.getByRole('heading', { name: 'N' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hola' })).toBeInTheDocument();
  });
  it('resumen: deshabilita confirmar sin cursos y emite confirmar', async () => {
    const confirmar = vi.fn();
    const { fixture } = await render(ResumenSeleccion, { inputs: { cantidad: 0, total: 0 }, on: { confirmar } });
    expect(screen.getByRole('button')).toBeDisabled();
    fixture.componentRef.setInput('cantidad', 1);
    await fixture.whenStable();
    await userEvent.click(screen.getByRole('button'));
    expect(confirmar).toHaveBeenCalledTimes(1);
  });
  it('filtro: [(elegida)] avisa al padre', async () => {
    @Component({ imports: [FiltroCategorias], template: `<app-filtro-categorias [categorias]="['A','B']" [(elegida)]="e" /><p>{{ e() }}</p>` })
    class Uso { e = signal('A'); }
    await render(Uso);
    await userEvent.click(screen.getByLabelText('B'));
    expect(screen.getByText('B', { selector: 'p' })).toBeInTheDocument();
  });
  it('contenedor: elegir, ver el resumen y confirmar', async () => {
    await render(PaginaCursos);
    const u = userEvent.setup();
    await u.click(screen.getByRole('button', { name: 'Elegir Angular desde cero' }));
    await u.click(screen.getByLabelText('Backend'));
    await u.click(screen.getByRole('button', { name: 'Elegir Kafka práctico' }));
    expect(screen.getByLabelText('Selección')).toHaveTextContent('2 cursos');
    await u.click(screen.getByRole('button', { name: 'Confirmar inscripción' }));
    expect(screen.getByRole('status')).toHaveTextContent('Inscripción confirmada');
  });
});
