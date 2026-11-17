import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProductsService } from '@org/shop/data';
import { Product } from '@org/models';
import {
  LoadingSpinnerComponent,
  ErrorMessageComponent,
} from '@org/shop/shared-ui';

@Component({
  selector: 'shop-product-detail',
  imports: [
    CommonModule,
    CurrencyPipe,
    RouterLink,
    LoadingSpinnerComponent,
    ErrorMessageComponent,
  ],
  template: `
    <div class="product-detail-container">
      @if (loading()) {
        <shop-loading-spinner />
      } @else if (error()) {
        <shop-error-message
          [title]="'Product Not Found'"
          [message]="error() || undefined"
          (retry)="loadProduct()"
        />
      } @else if (product()) {
        <div class="breadcrumb">
          <a routerLink="/products">← Back to Products</a>
        </div>

        <div class="product-detail">
          <div class="product-image-section">
            <img
              [src]="product()!.imageUrl"
              [alt]="product()!.name"
              class="product-image"
            />
            @if (!product()!.inStock) {
              <div class="out-of-stock-badge">Out of Stock</div>
            }
          </div>

          <div class="product-info-section">
            <div class="product-category">{{ product()!.category }}</div>
            <h1 class="product-name">{{ product()!.name }}</h1>

            <div class="product-rating">
              <span class="stars">
                @for (star of getStars(); track $index) {
                  <span [class.filled]="star">★</span>
                }
              </span>
              <span class="rating-text">
                {{ product()!.rating }} out of 5
              </span>
              <span class="review-count">
                ({{ product()!.reviewCount }} reviews)
              </span>
            </div>

            <div class="product-price">
              {{ product()!.price | currency }}
            </div>

            <div class="product-description">
              <h2>Description</h2>
              <p>{{ product()!.description }}</p>
            </div>

            <div class="product-actions">
              @if (product()!.inStock) {
                <button class="btn-primary" (click)="addToCart()">
                  Add to Cart
                </button>
                <button class="btn-secondary" (click)="addToWishlist()">
                  Add to Wishlist
                </button>
              } @else {
                <button class="btn-disabled" disabled>
                  Currently Unavailable
                </button>
              }
            </div>

            <div class="product-info">
              <h3>Product Information</h3>
              <dl>
                <dt>Product ID:</dt>
                <dd>{{ product()!.id }}</dd>
                <dt>Category:</dt>
                <dd>{{ product()!.category }}</dd>
                <dt>Availability:</dt>
                <dd>{{ product()!.inStock ? 'In Stock' : 'Out of Stock' }}</dd>
              </dl>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styleUrl: './product-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productsService = inject(ProductsService);

  // State signals
  readonly product = signal<Product | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit() {
    this.loadProduct();
  }

  loadProduct() {
    const productId = this.route.snapshot.paramMap.get('id');

    if (!productId) {
      this.error.set('Product ID not provided');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.productsService.getProductById(productId).subscribe({
      next: (product) => {
        if (product) {
          this.product.set(product);
        } else {
          this.error.set('Product not found');
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load product details');
        this.loading.set(false);
        console.error('Error loading product:', err);
      },
    });
  }

  getStars(): boolean[] {
    const product = this.product();
    if (!product) return [];

    const rating = product.rating;
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

  addToCart() {
    // This would typically call a cart service
    console.log('Adding to cart:', this.product()?.id);
    alert('Product added to cart!');
  }

  addToWishlist() {
    // This would typically call a wishlist service
    console.log('Adding to wishlist:', this.product()?.id);
    alert('Product added to wishlist!');
  }
}
