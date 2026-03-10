
import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useSettingsStore } from '../store/useAppStore';
import { generateSignature } from '../utils/crypto';
import { useScrollLock } from '../hooks/useScrollLock';

interface LeaderboardSubmitModalProps {
  onClose: () => void;
  workerUrl: string;
}

// 10 Fun Characters (Robots, Pixel Art, Big Smile, Big Ears)
const CHARACTERS = [
    { id: 'ears1', name: 'Imp', url: 'https://api.dicebear.com/9.x/big-ears/svg?seed=Avery' },
    { id: 'pixel1', name: '8-Bit', url: 'https://api.dicebear.com/9.x/pixel-art/svg?seed=Avery' },
    { id: 'mecha', name: 'Mecha', url: 'https://api.dicebear.com/9.x/bottts/svg?seed=Mecha&backgroundColor=c0aede' },
    { id: 'pixel3', name: 'Voxel', url: 'https://api.dicebear.com/9.x/pixel-art/svg?seed=Kimberly' },
    { id: 'pixel2', name: 'Retro', url: 'https://api.dicebear.com/9.x/pixel-art/svg?seed=Wyatt' },
    { id: 'ears2', name: 'Pixie', url: 'https://api.dicebear.com/9.x/big-ears/svg?seed=Sara' },
    { id: 'smile1', name: 'Cheery', url: 'https://api.dicebear.com/9.x/big-smile/svg?seed=Jessica' },
    { id: 'unit', name: 'Unit 01', url: 'https://api.dicebear.com/9.x/bottts/svg?seed=Unit01&backgroundColor=c0aede' },
    { id: 'smile2', name: 'Glee', url: 'https://api.dicebear.com/9.x/big-smile/svg?seed=Liliana' },
    { id: 'pixel4', name: 'Block', url: 'https://api.dicebear.com/9.x/pixel-art/svg?seed=Oliver' },
];

// --- CORE PROFANITY FILTER (Hinglish/Hindi & High Severity) ---
// Expanded to cover common acronyms and creative spellings
const LOCAL_BANNED_TERMS = [
    // Hinglish / Slang / Acronyms
    'chut', 'chutiya', 'chooch', 'bhosdi', 'bhosad', 'madarchod', 'mc', 'bc', 'bhenchod', 'behenchod', 
    'gand', 'gaand', 'lund', 'lavda', 'lauda', 'loda', 'jhaatu', 'jhat', 'kutta', 'kamina', 'harami', 
    'saala', 'randi', 'raand', 'bhadwa', 'chinaal', 'hijra', 'chakka', 'tatti', 'panty', 'nipple',
    // Common Abusive Acronyms (Indian Gaming/Chat Context)
    'bkl', 'bkc', 'mkc', 'mkl', 'tmkc', 'tkmkc', 'pkmkb', 'kbkc', 'bsdk', 'bhosdk', 'mdrchod',
    
    // Hindi (Devanagari)
    'चूतिया', 'मादरचोद', 'भोसड़ी', 'गांड', 'लंड', 'लौड़ा', 'रंडी', 'हरामी', 'साला', 'कुत्ता', 'भड़वा', 'बेशर्म', 'कमीना',
    
    // Hardcore English & Variations
    'fuck', 'nigger', 'nigga', 'faggot', 'rapist', 'pedophile', 'pedo', 'whore', 'slut', 'cunt', 'dick', 'pussy', 'cock', 'boobs', 'vagina', 'penis', 'anal', 'incest', 'porn', 'xxx', 'sex', 'hitler', 'nazi'
];

// --- MULTI-SOURCE BLOCKLIST CONFIGURATION ---
const BLOCKLIST_SOURCES = [
    // Source 1: Reliable open-source JSON object { words: [...] }
    { url: 'https://raw.githubusercontent.com/web-mech/badwords/master/lib/lang.json', type: 'json' },
    // Source 2: Extensive English List (Text format)
    { url: 'https://raw.githubusercontent.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/master/en', type: 'text' },
    // Source 3: Extensive Hindi List (Text format)
    { url: 'https://raw.githubusercontent.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/master/hi', type: 'text' },
    // Source 4: zautumnz Profane Words (Flat JSON Array)
    { url: 'https://raw.githubusercontent.com/zautumnz/profane-words/master/words.json', type: 'json' }
];

const LeaderboardSubmitModal: React.FC<LeaderboardSubmitModalProps> = ({ onClose, workerUrl }) => {
  useScrollLock(true);
  const settings = useSettingsStore();
  
  const [status, setStatus] = useState<'idle' | 'processing' | 'success' | 'error'>('idle');
  const [errorMsg, setErrorMsg] = useState('');
  const [username, setUsername] = useState('');
  // Fix: Explicitly assert that CHARACTERS[0] exists to prevent undefined type
  const [selectedChar, setSelectedChar] = useState(CHARACTERS[0]!);
  
  // Store the full list here
  const [fullBlocklist, setFullBlocklist] = useState<string[]>(LOCAL_BANNED_TERMS);
  const isListLoaded = useRef(false);

  // Anti-Spam Check
  const canSubmit = settings.adWatchCount >= 5 || settings.isContributor;

  // --- FETCH REMOTE BLOCKLISTS ---
  useEffect(() => {
      if (isListLoaded.current) return;
      
      const loadBlocklists = async () => {
          // Initialize with local high-severity terms
          let combinedWords = new Set<string>([...LOCAL_BANNED_TERMS]);

          const fetchSource = async (source: { url: string, type: string }) => {
              try {
                  const res = await fetch(source.url);
                  if (!res.ok) return;
                  
                  if (source.type === 'json') {
                      const data = await res.json();
                      // Handle { words: [...] } format (web-mech)
                      if (data && Array.isArray(data.words)) {
                          data.words.forEach((w: string) => combinedWords.add(w.toLowerCase()));
                      }
                      // Handle ["word", "word"] format (zautumnz)
                      else if (Array.isArray(data)) {
                          data.forEach((w: any) => {
                              if (typeof w === 'string') combinedWords.add(w.toLowerCase());
                          });
                      }
                  } else if (source.type === 'text') {
                      const text = await res.text();
                      // Split by new line, trim, ignore empty or comments
                      text.split('\n').forEach(line => {
                          const w = line.trim().toLowerCase();
                          if (w && !w.startsWith('#')) combinedWords.add(w);
                      });
                  }
              } catch (e) {
                  console.warn(`Failed to load blocklist from ${source.url}`);
              }
          };

          // Fetch all sources in parallel
          await Promise.all(BLOCKLIST_SOURCES.map(fetchSource));
          
          setFullBlocklist(Array.from(combinedWords));
          isListLoaded.current = true;
      };

      loadBlocklists();
  }, []);

  // --- NORMALIZE TEXT (De-obfuscator) ---
  const normalizeText = (text: string): string => {
      let clean = text.toLowerCase();
      
      // 1. Replace Leet Speak / Common Substitutions
      clean = clean
          .replace(/0/g, 'o')
          .replace(/1/g, 'i')
          .replace(/3/g, 'e')
          .replace(/4/g, 'a')
          .replace(/5/g, 's')
          .replace(/7/g, 't')
          .replace(/@/g, 'a')
          .replace(/\$/g, 's')
          .replace(/!/g, 'i')
          .replace(/\(/g, 'c')
          .replace(/\|/g, 'i')
          .replace(/v/g, 'u'); // Sometimes 'v' is used for 'u' in slang

      // 2. Remove EVERYTHING that is not a letter (a-z). 
      // This kills "dot tricks" like f.u.c.k, f_u_c_k, f u c k, f-u-c-k
      // NOTE: We only do this for checking English/Romanized terms.
      return clean.replace(/[^a-z]/g, '');
  };

  const checkProfanity = (text: string): boolean => {
      const normalized = normalizeText(text); // Compressed version (e.g. "f.u.c.k" -> "fuck")
      const lowerRaw = text.toLowerCase(); // Raw version (for Devanagari/Hindi checks)

      // Check 1: Normalized check against full blocklist
      const normalizedMatch = fullBlocklist.some(term => {
          // Only check English/Roman terms against normalized string
          if (/[^a-z]/.test(term)) return false; 
          return normalized.includes(term);
      });

      // Check 2: Raw check (catch specific Hindi phrases or mixed script evasion)
      const rawMatch = fullBlocklist.some(term => lowerRaw.includes(term));

      return normalizedMatch || rawMatch;
  };

  // --- SUBMISSION HANDLER ---
  const handleSubmit = async (isSimulation: boolean = false) => {
      setErrorMsg('');
      const cleanName = username.trim();

      if (!cleanName) {
          setErrorMsg("Please enter a cool name!");
          return;
      }
      if (cleanName.length > 15) {
          setErrorMsg("Name too long (Max 15 chars)");
          return;
      }
      if (cleanName.length < 3) {
          setErrorMsg("Name too short (Min 3 chars)");
          return;
      }
      
      // Perform strict validation
      if (checkProfanity(cleanName)) {
          setErrorMsg("Let's keep it family friendly!");
          return;
      }
      
      // Strict Rule: Must contribute unless in Simulation Mode
      if (!canSubmit && !isSimulation) {
          setErrorMsg("You need 5 contributions to join the Hall of Fame!");
          return;
      }

      setStatus('processing');

      // Artificial Delay for Animation (and Simulation)
      setTimeout(() => {
          if (isSimulation) {
              setStatus('success');
          } else {
              executeNetworkRequest();
          }
      }, 2500); 
  };

  const executeNetworkRequest = async () => {
      try {
          const payload = {
              username: username.trim(),
              avatarUrl: selectedChar.url, 
              xp: settings.adWatchCount * 10 + settings.submissionCount * 50,
              level: settings.submissionCount + Math.floor(settings.adWatchCount / 5),
              adWatchCount: settings.adWatchCount,
              submissionCount: settings.submissionCount,
              timestamp: Date.now()
          };

          const signature = await generateSignature(payload);
          
          const response = await fetch(workerUrl, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ data: payload, signature })
          });

          if (!response.ok) {
              const resData = await response.json();
              if (response.status === 429) throw new Error("Whoa! You can only join once every 24 hours.");
              throw new Error(resData.error || "Portal connection failed.");
          }

          setStatus('success');
          settings.registerSubmission();

      } catch (e: any) {
          setStatus('error');
          setErrorMsg(e.message || "Unknown Error");
      }
  };

  return createPortal(
    <div className="fixed inset-0 z-[10000] flex items-center justify-center touch-none">
        <div 
            className="absolute inset-0 bg-black/80 backdrop-blur-md transition-all duration-300" 
            onClick={onClose}
        ></div>
        
        <div 
            className="relative w-[90vw] max-w-sm bg-surface border border-theme-border rounded-[2.5rem] shadow-2xl overflow-hidden flex flex-col animate-slide-up z-50 max-h-[90vh]"
        >
            {/* Header */}
            <div className="shrink-0 p-6 pb-2 text-center relative z-10">
                <div className="absolute top-0 left-0 right-0 h-32 bg-gradient-to-b from-primary/10 to-transparent -z-10"></div>
                <h2 className="text-3xl font-black text-theme-text tracking-tight mb-1">Fame Gate</h2>
                <p className="text-xs font-bold text-theme-sub uppercase tracking-widest">Join the Legends</p>
                
                <button onClick={onClose} className="absolute top-5 right-5 w-8 h-8 rounded-full bg-theme-element flex items-center justify-center text-theme-sub hover:text-theme-text transition-colors">
                    <i className="fas fa-times"></i>
                </button>
            </div>

            <div className="flex-1 overflow-y-auto p-6 pt-2 no-scrollbar">
                {status === 'idle' && (
                    <div className="space-y-6">
                        
                        {/* Stats Pill */}
                        <div className="flex justify-center">
                            <div className="inline-flex items-center gap-3 bg-theme-element px-4 py-2 rounded-2xl border border-theme-border">
                                <div className="flex flex-col items-center">
                                    <span className="text-[10px] font-bold text-theme-sub uppercase">Level</span>
                                    <span className="text-lg font-black text-primary leading-none">{settings.submissionCount + Math.floor(settings.adWatchCount / 5)}</span>
                                </div>
                                <div className="w-px h-6 bg-theme-border"></div>
                                <div className="flex flex-col items-center">
                                    <span className="text-[10px] font-bold text-theme-sub uppercase">XP</span>
                                    <span className="text-lg font-black text-primary leading-none">{settings.adWatchCount * 10 + settings.submissionCount * 50}</span>
                                </div>
                            </div>
                        </div>

                        {/* Character Selector */}
                        <div>
                            <label className="block text-center text-xs font-bold text-theme-sub mb-3 uppercase tracking-wider">Choose Your Avatar</label>
                            <div className="grid grid-cols-5 gap-3">
                                {CHARACTERS.map((char) => (
                                    <div 
                                        key={char.id}
                                        onClick={() => setSelectedChar(char)}
                                        className={`aspect-square rounded-full p-1 cursor-pointer transition-all duration-300 ${selectedChar?.id === char.id ? 'bg-primary scale-110 shadow-lg shadow-primary/30' : 'bg-theme-element hover:bg-theme-hover'}`}
                                    >
                                        <img src={char.url} alt={char.name} className="w-full h-full rounded-full bg-white" />
                                    </div>
                                ))}
                            </div>
                            <p className="text-center text-[10px] font-bold text-theme-text mt-2 opacity-60">{selectedChar?.name}</p>
                        </div>

                        {/* Name Input */}
                        <div>
                            <label className="block text-xs font-bold text-theme-sub mb-2 uppercase ml-1">Your Alias</label>
                            <input 
                                type="text" 
                                className="w-full bg-theme-input border border-theme-border rounded-2xl px-5 py-4 text-center font-bold text-lg focus:border-primary focus:ring-2 focus:ring-primary/10 outline-none transition-all placeholder-theme-sub/30"
                                placeholder="Captain Code"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                maxLength={15}
                            />
                        </div>

                        {/* Action Button */}
                        <div className="space-y-3">
                            <button 
                                onClick={() => handleSubmit(false)}
                                disabled={!username || !canSubmit}
                                className={`w-full py-4 rounded-2xl font-black text-lg shadow-xl transition-all relative overflow-hidden group ${
                                    username && canSubmit
                                    ? 'bg-gradient-to-r from-primary to-neon text-white shadow-primary/30 hover:scale-[1.02] active:scale-95' 
                                    : 'bg-theme-element text-theme-sub cursor-not-allowed opacity-70'
                                }`}
                            >
                                <span className="relative z-10 flex items-center justify-center gap-2">
                                    <i className="fas fa-rocket group-hover:animate-bounce"></i>
                                    {canSubmit ? "Beam Me Up!" : "Need 5 Contributions"}
                                </span>
                                {username && canSubmit && <div className="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300"></div>}
                            </button>
                            
                            {/* ERROR MESSAGE */}
                            {errorMsg && <p className="text-red-500 text-xs font-bold text-center bg-red-500/10 py-2 rounded-xl animate-pulse">{errorMsg}</p>}

                            {/* DEBUG BUTTON (Always visible for testing UI logic, though guarded by isDevUnlocked typically, user requested it for testing UI) */}
                            {settings.isDevUnlocked && (
                                <button 
                                    onClick={() => handleSubmit(true)}
                                    className="w-full py-2 text-[10px] font-bold text-theme-sub uppercase tracking-widest opacity-50 hover:opacity-100 transition-opacity"
                                >
                                    <i className="fas fa-bug mr-1"></i> Simulate Protocol (Debug)
                                </button>
                            )}
                        </div>
                    </div>
                )}

                {status === 'processing' && (
                    <div className="flex flex-col items-center justify-center py-10 space-y-6">
                        <div className="relative w-32 h-32">
                            {/* Spinning Rings */}
                            <div className="absolute inset-0 border-4 border-primary/20 rounded-full"></div>
                            <div className="absolute inset-0 border-4 border-t-primary border-r-neon border-b-transparent border-l-transparent rounded-full animate-spin"></div>
                            <div className="absolute inset-4 border-4 border-t-acid border-l-acid border-b-transparent border-r-transparent rounded-full animate-spin-slow"></div>
                            
                            {/* Avatar in Center */}
                            <div className="absolute inset-2 rounded-full overflow-hidden bg-white flex items-center justify-center animate-pulse">
                                <img src={selectedChar?.url} className="w-full h-full object-cover" alt="" />
                            </div>
                        </div>
                        
                        <div className="text-center">
                            <h3 className="text-xl font-black text-theme-text animate-pulse">Warping to Multiverse...</h3>
                            <p className="text-xs font-bold text-theme-sub mt-2">Writing to Global Ledger</p>
                        </div>
                    </div>
                )}

                {status === 'success' && (
                    <div className="flex flex-col items-center text-center py-6">
                        <div className="w-24 h-24 bg-green-500 rounded-full flex items-center justify-center text-5xl text-white mb-6 shadow-2xl shadow-green-500/40 animate-bounce">
                            <i className="fas fa-check"></i>
                        </div>
                        <h3 className="text-3xl font-black text-theme-text mb-2 tracking-tight">You're In!</h3>
                        <p className="text-theme-sub text-sm font-medium mb-8 px-4 leading-relaxed">
                            <b>{username}</b> has been immortalized in the Hall of Fame.
                        </p>
                        
                        <div className="bg-theme-element border border-theme-border px-5 py-3 rounded-2xl mb-8 flex items-center gap-3">
                            <i className="fas fa-hourglass-half text-primary animate-pulse"></i>
                            <p className="text-xs font-bold text-theme-sub text-left">
                                The leaderboard refreshes every 60 minutes. Check back soon!
                            </p>
                        </div>

                        <button onClick={onClose} className="w-full py-4 bg-theme-text text-surface rounded-2xl font-bold text-sm hover:scale-[1.02] active:scale-95 transition-all">
                            Return to Base
                        </button>
                    </div>
                )}

                {status === 'error' && (
                    <div className="flex flex-col items-center text-center py-8">
                        <div className="w-20 h-20 bg-red-500/10 rounded-full flex items-center justify-center text-3xl text-red-500 mb-4">
                            <i className="fas fa-meteor"></i>
                        </div>
                        <h3 className="text-xl font-black text-red-500 mb-2">Signal Lost</h3>
                        <p className="text-theme-sub text-xs font-medium mb-6 bg-red-500/5 p-4 rounded-xl border border-red-500/10">
                            {errorMsg}
                        </p>
                        <button onClick={() => setStatus('idle')} className="px-8 py-3 bg-red-500 text-white rounded-xl font-bold text-sm hover:bg-red-600 shadow-lg shadow-red-500/20 active:scale-95 transition-all">
                            Try Again
                        </button>
                    </div>
                )}
            </div>
        </div>
    </div>,
    document.body
  );
};

export default LeaderboardSubmitModal;
