import {
  Component,
  input,
  output,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { Product } from '@org/models';

@Component({
  selector: 'shop-product-card',
  imports: [CommonModule, CurrencyPipe],
  template: `
    <div
      class="product-card"
      (click)="productClick.emit(product())"
      (keyup.enter)="productClick.emit(product())"
      (keyup.space)="productClick.emit(product())"
      [class.out-of-stock]="!product().inStock"
      tabindex="0"
      role="button"
      [attr.aria-label]="'View details for ' + product().name"
    >
      <div class="product-image">
        <img [src]="product().imageUrl" [alt]="product().name" />
        @if (!product().inStock) {
          <div class="out-of-stock-overlay">Out of Stock</div>
        }
      </div>
      <div class="product-info">
        <h3 class="product-name">{{ product().name }}</h3>
        <p class="product-category">{{ product().category }}</p>
        <div class="product-rating">
          <span class="stars">
            @for (star of getStars(); track $index) {
              <span [class.filled]="star">★</span>
            }
          </span>
          <span class="review-count">({{ product().reviewCount }})</span>
        </div>
        <div class="product-price">
          {{ product().price | currency }}
        </div>
      </div>
    </div>
  `,
  styleUrl: './product-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductCardComponent {
  readonly product = input.required<Product>();
  readonly productClick = output<Product>();

  getStars(): boolean[] {
    const rating = this.product().rating;
    const fullStars = Math.floor(rating);
    const hasHalfStar = rating % 1 >= 0.5;

    return Array(5)
      .fill(false)
      .map((_, index) => {
        if (index < fullStars) return true;
        if (index === fullStars && hasHalfStar) return true;
        return false;
      });
  }
}
