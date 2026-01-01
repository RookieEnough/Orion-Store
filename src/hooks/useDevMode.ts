import { useState, useCallback, useRef } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '@/utils/storage';

const TAPS_REQUIRED = 9;
const TOAST_DURATION = 2000;

export function useDevMode() {
  const [isUnlocked, setIsUnlocked] = useLocalStorage(STORAGE_KEYS.DEV_UNLOCKED, false);
  const [tapCount, setTapCount] = useState(0);
  const [toastMessage, setToastMessage] = useState('');
  const [showToast, setShowToast] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const showDevToast = useCallback((msg: string) => {
    setToastMessage(msg);
    setShowToast(true);
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setShowToast(false), TOAST_DURATION);
  }, []);

  const handleTap = useCallback(() => {
    if (isUnlocked) {
      showDevToast('You are already a developer.');
      return;
    }

    const newCount = tapCount + 1;
    setTapCount(newCount);
    const remaining = TAPS_REQUIRED - newCount;

    if (remaining <= 0) {
      setIsUnlocked(true);
      showDevToast('You are now a developer!');
    } else if (remaining <= 5) {
      showDevToast(`You are ${remaining} steps away from being a developer.`);
    }
  }, [isUnlocked, tapCount, setIsUnlocked, showDevToast]);

  return {
    isUnlocked,
    handleTap,
    toastMessage,
    showToast,
  };
}
