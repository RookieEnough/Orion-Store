
import React, { useState } from 'react';
import { FAQItem } from '../types';

interface FAQModalProps {
  onClose: () => void;
  items: FAQItem[];
}

const FAQModal: React.FC<FAQModalProps> = ({ onClose, items }) => {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  const toggleFAQ = (index: number) => {
    setActiveIndex(activeIndex === index ? null : index);
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col animate-slide-up">
      {/* Backdrop */}
      <div 
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        onClick={onClose}
      ></div>

      {/* Modal Content */}
      <div className="relative flex-1 mt-12 bg-surface rounded-t-[2.5rem] shadow-2xl overflow-hidden flex flex-col border-t border-theme-border">
        
        {/* Header */}
        <div className="px-8 py-6 bg-surface/90 backdrop-blur-xl sticky top-0 z-10 flex justify-between items-center border-b border-theme-border">
           <div>
              <h2 className="text-2xl font-black text-theme-text tracking-tight">
                FAQ<span className="text-primary">s</span>
              </h2>
              <p className="text-xs text-theme-sub font-medium">Common questions & secrets</p>
           </div>
           <button 
             onClick={onClose}
             className="w-10 h-10 rounded-full bg-theme-element border border-theme-border flex items-center justify-center hover:bg-theme-hover transition-colors text-theme-text"
           >
             <i className="fas fa-times"></i>
           </button>
        </div>

        {/* Scrollable List */}
        <div className="flex-1 overflow-y-auto p-6 pb-32 no-scrollbar">
          <div className="space-y-3">
            {items.map((faq, idx) => (
              <div 
                key={idx}
                onClick={() => toggleFAQ(idx)}
                className={`
                  group rounded-2xl transition-all duration-300 border cursor-pointer overflow-hidden
                  ${activeIndex === idx 
                    ? 'bg-primary/5 border-primary/30 shadow-lg shadow-primary/10' 
                    : 'bg-card border-theme-border hover:border-primary/50'}
                `}
              >
                {/* Question Header */}
                <div className="p-5 flex items-center gap-4">
                   <div className={`
                      w-10 h-10 rounded-full flex items-center justify-center text-lg transition-colors duration-300
                      ${activeIndex === idx ? 'bg-primary text-white' : 'bg-theme-element text-theme-sub group-hover:text-primary'}
                   `}>
                      <i className={`fas ${faq.icon}`}></i>
                   </div>
                   <h3 className={`flex-1 font-bold text-lg transition-colors ${activeIndex === idx ? 'text-primary' : 'text-theme-text'}`}>
                     {faq.question}
                   </h3>
                   <i className={`fas fa-chevron-down transition-transform duration-300 text-theme-sub ${activeIndex === idx ? 'rotate-180 text-primary' : ''}`}></i>
                </div>

                {/* Answer Body */}
                <div 
                  className={`
                    overflow-hidden transition-all duration-300 ease-in-out
                    ${activeIndex === idx ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'}
                  `}
                >
                  <div className="px-5 pb-5 pt-0 pl-[4.5rem]">
                    <p className="text-theme-sub leading-relaxed text-sm font-medium whitespace-pre-line">
                      {faq.answer}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Footer Decor */}
          <div className="mt-8 text-center">
             <i className="fas fa-infinity text-2xl text-theme-element animate-pulse"></i>
          </div>
        </div>
      </div>
    </div>
  );
};

export default FAQModal;
