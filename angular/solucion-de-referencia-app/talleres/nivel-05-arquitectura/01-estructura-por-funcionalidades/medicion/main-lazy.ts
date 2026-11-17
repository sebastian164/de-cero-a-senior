import { Component } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, RouterOutlet } from '@angular/router';
import rutas from '../tienda/tienda.rutas';

@Component({ selector: 'app-root', imports: [RouterOutlet], template: '<router-outlet />' })
class Raiz {}
bootstrapApplication(Raiz, { providers: [provideHttpClient(), provideRouter(rutas)] });
