import { describe, expect, it } from 'vitest';
import { buildForcedStoreUpdateViewModel } from './forcedStoreUpdateViewModel';

describe('buildForcedStoreUpdateViewModel', () => {
  it('prioritizes install as the primary action for ready states', () => {
    const viewModel = buildForcedStoreUpdateViewModel({
      currentVersion: '1.3.3',
      targetVersion: '1.3.4',
      status: 'ready_to_scan_or_install',
      progress: 100,
      statusText: '',
      errorText: '',
      exportPath: '',
      canInstall: true,
      canScan: true,
      isExported: false
    });

    expect(viewModel.actions).toEqual([
      { kind: 'secondary', intent: 'scan', label: 'Scan before install', icon: 'fa-shield-halved' },
      { kind: 'primary', intent: 'install', label: 'Install now', icon: 'fa-arrow-up-right-from-square' }
    ]);
  });

  it('switches to recovery actions after install failure', () => {
    const viewModel = buildForcedStoreUpdateViewModel({
      currentVersion: '1.3.3',
      targetVersion: '1.3.4',
      status: 'install_failed',
      progress: 100,
      statusText: 'Installer failed',
      errorText: 'Installer paused',
      exportPath: '',
      canInstall: true,
      canScan: false,
      isExported: false
    });

    expect(viewModel.actions).toEqual([
      { kind: 'outline', intent: 'export', label: 'Save APK to downloads', icon: 'fa-download' }
    ]);
    expect(viewModel.showSupportBlock).toBe(true);
  });

  it('exposes the exported path block only when export is ready', () => {
    const viewModel = buildForcedStoreUpdateViewModel({
      currentVersion: '1.3.3',
      targetVersion: '1.3.4',
      status: 'export_ready',
      progress: 100,
      statusText: '',
      errorText: '',
      exportPath: '/storage/emulated/0/Download/OrionStore_1.3.4.apk',
      canInstall: false,
      canScan: false,
      isExported: true
    });

    expect(viewModel.showExportPath).toBe(true);
    expect(viewModel.exportPathLabel).toBe('Saved path');
  });
});
