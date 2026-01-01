import { useState, useCallback, useRef } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '@/utils/storage';

export function useDevMode() {
  const [isUnlocked, setIsUnlocked] = useLocalStorage(STORAGE_KEYS.DEV_UNLOCKED, false);
  const [tapCount, setTapCount] = useState(0);
  const [toastMessage, setToastMessage] = useState('');
  const [showToast, setShowToast] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const showMsg = useCallback((msg: string) => {
    setToastMessage(msg);
    setShowToast(true);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setShowToast(false), 2000);
  }, []);

  const handleTap = useCallback(() => {
    if (isUnlocked) return showMsg('You are already a developer.');
    
    const count = tapCount + 1;
    setTapCount(count);
    const left = 9 - count;
    
    if (left <= 0) {
      setIsUnlocked(true);
      showMsg('You are now a developer!');
    } else if (left <= 5) {
      showMsg(`${left} steps away from being a developer.`);
    }
  }, [isUnlocked, tapCount, setIsUnlocked, showMsg]);

  return { isUnlocked, handleTap, toastMessage, showToast };
}
