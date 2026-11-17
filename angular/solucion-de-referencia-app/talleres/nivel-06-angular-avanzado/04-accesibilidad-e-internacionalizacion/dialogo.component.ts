import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

/** Punto 4: diálogo accesible con el CDK: rol, nombre, foco atrapado, Escape y retorno del foco. */
@Component({
  selector: 'app-dialogo-confirmar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dialogo" role="alertdialog" aria-modal="true" aria-labelledby="dlg-titulo" aria-describedby="dlg-texto">
      <h2 id="dlg-titulo">Eliminar pedido</h2>
      <p id="dlg-texto">¿Seguro que quieres eliminar el pedido {{ datos.pedido }}? No se puede deshacer.</p>
      <button type="button" (click)="ref.close(false)">Cancelar</button>
      <button type="button" (click)="ref.close(true)">Eliminar</button>
    </div>
  `,
  styles: `.dialogo { background: #fff; color: #111; padding: 16px; border: 2px solid #111; max-width: 24rem }`,
})
export class DialogoConfirmar {
  protected readonly ref = inject<DialogRef<boolean>>(DialogRef);
  protected readonly datos = inject<{ pedido: number }>(DIALOG_DATA);
}
