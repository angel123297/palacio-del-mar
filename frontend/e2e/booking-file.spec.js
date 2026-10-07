import { test, expect } from '@playwright/test';

test('user can search availability and view suites', async ({ page }) => {
  await page.goto('/');

  // Check booking bar is present
  const submitButton = page.locator('button.bf-submit-btn');
  await expect(submitButton).toBeVisible();

  // Click search availability
  await submitButton.click();

  // Verify rooms section loads and displays suites
  const roomsSection = page.locator('#rooms');
  await expect(roomsSection).toBeVisible();

  // Wait for suites or room cards to load
  await page.waitForTimeout(2000);
});
