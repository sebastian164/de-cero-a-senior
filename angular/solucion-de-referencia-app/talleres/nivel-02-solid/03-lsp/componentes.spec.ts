import { Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { BotonPrimarioComponent } from './boton.component';
import { EstrellasComponent } from './estrellas.component';
import { Csv, ExportadorLigeroAntes, formatosOfrecidos, Tsv } from './reportes';

describe('Taller n2-03', () => {
  describe('3. botón primario sustituible por <button>', () => {
    @Component({
      imports: [BotonPrimarioComponent],
      template: `<button appBotonPrimario type="submit" [disabled]="apagado()" aria-describedby="ayuda" (click)="clics = clics + 1">Guardar</button>`,
    })
    class Uso { apagado = signal(false); clics = 0; }

    it('respeta disabled, type y atributos ARIA', async () => {
      const { fixture } = await render(Uso);
      const b = screen.getByRole('button', { name: 'Guardar' });
      expect(b).toHaveAttribute('type', 'submit');
      expect(b).toHaveAttribute('aria-describedby', 'ayuda');
      fixture.componentInstance.apagado.set(true);
      await fixture.whenStable();
      expect(b).toBeDisabled();
      await userEvent.click(b);
      expect(fixture.componentInstance.clics).toBe(0);
    });

    it('el atributo estático disabled (sin valor) deshabilita, como en el nativo', async () => {
      @Component({ imports: [BotonPrimarioComponent], template: `<button appBotonPrimario type="button" disabled>X</button>` })
      class Estatico {}
      await render(Estatico);
      expect(screen.getByRole('button', { name: 'X' })).toBeDisabled();
    });

    it('recibe el foco con Tab y se activa con Enter y con Espacio, como un botón nativo', async () => {
      const { fixture } = await render(Uso);
      const u = userEvent.setup();
      await u.tab();
      expect(screen.getByRole('button', { name: 'Guardar' })).toHaveFocus();
      await u.keyboard('{Enter}');
      await u.keyboard(' ');
      expect(fixture.componentInstance.clics).toBe(2);
    });
  });

  describe('4. estrellas con ControlValueAccessor', () => {
    @Component({ imports: [EstrellasComponent, ReactiveFormsModule], template: `<app-estrellas [formControl]="control" />` })
    class Formulario { control = new FormControl(2); }

    it('escribe el valor inicial, avisa los cambios y marca tocado al salir', async () => {
      const { fixture } = await render(Formulario);
      const c = fixture.componentInstance.control;
      expect(screen.getByRole('radio', { name: '2 estrellas' })).toHaveAttribute('aria-checked', 'true');
      await userEvent.click(screen.getByRole('radio', { name: '4 estrellas' }));
      expect(c.value).toBe(4);
      await userEvent.tab();
      expect(c.touched).toBe(true);
      c.setValue(1);
      await fixture.whenStable();
      expect(screen.getByRole('radio', { name: '1 estrella' })).toHaveAttribute('aria-checked', 'true');
    });

    it('disable() del formulario deshabilita el control y deja de aceptar valores', async () => {
      const { fixture } = await render(Formulario);
      const c = fixture.componentInstance.control;
      c.disable();
      await fixture.whenStable();
      expect(screen.getByRole('radio', { name: '5 estrellas' })).toBeDisabled();
      await userEvent.click(screen.getByRole('radio', { name: '5 estrellas' }));
      expect(c.value).toBe(2);
    });
  });

  it('2. el exportador de antes promete PDF y lanza; el rediseño solo ofrece lo que existe', () => {
    expect(() => new ExportadorLigeroAntes().aPdf()).toThrow('No implementado');
    expect(formatosOfrecidos([new Csv(), new Tsv()])).toEqual(['csv', 'tsv']);
    expect(new Csv().exportar([['a;b', 'c']])).toBe('a,b;c');
  });
});
