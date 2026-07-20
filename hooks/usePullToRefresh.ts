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
    const startXRef = useRef<number | null>(null);
    const startYRef = useRef<number | null>(null);
    const rejectedRef = useRef(false);

    useEffect(() => {
        if (disabled) return;

        const getScrollTop = () => {
            const rootScroller = document.getElementById('root');
            return Math.max(
                rootScroller?.scrollTop || 0,
                window.scrollY || 0,
                document.documentElement.scrollTop || 0,
                document.body.scrollTop || 0
            );
        };

        const hasBlockingOverlay = () => (
            document.body.classList.contains('lightbox-open')
            || !!document.querySelector('.modal-content,[role="dialog"],[class*="Modal"],[class*="modal"]')
        );

        const onPointerDown = (e: PointerEvent) => {
            if (getScrollTop() > 2) return;
            if (hasBlockingOverlay()) return;
            if (!e.isPrimary) return;
            startXRef.current = e.clientX;
            startYRef.current = e.clientY;
            rejectedRef.current = false;
        };

        const onPointerMove = (e: PointerEvent) => {
            if (startXRef.current == null || startYRef.current == null || rejectedRef.current) return;
            if (getScrollTop() > 2) {
                startXRef.current = null;
                startYRef.current = null;
                return;
            }
            if (hasBlockingOverlay()) {
                startXRef.current = null;
                startYRef.current = null;
                return;
            }
            const dx = Math.abs(e.clientX - startXRef.current);
            const dy = e.clientY - startYRef.current;
            const absDy = Math.abs(dy);
            if (absDy > 8 && dy < 0) {
                rejectedRef.current = true;
                startXRef.current = null;
                startYRef.current = null;
                return;
            }
            if (dx > 10 && dx > absDy * 0.9) {
                rejectedRef.current = true;
                startXRef.current = null;
                startYRef.current = null;
                return;
            }
            if (dy < 10 || dy <= dx * 1.18) return;
            if (dy >= threshold) {
                e.preventDefault();
                startXRef.current = null;
                startYRef.current = null;
                onRefresh();
            }
        };

        const onPointerEnd = () => {
            startXRef.current = null;
            startYRef.current = null;
            rejectedRef.current = false;
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
