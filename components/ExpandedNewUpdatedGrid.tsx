import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { AppItem } from '../types';
import CompactAppCard from './CompactAppCard';
import { useSettingsStore } from '../store/useAppStore';

const useMotionEnabled = () => {
    const disableAnimations = useSettingsStore((state) => state.disableAnimations);
    const prefersReducedMotion = useReducedMotion();
    return !disableAnimations && !prefersReducedMotion;
};

interface ExpandedNewUpdatedGridProps {
    title: string;
    apps: AppItem[];
    onAppClick: (app: AppItem) => void;
    onViewMore: () => void;
}

const ExpandedNewUpdatedGrid: React.FC<ExpandedNewUpdatedGridProps> = ({
    title,
    apps,
    onAppClick,
    onViewMore
}) => {
    const motionEnabled = useMotionEnabled();

    if (apps.length === 0) return null;

    return (
        <section
            className="relative mb-8 flex flex-col gap-4"
            style={{ contentVisibility: 'auto', containIntrinsicSize: '0 520px' }}
        >
            <div className="px-4">
                <div className="flex items-center justify-between">
                    <h2 className="text-lg font-black leading-none tracking-tight text-theme-text">
                        {title}
                    </h2>
                    <span className="text-[10px] font-bold text-theme-sub">{apps.length} apps</span>
                </div>
            </div>

            <div className="px-4">
                <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-2.5 sm:gap-3">
                    {apps.map((app, i) => (
                        <motion.div
                            key={app.id}
                            initial={motionEnabled ? { opacity: 0, y: 14 } : undefined}
                            whileInView={motionEnabled ? { opacity: 1, y: 0 } : undefined}
                            viewport={{ once: true }}
                            transition={motionEnabled ? { delay: i * 0.03 } : undefined}
                            whileTap={motionEnabled ? { scale: 0.97 } : undefined}
                            className="flex justify-center"
                        >
                            <div className="w-full min-w-0">
                                <CompactAppCard
                                    app={app}
                                    index={i}
                                    priority={i < 9}
                                    className="w-full"
                                    motionEnabled={motionEnabled}
                                    onClick={() => onAppClick(app)}
                                />
                            </div>
                        </motion.div>
                    ))}
                </div>

                <motion.button
                    whileTap={{ scale: 0.97 }}
                    onClick={onViewMore}
                    className="mt-4 flex w-full items-center justify-center gap-2 rounded-2xl bg-theme-element py-3 text-sm font-bold text-theme-text transition-all hover:bg-theme-hover"
                >
                    <span>View more apps</span>
                    <i className="fas fa-arrow-right text-[10px]"></i>
                </motion.button>
            </div>
        </section>
    );
};

export default React.memo(ExpandedNewUpdatedGrid);
