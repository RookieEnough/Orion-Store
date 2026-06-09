import { useEffect, useRef } from 'react';

interface UsePullToRefreshOptions {
    /** Called when the user pulls down past the threshold. */
    onRefresh: () => void;
    /** If true, the hook ignores further pull gestures until reset. */
    disabled?: boolean;
    /** Pull distance in pixels required to trigger refresh. Default 60. */
    threshold?: number;
}

/**
 * Tiny pull-to-refresh hook that works with window scrolling.
 *
 * Fires `onRefresh` when the user drags downward from the top of the
 * page (window.scrollY === 0) past `threshold` pixels.
 */
export function usePullToRefresh({
    onRefresh,
    disabled = false,
    threshold = 60
}: UsePullToRefreshOptions): void {
    const startYRef = useRef<number | null>(null);

    useEffect(() => {
        if (disabled) return;

        const onPointerDown = (e: PointerEvent) => {
            const rootScroller = document.getElementById('root');
            const scrollTop = rootScroller ? rootScroller.scrollTop : window.scrollY;
            if (scrollTop > 0) return;
            if (document.body.classList.contains('lightbox-open') || document.querySelector('.modal-content')) return;
            if (!e.isPrimary) return;
            startYRef.current = e.clientY;
        };

        const onPointerMove = (e: PointerEvent) => {
            if (startYRef.current == null) return;
            const rootScroller = document.getElementById('root');
            const scrollTop = rootScroller ? rootScroller.scrollTop : window.scrollY;
            if (scrollTop > 0) {
                startYRef.current = null;
                return;
            }
            if (document.body.classList.contains('lightbox-open') || document.querySelector('.modal-content')) {
                startYRef.current = null;
                return;
            }
            const dy = e.clientY - startYRef.current;
            if (dy <= 0) {
                startYRef.current = null;
                return;
            }
            if (dy >= threshold) {
                e.preventDefault();
                startYRef.current = null;
                onRefresh();
            }
        };

        const onPointerEnd = () => {
            startYRef.current = null;
        };

        window.addEventListener('pointerdown', onPointerDown, { passive: true });
        window.addEventListener('pointermove', onPointerMove, { passive: false });
        window.addEventListener('pointerup', onPointerEnd);
        window.addEventListener('pointercancel', onPointerEnd);

        return () => {
            window.removeEventListener('pointerdown', onPointerDown);
            window.removeEventListener('pointermove', onPointerMove);
            window.removeEventListener('pointerup', onPointerEnd);
            window.removeEventListener('pointercancel', onPointerEnd);
        };
    }, [disabled, threshold, onRefresh]);
}