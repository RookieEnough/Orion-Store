
import React, { useEffect, useState } from 'react';
import { useScrollLock } from '../hooks/useScrollLock';
import { RELEASE_NOTES_URL } from '../constants';

interface ReleaseNotesModalProps {
  onClose: () => void;
}

interface NoteItem {
    label: string;
    text: string;
}

interface UpdateLog {
    version: string;
    date: string;
    title: string;
    color?: string;
    items?: NoteItem[];
    changes?: string[]; // Simplified remote format support
}

const DEFAULT_NOTES: UpdateLog[] = [
    {
        version: 'v1.2.3',
        date: 'Current',
        title: 'The "Ignore" Update',
        color: 'text-primary',
        items: [
            { label: 'NEW', text: 'Added option to ignore app updates for 1 week, until next version, or never.' },
            { label: 'NEW', text: 'Added option to close Developer Options after unlocking.' },
            { label: 'IMP', text: 'Bumped store version to v1.2.3.' }
        ]
    },
    {
        version: 'v1.2.2',
        date: 'Feb 2026',
        title: 'The Patch Notes Update',
        color: 'text-theme-sub',
        items: [
            { label: 'NEW', text: 'Release Notes Viewer: You can now check what\'s new directly from the Dev tab.' },
            { label: 'FIX', text: 'Optimized internal memory usage for smoother scrolling.' },
            { label: 'TWEAK', text: 'Improved touch target size for navigation buttons.' }
        ]
    },
    {
        version: 'v1.2.1',
        date: 'Previous',
        title: 'Stability Patch',
        color: 'text-theme-sub',
        items: [
            { label: 'FIX', text: 'Resolved duplicate download entries in queue.' },
            { label: 'FIX', text: 'Fixed crash when resuming app from background.' },
            { label: 'IMP', text: 'Added "Retry" button for failed downloads.' }
        ]
    }
];

const ReleaseNotesModal: React.FC<ReleaseNotesModalProps> = ({ onClose }) => {
  useScrollLock(true);
  const [updates, setUpdates] = useState<UpdateLog[]>(DEFAULT_NOTES);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
      fetchNotes();
  }, []);

  const fetchNotes = async () => {
      try {
          // 1. Try Cache First
          const cached = localStorage.getItem('orion_release_notes_cache');
          if (cached) {
              setUpdates(JSON.parse(cached));
              setLoading(false); // Show cached content immediately
          }

          // 2. Fetch Remote
          const res = await fetch(`${RELEASE_NOTES_URL}?t=${Date.now()}`);
          if (res.ok) {
              const data = await res.json();
              
              // 3. Process & Normalize Data (Support simplified "changes": ["NEW: Text"] format)
              const processed: UpdateLog[] = data.map((entry: any) => {
                  let items: NoteItem[] = entry.items || [];
                  
                  // Convert simple string array to structured items if 'items' is missing
                  if (!items.length && entry.changes && Array.isArray(entry.changes)) {
                      items = entry.changes.map((line: string) => {
                          let label = 'NOTE';
                          let text = line;
                          
                          if (line.includes(':')) {
                              const parts = line.split(':');
                              const firstPart = parts[0];
                              if (firstPart) {
                                  const possibleLabel = firstPart.trim().toUpperCase();
                                  // Check for known prefixes to strip them
                                  if (['NEW', 'FIX', 'IMP', 'TWEAK', 'HOT', 'SEC'].includes(possibleLabel)) {
                                      label = possibleLabel;
                                      text = parts.slice(1).join(':').trim();
                                  }
                              }
                          }
                          return { label, text };
                      });
                  }

                  return {
                      version: entry.version || 'Unknown',
                      date: entry.date || '',
                      title: entry.title || 'Update',
                      color: entry.color || (entry.version === data[0].version ? 'text-primary' : 'text-theme-sub'),
                      items
                  };
              });

              setUpdates(processed);
              localStorage.setItem('orion_release_notes_cache', JSON.stringify(processed));
          }
      } catch (e) {
          console.warn("Failed to fetch remote notes, using fallback/cache.");
          // Fallback to default if cache is empty is already handled by initial state
      } finally {
          setLoading(false);
      }
  };

  const getLabelStyle = (label: string) => {
      switch (label) {
          case 'NEW': return 'text-green-500 border-green-500/30 bg-green-500/10';
          case 'FIX': return 'text-orange-500 border-orange-500/30 bg-orange-500/10';
          case 'HOT': return 'text-red-500 border-red-500/30 bg-red-500/10';
          case 'SEC': return 'text-purple-500 border-purple-500/30 bg-purple-500/10';
          case 'IMP': 
          case 'TWEAK': return 'text-blue-500 border-blue-500/30 bg-blue-500/10';
          default: return 'text-theme-sub border-theme-border bg-theme-element';
      }
  };

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-6 animate-fade-in touch-none">
      <div className="absolute inset-0 bg-black/80 backdrop-blur-md" onClick={onClose}></div>
      
      <div className="relative w-full max-w-sm bg-surface border border-theme-border rounded-[2.5rem] shadow-2xl overflow-hidden animate-slide-up flex flex-col max-h-[80vh]">
        
        {/* Header */}
        <div className="relative p-8 pb-4 text-center z-10 shrink-0">
            <div className="absolute top-0 left-0 right-0 h-32 bg-gradient-to-b from-primary/10 to-transparent -z-10"></div>
            <div className="w-16 h-16 bg-primary text-white rounded-3xl flex items-center justify-center text-3xl mx-auto mb-4 shadow-xl shadow-primary/30 transform -rotate-6">
                <i className="fas fa-clipboard-list"></i>
            </div>
            <h2 className="text-2xl font-black text-theme-text tracking-tight">Patch Notes</h2>
            <p className="text-xs text-theme-sub font-bold uppercase tracking-widest mt-1">What's Changing</p>
        </div>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-y-auto p-6 pt-2 space-y-8 no-scrollbar overscroll-contain">
            {loading ? (
                <div className="space-y-6 animate-pulse">
                    {[1, 2].map(i => (
                        <div key={i} className="pl-4 border-l-2 border-theme-border/50">
                            <div className="h-4 w-20 bg-theme-element rounded mb-2"></div>
                            <div className="h-3 w-32 bg-theme-element rounded mb-4"></div>
                            <div className="space-y-2">
                                <div className="h-3 w-full bg-theme-element rounded"></div>
                                <div className="h-3 w-3/4 bg-theme-element rounded"></div>
                            </div>
                        </div>
                    ))}
                </div>
            ) : (
                updates.map((update, idx) => (
                    <div key={idx} className="relative pl-4 border-l-2 border-theme-border/50">
                        <div className={`absolute -left-[5px] top-0 w-2.5 h-2.5 rounded-full ${idx === 0 ? 'bg-primary shadow-[0_0_10px_rgba(99,102,241,0.5)]' : 'bg-theme-border'}`}></div>
                        
                        <div className="mb-3">
                            <span className={`text-xs font-black ${update.color} bg-theme-element px-2 py-0.5 rounded-md`}>{update.version}</span>
                            <span className="text-[10px] text-theme-sub font-bold ml-2 uppercase tracking-wide opacity-70">{update.date}</span>
                        </div>
                        
                        <h3 className="text-sm font-bold text-theme-text mb-3">{update.title}</h3>
                        
                        <div className="space-y-3">
                            {update.items?.map((item, i) => (
                                <div key={i} className="flex gap-3 items-start">
                                    <span className={`text-[9px] font-black uppercase px-1.5 py-0.5 rounded border leading-none mt-0.5 shrink-0 ${getLabelStyle(item.label)}`}>
                                        {item.label}
                                    </span>
                                    <p className="text-xs text-theme-sub font-medium leading-relaxed">
                                        {item.text}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </div>
                ))
            )}
            
            <div className="text-center pt-4 opacity-50">
                <i className="fas fa-ellipsis-v text-theme-sub"></i>
            </div>
        </div>

        {/* Footer */}
        <div className="p-6 pt-4 shrink-0 z-20 bg-surface/95 backdrop-blur-xl border-t border-theme-border/50">
            <button 
                onClick={onClose}
                className="w-full py-4 rounded-2xl bg-theme-text text-surface font-bold text-sm shadow-lg hover:scale-[1.02] active:scale-95 transition-all"
            >
                Close
            </button>
        </div>

      </div>
    </div>
  );
};

export default ReleaseNotesModal;
