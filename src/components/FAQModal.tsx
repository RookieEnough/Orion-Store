import { memo } from 'react';
import type { FAQItem } from '@/types';

interface FAQModalProps {
  items: FAQItem[];
  onClose: () => void;
}

export const FAQModal = memo(function FAQModal({ items, onClose }: FAQModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in">
      <div className="absolute inset-0 bg-black/70 backdrop-blur-md" onClick={onClose} />

      <div className="relative bg-surface border border-theme-border rounded-3xl w-full max-w-lg max-h-[80vh] overflow-hidden animate-slide-up">
        {/* Header */}
        <div className="sticky top-0 bg-surface/90 backdrop-blur-xl z-10 p-4 flex justify-between items-center border-b border-theme-border">
          <h2 className="text-xl font-black text-theme-text">FAQs</h2>
          <button
            onClick={onClose}
            className="w-10 h-10 rounded-full bg-theme-element flex items-center justify-center text-theme-sub hover:text-theme-text transition-colors"
          >
            <i className="fas fa-times" />
          </button>
        </div>

        {/* Content */}
        <div className="p-4 overflow-y-auto max-h-[calc(80vh-80px)] no-scrollbar space-y-4">
          {items.map((item, index) => (
            <div
              key={index}
              className="bg-card border border-theme-border rounded-2xl p-4 hover:border-primary/30 transition-colors"
            >
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center shrink-0">
                  <i className={`fas ${item.icon} text-primary`} />
                </div>
                <div>
                  <h3 className="font-bold text-theme-text mb-2">{item.question}</h3>
                  <p className="text-theme-sub text-sm leading-relaxed whitespace-pre-line">
                    {item.answer}
                  </p>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
});
