import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { AvatarComponent, ContadorPedidosComponent, proveedoresIsp } from './isp';

@Component({ imports: [AvatarComponent, ContadorPedidosComponent], template: `<app-avatar fotoUrl="favicon.ico" nombre="Ana Gómez" /><app-contador-pedidos />` })
class Demo {}
export default [{ path: '', component: Demo, providers: proveedoresIsp }] satisfies Routes;
