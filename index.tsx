import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';
import { installI18nObserver } from './i18n';

installI18nObserver();

const container = document.getElementById('root');
if (container) {
  const root = createRoot(container);
  root.render(
    <App />
  );
} else {
  console.error("Root element not found");
}
