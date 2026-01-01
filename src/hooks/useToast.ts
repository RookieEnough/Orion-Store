import { useState, useCallback, useRef } from 'react';

type ToastType = 'success' | 'error' | 'info' | 'update' | 'theme';

interface Toast {
  id: string;
  message: string;
  type: ToastType;
}

export function useToast(duration = 3000) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const show = useCallback(
    (message: string, type: ToastType = 'info') => {
      const id = crypto.randomUUID();
      setToasts((prev) => [...prev, { id, message, type }]);

      const timer = setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
        timersRef.current.delete(id);
      }, duration);

      timersRef.current.set(id, timer);
      return id;
    },
    [duration]
  );

  const dismiss = useCallback((id: string) => {
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const success = useCallback((msg: string) => show(msg, 'success'), [show]);
  const error = useCallback((msg: string) => show(msg, 'error'), [show]);
  const info = useCallback((msg: string) => show(msg, 'info'), [show]);

  return { toasts, show, dismiss, success, error, info };
}
