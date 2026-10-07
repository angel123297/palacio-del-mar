import { test, expect } from '@playwright/test';

test('homepage loads and displays Palacio del Mar content', async ({ page }) => {
  await page.goto('/');
  
  // Verify that the page loads correctly and title or brand header is present
  await expect(page).toHaveTitle(/Palacio del Mar/i);
  
  // Check if main booking bar or navigation is visible
  const bookingBar = page.locator('text=Llegada');
  await expect(bookingBar).toBeVisible({ timeout: 10000 });
});
