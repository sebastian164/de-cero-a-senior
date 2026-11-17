import { Component, signal } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { ListaPaginadaComponent } from './lista-paginada.component';
import { ContadorAManoComponent } from './migracion/contador-a-mano.component';
import { ContadorMigradoComponent } from './migracion/resultado/contador-migrado.component';
import { acotarPagina, numeroDePaginas } from './paginacion';
import { PaginacionComponent } from './paginacion.component';
import { PaginacionDecoradoresComponent } from './paginacion-decoradores.component';

@Component({
  imports: [PaginacionComponent],
  template: `<app-paginacion [total]="total()" tamano="10" [(pagina)]="pagina" /><p>padre: {{ pagina() }}</p>`,
})
class Anfitrion { total = signal(70); pagina = signal(1); }

describe('Taller n1-03 — paginación', () => {
  it('reglas puras', () => {
    expect(numeroDePaginas(70, 10)).toBe(7);
    expect(numeroDePaginas(0, 10)).toBe(1);
    expect(acotarPagina(9, 7)).toBe(7);
    expect(acotarPagina(0, 7)).toBe(1);
  });

  it('1-2. tamaño "10" llega como número; texto, anterior y siguiente son derivados; [(pagina)] avisa al padre', async () => {
    await render(Anfitrion);
    const u = userEvent.setup();
    expect(screen.getByText('Página 1 de 7')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    await u.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(screen.getByText('Página 2 de 7')).toBeInTheDocument();
    expect(screen.getByText('padre: 2')).toBeInTheDocument();
  });

  it('3. si el total baja y la página deja de existir, se corrige sola, también en el padre', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.pagina.set(7);
    await fixture.whenStable();
    expect(screen.getByText('Página 7 de 7')).toBeInTheDocument();
    fixture.componentInstance.total.set(25);
    await fixture.whenStable();
    expect(screen.getByText('Página 3 de 3')).toBeInTheDocument();
    expect(screen.getByText('padre: 3')).toBeInTheDocument();
  });

  it('3b. clic en Siguiente y, sin detección de cambios en medio, bajar el total: el padre también se corrige', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.pagina.set(2);
    await fixture.whenStable();
    fixture.componentInstance.pagina.set(3);                 // pedida 3...
    fixture.componentInstance.total.set(15);                 // ...y en seguida solo hay 2 páginas
    await fixture.whenStable();
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument();
    expect(screen.getByText('padre: 2')).toBeInTheDocument();
  });

  it('4. "Ir a la primera" vuelve a la página 1 y lleva el foco al primer elemento de la lista', async () => {
    const { fixture } = await render(ListaPaginadaComponent);
    const u = userEvent.setup();
    await u.click(screen.getByRole('button', { name: 'Siguiente' }));
    await u.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(screen.getByText('Elemento 11')).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Ir a la primera' }));
    await fixture.whenStable();
    expect(document.activeElement).toHaveTextContent('Elemento 1');
  });

  it('6. la versión con decoradores acepta tamano="5" como texto y deja que el texto se desfase', async () => {
    const { fixture } = await render(PaginacionDecoradoresComponent, { inputs: { total: 20, tamano: '5' } });
    const c = fixture.componentInstance;
    expect(typeof c.tamano).toBe('string');                 // con input({ transform }) sería un número
    c.pagina = 3;                                            // nadie impide escribir la entrada desde fuera…
    expect(c.texto).toBe('Página 1 de 4');                   // …y el estado derivado queda viejo
  });

  it('7. el componente migrado por los esquemas funciona con señales', async () => {
    await render(ContadorMigradoComponent, { inputs: { valor: 2, unidad: 'kg' } });
    expect(screen.getByText('2 kg')).toBeInTheDocument();
  });

  it('7. el revisado a mano reemplaza ngOnChanges por computed', async () => {
    const { fixture } = await render(ContadorAManoComponent, { inputs: { valor: 3 } });
    expect(fixture.nativeElement.textContent).toContain('3 unidades');
    fixture.componentRef.setInput('valor', 4);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('4 unidades');
  });
});
