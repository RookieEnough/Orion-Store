import { useState, useCallback } from 'react';
import { storage } from '@/utils/storage';

export function useLocalStorage<T>(key: string, initial: T): [T, (v: T | ((p: T) => T)) => void] {
  const [value, setValue] = useState<T>(() => {
    const item = storage.get(key);
    if (item === null) return initial;
    try { return JSON.parse(item); } catch { return item as T; }
  });

  const set = useCallback((v: T | ((p: T) => T)) => {
    setValue(prev => {
      const next = v instanceof Function ? v(prev) : v;
      storage.set(key, JSON.stringify(next));
      return next;
    });
  }, [key]);

  return [value, set];
}
