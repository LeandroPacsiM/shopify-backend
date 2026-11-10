import { expect, test } from '@playwright/test';

// Smoke sin backend: solo render y navegación (no muta nada).
test('raíz redirige a /login con el form staff', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByLabel(/correo electrónico/i)).toBeVisible();
  await expect(page.getByLabel(/contraseña/i)).toBeVisible();
});

test('registro → email: el registro pide datos y enlaza a login', async ({ page }) => {
  await page.goto('/registro');
  await expect(page.getByPlaceholder(/tucorreo@ejemplo.com/i)).toBeVisible();
  await expect(page.getByRole('button', { name: /crear cuenta/i })).toBeVisible();
});
