
import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.orion.store',
  appName: 'Orion Store',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  }
};

export default config;
