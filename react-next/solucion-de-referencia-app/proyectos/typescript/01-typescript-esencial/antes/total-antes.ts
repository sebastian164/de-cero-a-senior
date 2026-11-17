function total(items: any, cupon?: any) {
  let t = 0;
  for (const i of items) t += i.precio * i.cant;      // 'cant' o 'cantidad': nadie avisa
  if (cupon.tipo == 'porcentaje') t -= t * cupon.valor / 100;   // falla si no hay cupón
  return t;
}

export { total };
