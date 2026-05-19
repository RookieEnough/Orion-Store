import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AppItem } from '../types';
import AppCard from './AppCard';
import { useSettingsStore } from '../store/useAppStore';

interface ClassicAppListProps {
    apps: AppItem[];
    onAppClick: (app: AppItem) => void;
}

const INITIAL_BATCH = 18;
const LOAD_MORE_BATCH = 12;

const ClassicAppList: React.FC<ClassicAppListProps> = ({ apps, onAppClick }) => {
    const compactMode = useSettingsStore((state) => state.compactMode);
    const [visibleCount, setVisibleCount] = useState(() => Math.min(apps.length, INITIAL_BATCH));
    const loadMoreRef = useRef<HTMLDivElement | null>(null);
    const visibleApps = useMemo(() => apps.slice(0, visibleCount), [apps, visibleCount]);
    const hasMore = visibleCount < apps.length;
    const gridGapClass = compactMode ? 'gap-2 sm:gap-2.5' : 'gap-4';

    useEffect(() => {
        setVisibleCount(Math.min(apps.length, INITIAL_BATCH));
    }, [apps]);

    useEffect(() => {
        if (!hasMore || !loadMoreRef.current) return;

        const observer = new IntersectionObserver(
            ([entry]) => {
                if (!entry?.isIntersecting) return;
                setVisibleCount((current) => Math.min(apps.length, current + LOAD_MORE_BATCH));
            },
            { rootMargin: '320px 0px' }
        );

        observer.observe(loadMoreRef.current);
        return () => observer.disconnect();
    }, [apps.length, hasMore]);

    return (
        <>
            <div className={`grid grid-cols-1 animate-fade-in md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 ${gridGapClass}`}>
                {visibleApps.map((app, index) => (
                    <AppCard
                        key={app.id}
                        app={app}
                        priority={index < 10}
                        onClick={onAppClick}
                    />
                ))}
            </div>
            {hasMore && <div ref={loadMoreRef} className="h-8 w-full" aria-hidden="true" />}
        </>
    );
};

export default React.memo(ClassicAppList);
