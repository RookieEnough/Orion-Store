import { useMemo } from 'react';
import { shallow } from 'zustand/shallow';
import { AppItem } from '../types';
import { useSettingsStore } from '../store/useAppStore';
import { getPreferredVersion, hasAvailableUpdate } from '../utils/appVersioning';

const ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;

export const useAvailableUpdates = (apps: AppItem[]) => {
  const { installedVersions, appStreams, ignoredUpdates } = useSettingsStore((state) => ({
    installedVersions: state.installedVersions,
    appStreams: state.appStreams,
    ignoredUpdates: state.ignoredUpdates
  }), shallow);

  return useMemo(() => {
    return apps.filter((app) => {
      const localVer = installedVersions[app.id];
      if (!localVer) return false;

      const preferredStream = appStreams[app.id] || 'Stable';
      const targetVersion = getPreferredVersion(app, preferredStream);
      const isUpdate = hasAvailableUpdate(app, localVer, preferredStream);
      if (!isUpdate) return false;

      const ignored = ignoredUpdates[app.id];
      if (ignored) {
        if (ignored.type === 'never') return false;
        if (ignored.type === 'week' && ignored.timestamp && Date.now() - ignored.timestamp < ONE_WEEK_MS) return false;
        if (ignored.type === 'version' && ignored.version === targetVersion) return false;
      }

      return true;
    });
  }, [apps, installedVersions, appStreams, ignoredUpdates]);
};

export default useAvailableUpdates;
