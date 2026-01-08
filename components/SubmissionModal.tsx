import React, { useState, useEffect } from 'react';
import { AppCategory, Platform, Tab } from '../types';

interface SubmissionModalProps {
  onClose: () => void;
  currentStoreVersion: string;
  onSuccess?: () => void;
  submissionCount?: number;
  activeTab: Tab;
}

// Helper Component for Labels with Clickable Tooltips
const LabelWithTooltip: React.FC<{ 
    label: string; 
    tooltip: string; 
    required?: boolean;
    onHelp: (text: string) => void;
}> = ({ label, tooltip, required, onHelp }) => (
    <label className="block text-xs font-bold text-theme-sub mb-1.5 uppercase flex items-center">
        {label}
        {required && <span className="text-red-500 ml-0.5">*</span>}
        <button 
            type="button"
            onClick={() => onHelp(tooltip)}
            className="ml-1.5 text-theme-sub opacity-40 hover:opacity-100 cursor-help text-[10px] transition-opacity focus:outline-none"
            title="Click for info"
        >
            <i className="fas fa-question-circle"></i>
        </button>
    </label>
);

const SubmissionModal: React.FC<SubmissionModalProps> = ({ onClose, currentStoreVersion, onSuccess, submissionCount = 0, activeTab }) => {
  // Default mode depends on context. Android -> Obtainium by default. PC/TV -> Manual.
  const [mode, setMode] = useState<'obtainium' | 'manual'>(activeTab === 'android' ? 'obtainium' : 'manual');
  const [jsonInput, setJsonInput] = useState('');
  const [error, setError] = useState('');
  const [activeHelpText, setActiveHelpText] = useState<string | null>(null);

  // Screenshot Management
  const [screenshotInput, setScreenshotInput] = useState('');
  const [addedScreenshots, setAddedScreenshots] = useState<string[]>([]);

  // Obtainium Mode Overrides
  const [obtainiumIcon, setObtainiumIcon] = useState('');
  const [obtainiumKeyword, setObtainiumKeyword] = useState('');
  const [obtainiumDescription, setObtainiumDescription] = useState('');

  // Manual Form State
  const [formData, setFormData] = useState({
    name: '',
    id: '', 
    description: '',
    icon: '',
    repoUrl: '',
    githubRepo: '',
    gitlabRepo: '',
    gitlabDomain: '',
    releaseKeyword: '',
    packageName: '',
    category: AppCategory.UTILITY,
    author: '',
    officialSite: '', 
  });

  // Calculate Trust Stats
  const baseCooldown = 180; // 3 hours
  const reductionPerSub = 15; // 15 mins
  const maxReduction = 150; // Down to 30 mins
  const currentReduction = Math.min(submissionCount * reductionPerSub, maxReduction);
  const currentCooldown = baseCooldown - currentReduction;
  
  const currentLevel = submissionCount;
  
  // Rank Logic
  const getRankInfo = (level: number) => {
      if (level >= 10) return { title: "Elite", color: "text-acid", bg: "bg-acid", icon: "fa-crown" };
      if (level >= 5) return { title: "Expert", color: "text-purple-400", bg: "bg-purple-500", icon: "fa-star" };
      if (level >= 1) return { title: "Contributor", color: "text-blue-400", bg: "bg-blue-500", icon: "fa-shield-alt" };
      return { title: "Newcomer", color: "text-gray-400", bg: "bg-gray-500", icon: "fa-user" };
  };

  const rank = getRankInfo(currentLevel);

  // Auto-generate ID
  useEffect(() => {
    if (formData.name) {
        const generatedId = formData.name.toLowerCase().replace(/[^a-z0-9]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
        setFormData(prev => ({ ...prev, id: generatedId }));
    }
  }, [formData.name]);

  useEffect(() => {
    // Auto-fill Repo details if we are on Android
    if (activeTab === 'android' && formData.repoUrl) {
        // GitHub Parsing
        if (formData.repoUrl.includes('github.com')) {
            try {
                const parts = formData.repoUrl.split('github.com/')[1].split('/');
                if (parts.length >= 2) {
                    const owner = parts[0];
                    const repo = parts[1].replace('.git', '').replace(/\/$/, '');
                    setFormData(prev => ({ 
                        ...prev, 
                        githubRepo: `${owner}/${repo}`,
                        gitlabRepo: '',
                        gitlabDomain: '',
                        author: prev.author || owner 
                    }));
                }
            } catch (e) {}
        }
        // GitLab Parsing
        else if (formData.repoUrl.includes('gitlab')) {
            try {
                const urlObj = new URL(formData.repoUrl);
                const pathParts = urlObj.pathname.split('/').filter(p => p);
                if (pathParts.length >= 2) {
                    setFormData(prev => ({
                        ...prev,
                        githubRepo: '',
                        gitlabRepo: pathParts.join('/'),
                        gitlabDomain: urlObj.hostname,
                        author: prev.author || pathParts[0]
                    }));
                }
            } catch (e) {}
        }
    }
  }, [formData.repoUrl, activeTab]);

  const handleInputChange = (field: string, value: string) => {
      setFormData(prev => ({ ...prev, [field]: value }));
  };

  const handleAddScreenshot = () => {
      if (screenshotInput.trim()) {
          if (!screenshotInput.startsWith('http')) {
              setError("Screenshot must be a valid URL starting with http/https");
              setTimeout(() => setError(''), 3000);
              return;
          }
          setAddedScreenshots([...addedScreenshots, screenshotInput.trim()]);
          setScreenshotInput('');
      }
  };

  const handleRemoveScreenshot = (index: number) => {
      setAddedScreenshots(prev => prev.filter((_, i) => i !== index));
  };

  const isValidRepoUrl = (url: string) => {
      if (!url) return false;
      const lower = url.toLowerCase();
      return lower.includes('github.com') || lower.includes('gitlab');
  };

  const generateIssueUrl = (appsData: any[]) => {
      const jsonPayload = JSON.stringify(appsData, null, 2);
      const title = `App Submission [${appsData.length} App${appsData.length > 1 ? 's' : ''}]`;
      const body = `
### App Submission Request

I would like to submit the following apps to Orion Store.

\`\`\`json
${jsonPayload}
\`\`\`

*Generated by Orion Store v${currentStoreVersion}*
      `.trim();

      return `https://github.com/RookieEnough/Orion-Data/issues/new?title=${encodeURIComponent(title)}&body=${encodeURIComponent(body)}`;
  };

  const handleSubmit = () => {
      setError('');
      
      if (addedScreenshots.length < 3) {
          setError("At least 3 screenshots are required.");
          return;
      }

      let appsToSubmit: any[] = [];

      try {
          if (mode === 'obtainium') {
              if (!jsonInput.trim()) { setError("Please paste JSON content."); return; }
              if (!obtainiumIcon.trim()) { setError("Icon URL is required."); return; }

              const parsed = JSON.parse(jsonInput);
              
              let rawList = [];
              if (Array.isArray(parsed)) rawList = parsed;
              else if (parsed.apps && Array.isArray(parsed.apps)) rawList = parsed.apps;
              else if (parsed.url) rawList = [parsed];
              else throw new Error("Invalid format");

              appsToSubmit = rawList.filter((item: any) => item.url && isValidRepoUrl(item.url)).map((item: any) => {
                  let owner = 'unknown';
                  let repo = 'unknown';
                  let githubPath = undefined;
                  let gitlabPath = undefined;
                  let gitlabDomain = undefined;
                  
                  if (item.url.includes('github.com')) {
                      const parts = item.url.split('github.com/')[1]?.split('/');
                      owner = parts ? parts[0] : 'unknown';
                      repo = parts ? parts[1].replace('.git', '').replace(/\/$/, '') : 'unknown';
                      githubPath = `${owner}/${repo}`;
                  } else if (item.url.includes('gitlab')) {
                      // GitLab Parsing Logic
                      try {
                          const urlObj = new URL(item.url);
                          const pathParts = urlObj.pathname.split('/').filter((p: string) => p);
                          if (pathParts.length >= 2) {
                              owner = pathParts[0];
                              repo = pathParts[pathParts.length - 1];
                              gitlabPath = pathParts.join('/');
                              gitlabDomain = urlObj.hostname;
                          }
                      } catch(e) {}
                  }

                  let desc = obtainiumDescription;
                  let author = item.author || owner;

                  if (item.additionalSettings) {
                      try {
                          const sub = typeof item.additionalSettings === 'string' ? JSON.parse(item.additionalSettings) : item.additionalSettings;
                          if (!desc) desc = sub.about || '';
                          if (sub.appAuthor) author = sub.appAuthor;
                      } catch(e) {}
                  }

                  if (!desc) desc = `A new app discovered via Obtainium${item.overrideSource === 'GitLab' ? ' (GitLab)' : ''}.`;

                  return {
                      id: item.id || `sub-${owner}-${repo}`.toLowerCase(),
                      name: item.name || repo,
                      description: desc, 
                      icon: obtainiumIcon, 
                      version: "Latest",
                      latestVersion: "Latest",
                      downloadUrl: "#",
                      repoUrl: item.url,
                      githubRepo: githubPath,
                      gitlabRepo: gitlabPath,
                      gitlabDomain: gitlabDomain,
                      releaseKeyword: obtainiumKeyword || '',
                      packageName: item.packageName || item.id || '',
                      category: AppCategory.UTILITY, 
                      platform: Platform.ANDROID,
                      size: "Varies",
                      author: author,
                      screenshots: addedScreenshots 
                  };
              });

              if (appsToSubmit.length === 0) {
                  setError("No valid repositories found in the pasted JSON.");
                  return;
              }

          } else {
              // MANUAL MODE
              if (activeTab === 'android') {
                  if (!formData.repoUrl) { setError("Repo URL is required."); return; }
                  if (!isValidRepoUrl(formData.repoUrl)) { setError("Only GitHub or GitLab repositories are allowed."); return; }
                  if (!formData.packageName) { setError("Package Name is required."); return; }
              } else {
                  if (!formData.officialSite) { setError("Official Website Link is required."); return; }
              }

              if (!formData.name) { setError("App Name is required."); return; }
              if (!formData.category) { setError("Category is required."); return; }
              if (!formData.author) { setError("Author is required."); return; }
              if (!formData.description) { setError("Description is required."); return; }
              if (!formData.icon) { setError("Icon URL is required."); return; }
              if (!formData.id) { setError("App ID is required."); return; }

              const platform = activeTab === 'pc' ? Platform.PC : activeTab === 'tv' ? Platform.TV : Platform.ANDROID;

              appsToSubmit = [{
                  id: formData.id,
                  name: formData.name,
                  description: formData.description,
                  icon: formData.icon,
                  version: "Latest", 
                  latestVersion: "Latest",
                  downloadUrl: formData.officialSite || "#", 
                  repoUrl: formData.repoUrl,
                  githubRepo: formData.githubRepo || undefined,
                  gitlabRepo: formData.gitlabRepo || undefined,
                  gitlabDomain: formData.gitlabDomain || undefined,
                  releaseKeyword: formData.releaseKeyword,
                  packageName: formData.packageName,
                  category: formData.category,
                  platform: platform,
                  size: "Varies",
                  author: formData.author,
                  officialSite: formData.officialSite,
                  screenshots: addedScreenshots
              }];
          }

          if (onSuccess) onSuccess();

          const url = generateIssueUrl(appsToSubmit);
          window.open(url, '_blank');
          
          onClose();

      } catch (e) {
          console.error(e);
          setError("Failed to parse data. Please check inputs.");
      }
  };

  const renderScreenshotSection = () => (
      <div>
          <LabelWithTooltip 
              label="Screenshots (Min 3)" 
              tooltip="Add direct image URLs. Copy links from the repo's Readme, Play Store, or F-Droid." 
              required
              onHelp={setActiveHelpText}
          />
          <div className="flex gap-2 mb-2">
              <input 
                  type="text"
                  className="flex-1 bg-theme-input border border-theme-border rounded-lg px-2 py-2 text-xs focus:border-primary outline-none"
                  placeholder="https://image.url/screenshot.jpg"
                  value={screenshotInput}
                  onChange={(e) => setScreenshotInput(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleAddScreenshot()}
              />
              <button 
                  onClick={handleAddScreenshot}
                  className="w-8 h-8 rounded-lg bg-primary text-white flex items-center justify-center hover:bg-primary/90 transition-colors shadow-lg shadow-primary/20"
              >
                  <i className="fas fa-plus text-xs"></i>
              </button>
          </div>
          
          {addedScreenshots.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                  {addedScreenshots.map((url, idx) => (
                      <div key={idx} className="flex items-center gap-2 bg-card border border-theme-border px-2 py-1.5 rounded-md animate-fade-in">
                          <i className="fas fa-image text-theme-sub text-[10px]"></i>
                          <span className="text-[10px] font-bold text-theme-text">Screenshot {idx + 1}</span>
                          <button 
                              onClick={() => handleRemoveScreenshot(idx)}
                              className="w-4 h-4 flex items-center justify-center text-red-500 hover:bg-red-500/10 rounded-full transition-colors"
                          >
                              <i className="fas fa-times text-[8px]"></i>
                          </button>
                      </div>
                  ))}
              </div>
          ) : (
              <p className="text-[10px] text-theme-sub italic opacity-50 pl-1">No screenshots added yet.</p>
          )}
      </div>
  );

  const resetForm = () => {
      setError('');
      setAddedScreenshots([]);
      setObtainiumDescription('');
      setObtainiumIcon('');
      setObtainiumKeyword('');
      setJsonInput('');
  };

  const isAndroid = activeTab === 'android';

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 animate-fade-in">
        <div className="absolute inset-0 bg-black/80 backdrop-blur-md" onClick={onClose}></div>
        
        {/* Main Content Modal */}
        <div className="bg-surface border border-theme-border rounded-3xl p-0 w-full max-w-lg relative z-10 animate-slide-up shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
            
            {/* Header */}
            <div className="p-6 pb-4 border-b border-theme-border flex justify-between items-center bg-surface/95 backdrop-blur-xl z-20">
                <div>
                    <h3 className="text-2xl font-black text-theme-text">Submit {activeTab === 'android' ? 'App' : activeTab === 'tv' ? 'TV App' : 'Software'}</h3>
                    <p className="text-xs text-theme-sub">Contribute to the Orion Library</p>
                </div>
                <button onClick={onClose} className="w-8 h-8 rounded-full bg-theme-element border border-theme-border flex items-center justify-center text-theme-text hover:bg-theme-hover transition-colors">
                    <i className="fas fa-times"></i>
                </button>
            </div>

            {/* Scrollable Content */}
            <div className="overflow-y-auto p-6 flex-1 no-scrollbar">
                
                {/* TRUST LEVEL BANNER */}
                <div className={`bg-gradient-to-r ${currentLevel >= 10 ? 'from-acid/10 to-primary/10' : 'from-primary/10 to-blue-500/10'} border border-primary/20 rounded-2xl p-4 mb-6 relative overflow-hidden`}>
                    <div className="absolute top-0 right-0 w-24 h-24 bg-primary/10 rounded-full blur-xl -translate-y-1/2 translate-x-1/2"></div>
                    <div className="relative z-10">
                        <div className="flex justify-between items-start mb-1">
                            <div className="flex items-center gap-1.5">
                                <span className={`text-[10px] font-black uppercase tracking-widest ${rank.color}`}>
                                    {rank.title} (Lvl {currentLevel})
                                </span>
                                {currentLevel >= 10 && <i className="fas fa-crown text-[10px] text-acid animate-bounce"></i>}
                            </div>
                            {currentReduction > 0 && (
                                <span className="text-[10px] font-bold text-green-500 bg-green-500/10 px-2 py-0.5 rounded-full">
                                    -{currentReduction}m Cooldown
                                </span>
                            )}
                        </div>
                        <div className="flex items-baseline gap-1 mb-2">
                            <h4 className="text-2xl font-black text-theme-text">{Math.floor(currentCooldown / 60)}h {currentCooldown % 60}m</h4>
                            <span className="text-xs text-theme-sub font-medium">wait time</span>
                        </div>
                        <div className="w-full bg-theme-element h-1.5 rounded-full overflow-hidden">
                            <div 
                                className={`h-full rounded-full transition-all duration-500 ${currentLevel >= 10 ? 'bg-gradient-to-r from-acid to-primary' : 'bg-gradient-to-r from-primary to-blue-400'}`}
                                style={{ width: `${Math.min((currentLevel / 10) * 100, 100)}%` }}
                            ></div>
                        </div>
                    </div>
                </div>

                {/* Tabs - Only show for Android */}
                {isAndroid ? (
                    <div className="flex p-1 bg-theme-input rounded-xl mb-6 sticky top-0 z-10 shadow-sm border border-theme-border">
                        <button 
                            onClick={() => { setMode('obtainium'); resetForm(); }}
                            className={`flex-1 py-2 rounded-lg text-xs font-bold transition-all ${mode === 'obtainium' ? 'bg-surface shadow-sm text-primary' : 'text-theme-sub hover:text-theme-text'}`}
                        >
                            Obtainium Import
                        </button>
                        <button 
                            onClick={() => { setMode('manual'); resetForm(); }}
                            className={`flex-1 py-2 rounded-lg text-xs font-bold transition-all ${mode === 'manual' ? 'bg-surface shadow-sm text-primary' : 'text-theme-sub hover:text-theme-text'}`}
                        >
                            Direct Repo
                        </button>
                    </div>
                ) : (
                    <div className="mb-4">
                        <p className="text-xs font-bold text-theme-text bg-theme-element p-3 rounded-xl border border-theme-border">
                            Submitting request for <span className="text-primary uppercase">{activeTab}</span>
                        </p>
                    </div>
                )}

                {error && (
                    <div className="mb-4 p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-500 text-xs font-bold flex items-center gap-2 animate-pulse">
                        <i className="fas fa-exclamation-circle"></i>
                        {error}
                    </div>
                )}

                {mode === 'obtainium' && isAndroid ? (
                    <div className="space-y-4">
                        <div>
                            <label className="block text-xs font-bold text-theme-sub mb-2 uppercase">Paste JSON Export</label>
                            <textarea 
                                className="w-full h-40 bg-theme-input border border-theme-border rounded-xl p-3 text-xs font-mono focus:border-primary outline-none resize-none"
                                placeholder='{"apps": [{"url": "https://gitlab.com/..."}]}'
                                value={jsonInput}
                                onChange={(e) => setJsonInput(e.target.value)}
                            ></textarea>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                            <div>
                                <LabelWithTooltip 
                                    label="Icon URL" 
                                    tooltip="Override the default avatar." 
                                    required 
                                    onHelp={setActiveHelpText}
                                />
                                <input 
                                    type="text"
                                    className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none"
                                    placeholder="https://..."
                                    value={obtainiumIcon}
                                    onChange={(e) => setObtainiumIcon(e.target.value)}
                                />
                            </div>
                            <div>
                                <LabelWithTooltip 
                                    label="Release Keyword" 
                                    tooltip="Filter releases by this keyword." 
                                    onHelp={setActiveHelpText}
                                />
                                <input 
                                    type="text"
                                    className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none"
                                    placeholder="e.g. app-release"
                                    value={obtainiumKeyword}
                                    onChange={(e) => setObtainiumKeyword(e.target.value)}
                                />
                            </div>
                        </div>
                        <div>
                            <LabelWithTooltip label="Description Override" tooltip="Optional short description. If empty, Orion will try to extract it from the JSON metadata." onHelp={setActiveHelpText} />
                            <textarea 
                                className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none h-20 resize-none"
                                placeholder="What does this app do?"
                                value={obtainiumDescription}
                                onChange={(e) => setObtainiumDescription(e.target.value)}
                            />
                        </div>
                        <div className="p-4 bg-theme-element/50 rounded-xl space-y-3 border border-theme-border">
                            <p className="text-[10px] font-bold text-theme-sub uppercase tracking-wider mb-2">Media</p>
                            {renderScreenshotSection()}
                        </div>
                    </div>
                ) : (
                    <div className="space-y-4">
                        {/* Conditional URL Field */}
                        <div>
                            {isAndroid ? (
                                <>
                                    <LabelWithTooltip label="Repo URL" tooltip="GitHub or GitLab URL." required onHelp={setActiveHelpText} />
                                    <input 
                                        type="text"
                                        className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none"
                                        placeholder="https://gitlab.com/owner/repo"
                                        value={formData.repoUrl}
                                        onChange={(e) => handleInputChange('repoUrl', e.target.value)}
                                    />
                                    {formData.githubRepo && (
                                        <p className="text-[10px] text-green-500 mt-1 font-mono flex items-center gap-1">
                                            <i className="fab fa-github"></i> GitHub: {formData.githubRepo}
                                        </p>
                                    )}
                                    {formData.gitlabRepo && (
                                        <p className="text-[10px] text-orange-500 mt-1 font-mono flex items-center gap-1">
                                            <i className="fab fa-gitlab"></i> GitLab: {formData.gitlabRepo}
                                        </p>
                                    )}
                                </>
                            ) : (
                                <>
                                    <LabelWithTooltip 
                                      label="Official Website / Repo Link" 
                                      tooltip="The source URL for this software (Website, GitHub, etc)." 
                                      required 
                                      onHelp={setActiveHelpText} 
                                    />
                                    <input 
                                        type="text"
                                        className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none"
                                        placeholder="https://example.com/download"
                                        value={formData.officialSite}
                                        onChange={(e) => handleInputChange('officialSite', e.target.value)}
                                    />
                                </>
                            )}
                        </div>

                        {/* Name & ID */}
                        <div className="grid grid-cols-2 gap-3">
                            <div>
                                <LabelWithTooltip label="App Name" tooltip="Display name." required onHelp={setActiveHelpText} />
                                <input 
                                    type="text"
                                    className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none"
                                    value={formData.name}
                                    onChange={(e) => handleInputChange('name', e.target.value)}
                                />
                            </div>
                            <div>
                                <LabelWithTooltip label="App ID (Auto)" tooltip="Internal unique ID." onHelp={setActiveHelpText} />
                                <input 
                                    type="text"
                                    className="w-full bg-theme-element border border-theme-border rounded-xl px-3 py-3 text-sm font-mono opacity-70 focus:outline-none"
                                    value={formData.id}
                                    readOnly
                                />
                            </div>
                        </div>

                        {/* Category & Author */}
                        <div className="grid grid-cols-2 gap-3">
                            <div>
                                <LabelWithTooltip label="Category" tooltip="Select category." required onHelp={setActiveHelpText} />
                                <select 
                                    className="w-full bg-theme-input border border-theme-border rounded-xl px-2 py-3 text-sm focus:border-primary outline-none appearance-none"
                                    value={formData.category}
                                    onChange={(e) => handleInputChange('category', e.target.value)}
                                >
                                    {Object.values(AppCategory).map(c => (
                                        <option key={c} value={c}>{c}</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <LabelWithTooltip label="Author" tooltip="Developer name." required onHelp={setActiveHelpText} />
                                <input 
                                    type="text"
                                    className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none"
                                    value={formData.author}
                                    onChange={(e) => handleInputChange('author', e.target.value)}
                                />
                            </div>
                        </div>

                        {/* Description */}
                        <div>
                            <LabelWithTooltip label="Description" tooltip="Short summary." required onHelp={setActiveHelpText} />
                            <textarea 
                                className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none h-20 resize-none"
                                value={formData.description}
                                onChange={(e) => handleInputChange('description', e.target.value)}
                            />
                        </div>

                        {/* Icon */}
                        <div>
                            <LabelWithTooltip label="Icon URL" tooltip="Direct link to icon image." required onHelp={setActiveHelpText} />
                            <input 
                                type="text"
                                className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none"
                                placeholder="https://..."
                                value={formData.icon}
                                onChange={(e) => handleInputChange('icon', e.target.value)}
                            />
                        </div>

                        {/* Media Section */}
                        <div className="p-4 bg-theme-element/50 rounded-xl space-y-3 border border-theme-border">
                            <p className="text-[10px] font-bold text-theme-sub uppercase tracking-wider mb-2">Media</p>
                            {renderScreenshotSection()}
                        </div>
                    </div>
                )}
            </div>

            {/* Footer Buttons */}
            <div className="p-6 border-t border-theme-border bg-surface/95 backdrop-blur-xl z-20">
                <button 
                    onClick={handleSubmit}
                    className="w-full py-4 rounded-2xl bg-primary text-white font-bold text-lg shadow-xl shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2 mb-2"
                >
                    <i className="fas fa-paper-plane"></i>
                    <span>Generate Request</span>
                </button>
                <p className="text-[10px] text-center text-theme-sub opacity-60">
                    This will open a pre-filled GitHub issue.
                </p>
            </div>
        </div>
        
        {/* Help Tooltip */}
        {activeHelpText && (
            <div className="absolute inset-0 z-[70] flex items-center justify-center p-8 animate-fade-in" onClick={() => setActiveHelpText(null)}>
                <div className="bg-black/90 text-white p-4 rounded-2xl max-w-xs text-center shadow-2xl border border-white/10 animate-scale-in" onClick={(e) => e.stopPropagation()}>
                    <i className="fas fa-info-circle text-2xl text-primary mb-2"></i>
                    <p className="text-sm font-medium">{activeHelpText}</p>
                    <button onClick={() => setActiveHelpText(null)} className="mt-4 text-xs font-bold text-theme-sub uppercase tracking-widest hover:text-white">Close</button>
                </div>
            </div>
        )}
    </div>
  );
};

export default SubmissionModal;