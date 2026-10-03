import { describe, expect, it } from 'vitest';
import { AppItem, Platform } from '../types';
import { buildAppUpdateIssueUrl, buildAppUpdatePayload } from './appChangeRequest';

const makeApp = (platform: Platform): AppItem => ({
  id: 'example-app',
  name: 'Example App',
  description: 'Old description',
  icon: 'https://example.com/icon.png',
  version: '1.0.0',
  latestVersion: '1.0.0',
  downloadUrl: 'https://example.com/app.apk',
  repoUrl: 'https://github.com/example/app',
  category: 'Utility',
  platform,
  size: '1 MB',
  author: 'Example',
  screenshots: []
});

describe('App Update issue builder', () => {
  it('produces the title and JSON payload expected by app_update.yml', () => {
    const app = makeApp(Platform.ANDROID);
    const payload = buildAppUpdatePayload(app, 'packageName', 'com.example.corrected');
    const url = buildAppUpdateIssueUrl({
      app,
      fieldLabel: 'Android package name',
      currentValue: 'com.example.old',
      suggestedValue: 'com.example.corrected',
      reason: 'Package was renamed.',
      referenceLink: 'https://example.com/release',
      payload
    });

    const decoded = decodeURIComponent(url);
    expect(decoded).toContain('title=App Update - Example App (Android)');
    expect(decoded).toContain('### Automation payload');
    expect(decoded).toContain('"id": "example-app"');
    expect(decoded).toContain('"platform": "Android"');
    expect(decoded).toContain('"packageName": "com.example.corrected"');
  });

  it('keeps PC and TV updates scoped to the existing platform', () => {
    const pcApp = makeApp(Platform.PC);
    const tvApp = makeApp(Platform.TV);

    expect(buildAppUpdatePayload(pcApp, 'downloadUrl', 'https://example.com/pc.exe').platform).toBe(Platform.PC);
    expect(buildAppUpdatePayload(tvApp, 'description', 'Updated TV description').platform).toBe(Platform.TV);
  });
});
