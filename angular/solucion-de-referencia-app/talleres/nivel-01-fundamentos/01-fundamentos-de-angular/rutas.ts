import { Routes } from '@angular/router';
import { DetalleTareaComponent } from './detalle-tarea.component';
import { ListaTareasComponent } from './lista-tareas.component';

export default [
  { path: '', component: ListaTareasComponent, title: 'Tareas' },
  { path: ':id', component: DetalleTareaComponent, title: 'Detalle de tarea' },
] satisfies Routes;
