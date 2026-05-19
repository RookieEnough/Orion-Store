
import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.orion.store',
  appName: 'Orion Store',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  },
  android: {
    allowMixedContent: true,
    backgroundColor: '#00000000'
  },
  plugins: {
    StatusBar: {
      style: 'LIGHT',
      backgroundColor: '#00000000'
    }
  }
};

export default config;
