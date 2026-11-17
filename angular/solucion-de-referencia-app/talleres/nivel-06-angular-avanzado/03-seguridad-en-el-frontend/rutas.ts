import { Routes } from '@angular/router';
import { Admin, Marco, XssPeligroso, XssSaneado, XssSeguro, soloAdmin } from './paginas';

export default [{
  path: '', component: Marco, children: [
    { path: 'xss-peligroso', component: XssPeligroso },
    { path: 'xss-saneado', component: XssSaneado },
    { path: 'xss-seguro', component: XssSeguro },
    { path: 'admin', component: Admin, canActivate: [soloAdmin] },
  ],
}] satisfies Routes;
