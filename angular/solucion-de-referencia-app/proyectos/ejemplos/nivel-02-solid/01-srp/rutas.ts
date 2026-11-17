import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { FacturaComponent } from './factura.component';

@Component({ imports: [FacturaComponent], template: `<app-factura id="F-1" />` })
class Demo {}
export default [{ path: '', component: Demo }] satisfies Routes;
