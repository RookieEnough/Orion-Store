import { useState, useCallback } from 'react';
import { storage } from '@/utils/storage';

export function useLocalStorage<T>(
  key: string,
  initialValue: T
): [T, (value: T | ((prev: T) => T)) => void] {
  const [storedValue, setStoredValue] = useState<T>(() => {
    const item = storage.get(key);
    if (item === null) return initialValue;
    try {
      return JSON.parse(item) as T;
    } catch {
      return item as unknown as T;
    }
  });

  const setValue = useCallback(
    (value: T | ((prev: T) => T)) => {
      setStoredValue((prev) => {
        const newValue = value instanceof Function ? value(prev) : value;
        storage.set(key, JSON.stringify(newValue));
        return newValue;
      });
    },
    [key]
  );

  return [storedValue, setValue];
}
