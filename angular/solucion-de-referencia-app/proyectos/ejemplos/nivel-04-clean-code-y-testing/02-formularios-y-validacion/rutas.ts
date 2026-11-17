import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { RegistroComponent } from './registro.component';

@Component({ imports: [RegistroComponent], template: `<h1>Registro (formularios con señales)</h1><app-registro />` })
class Pagina {}
export default [{ path: '', component: Pagina }] satisfies Routes;
