import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { PedidosI18n } from './pedidos.component';

@Component({ imports: [PedidosI18n], template: `<div><h1>Accesibilidad e i18n</h1><app-pedidos-i18n /></div>` })
class Demo {}
export default [{ path: '', component: Demo }] satisfies Routes;
