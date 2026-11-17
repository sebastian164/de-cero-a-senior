import { Service } from '@angular/core';

// Comprueba la nota del README: desde Angular 22 existe @Service(), equivalente a @Injectable({ providedIn: 'root' }).
@Service()
export class RelojDeEjemplo {
  ahora(): number { return 42; }
}
