import { Injectable, signal, computed } from '@angular/core';

export type Currency = 'COP' | 'USD' | 'EUR';

@Injectable({
  providedIn: 'root'
})
export class CurrencyService {
  // Tasas de cambio de referencia para visualización internacional
  private readonly rates: Record<Currency, number> = {
    COP: 1,
    USD: 0.00025, // Aprox. 1 USD = 4,000 COP
    EUR: 0.00023  // Aprox. 1 EUR = 4,350 COP
  };

  private readonly symbols: Record<Currency, string> = {
    COP: '$',
    USD: 'US$',
    EUR: '€'
  };

  public currentCurrency = signal<Currency>('COP');

  public setCurrency(currency: Currency): void {
    this.currentCurrency.set(currency);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('selected_currency', currency);
    }
  }

  constructor() {
    if (typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem('selected_currency') as Currency;
      if (saved && ['COP', 'USD', 'EUR'].includes(saved)) {
        this.currentCurrency.set(saved);
      }
    }
  }

  public formatPrice(amountCop: number): string {
    const cur = this.currentCurrency();
    const rate = this.rates[cur];
    const symbol = this.symbols[cur];

    if (cur === 'COP') {
      return `${symbol} ${Math.round(amountCop).toLocaleString('es-CO')} COP`;
    }

    const converted = amountCop * rate;
    return `${symbol} ${converted.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })} ${cur}`;
  }
}
