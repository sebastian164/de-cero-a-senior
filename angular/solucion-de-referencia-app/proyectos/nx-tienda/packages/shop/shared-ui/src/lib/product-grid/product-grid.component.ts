import {
  Component,
  input,
  output,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Product } from '@org/models';
import { ProductCardComponent } from '../product-card/product-card.component';

@Component({
  selector: 'shop-product-grid',
  imports: [CommonModule, ProductCardComponent],
  template: `
    <div class="product-grid">
      @for (product of products(); track product.id) {
        <shop-product-card
          [product]="product"
          (productClick)="productSelect.emit($event)"
        />
      } @empty {
        <div class="no-products">
          <p>No products found</p>
        </div>
      }
    </div>
  `,
  styleUrl: './product-grid.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductGridComponent {
  readonly products = input.required<Product[]>();
  readonly productSelect = output<Product>();
}
