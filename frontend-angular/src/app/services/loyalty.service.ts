import { Injectable, signal, computed } from '@angular/core';

export type LoyaltyTier = 'Bronze' | 'Silver' | 'Gold' | 'Platinum' | 'Palace';

export interface LoyaltyStatus {
  tier: LoyaltyTier;
  points: number;
  nextTierPoints: number;
  bookingsCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class LoyaltyService {
  // Mock data for the loyalty status
  private statusSignal = signal<LoyaltyStatus>({
    tier: 'Gold',
    points: 12500,
    nextTierPoints: 20000,
    bookingsCount: 4
  });

  readonly status = this.statusSignal.asReadonly();

  readonly progressPercentage = computed(() => {
    const s = this.statusSignal();
    if (s.points >= s.nextTierPoints) return 100;
    return Math.round((s.points / s.nextTierPoints) * 100);
  });

  // Example of methods to update loyalty (could be tied to real API)
  updateLoyalty(newStatus: Partial<LoyaltyStatus>) {
    this.statusSignal.update(s => ({ ...s, ...newStatus }));
  }
}
