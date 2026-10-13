import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { LoyaltyService } from './loyalty.service';

describe('LoyaltyService', () => {
  let service: LoyaltyService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LoyaltyService]
    });
    service = TestBed.inject(LoyaltyService);
  });

  it('should be created and return initial tier and points', () => {
    expect(service).toBeTruthy();
    const status = service.status();
    expect(status.tier).toBe('Gold');
    expect(status.points).toBe(12500);
    expect(status.nextTierPoints).toBe(20000);
  });

  it('should compute progress percentage correctly', () => {
    // 12500 / 20000 = 62.5% -> rounded to 63%
    expect(service.progressPercentage()).toBe(63);

    service.updateLoyalty({ points: 20000, nextTierPoints: 20000 });
    expect(service.progressPercentage()).toBe(100);

    service.updateLoyalty({ points: 25000, nextTierPoints: 20000 });
    expect(service.progressPercentage()).toBe(100);
  });

  it('should allow partial updates to loyalty status', () => {
    service.updateLoyalty({ tier: 'Platinum', points: 35000, nextTierPoints: 50000, bookingsCount: 10 });
    expect(service.status().tier).toBe('Platinum');
    expect(service.status().bookingsCount).toBe(10);
    expect(service.progressPercentage()).toBe(70);
  });
});
