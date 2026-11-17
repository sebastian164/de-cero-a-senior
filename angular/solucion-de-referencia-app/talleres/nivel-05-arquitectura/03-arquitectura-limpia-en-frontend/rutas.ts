import { Routes } from '@angular/router';
import { AsistenteSolicitud } from './aplicacion/asistente';
import { provideSolicitudes } from './datos/configuracion';
import { AsistentePagina } from './ui/asistente.pagina';

export default [{ path: '', component: AsistentePagina, providers: [...provideSolicitudes(), AsistenteSolicitud] }] satisfies Routes;
