import { request, type FullConfig } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** Browser state shared by every test: the Vercel bypass cookie, when there is one. */
export const STORAGE_STATE = resolve(__dirname, '../playwright/.auth/vercel.json');

/**
 * Vercel Deployment Protection puts a login page in front of preview deployments.
 * With VERCEL_AUTOMATION_BYPASS_SECRET set (CI), one request to the deployment sends
 * the secret and asks Vercel for its bypass cookie; the tests then reuse that cookie.
 *
 * The secret goes only to the deployment's own origin. Playwright's extraHTTPHeaders
 * would attach it to every request the page makes, third-party hosts included.
 * Without the secret (local runs against the public production alias) the stored
 * state is empty.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use.baseURL;
  const secret = process.env['VERCEL_AUTOMATION_BYPASS_SECRET'];
  const ctx = await request.newContext({
    baseURL,
    extraHTTPHeaders: secret
      ? { 'x-vercel-protection-bypass': secret, 'x-vercel-set-bypass-cookie': 'true' }
      : {},
  });
  try {
    if (secret) {
      const res = await ctx.get('/');
      if (!res.ok()) {
        throw new Error(
          `Vercel bypass failed: ${res.status()} from ${baseURL} — check VERCEL_AUTOMATION_BYPASS_SECRET`,
        );
      }
    }
    mkdirSync(dirname(STORAGE_STATE), { recursive: true });
    await ctx.storageState({ path: STORAGE_STATE });
  } finally {
    await ctx.dispose();
  }
}
