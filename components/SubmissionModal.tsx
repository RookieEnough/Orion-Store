
import React, { useState, useEffect, useMemo, useRef } from 'react';
import { AppCategory, Platform, Tab } from '../types';
import { useScrollLock } from '../hooks/useScrollLock';
import { useSettingsStore } from '../store/useAppStore';

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

type RepoDetectionStatus = 'idle' | 'loading' | 'success' | 'error';

interface RepoSource {
    provider: 'github' | 'gitlab' | 'codeberg';
    repoPath: string;
    owner: string;
    repo: string;
    domain?: string;
}

interface RepoAutofillResult {
    name?: string;
    description?: string;
    packageName?: string;
    author?: string;
}

const COMMON_ANDROID_PATHS = [
    'app/src/main/AndroidManifest.xml',
    'src/main/AndroidManifest.xml',
    'android/app/src/main/AndroidManifest.xml',
    'app/build.gradle',
    'app/build.gradle.kts',
    'android/app/build.gradle',
    'android/app/build.gradle.kts',
    'build.gradle',
    'build.gradle.kts'
] as const;

const PACKAGE_PATTERNS = [
    /applicationId\s*(?:=)?\s*["']([a-zA-Z0-9_.]+)["']/,
    /namespace\s*(?:=)?\s*["']([a-zA-Z0-9_.]+)["']/,
    /package\s*=\s*["']([a-zA-Z0-9_.]+)["']/
] as const;

const parseRepoSource = (repoUrl: string): RepoSource | null => {
    try {
        const url = new URL(repoUrl.trim());
        const rawParts = url.pathname.split('/').filter(Boolean);
        const stopMarkers = new Set(['tree', 'blob', 'raw', 'releases']);
        const markerIndex = rawParts.findIndex((part) => stopMarkers.has(part));
        const pathParts = (markerIndex >= 0 ? rawParts.slice(0, markerIndex) : rawParts).filter((part) => part !== '-');
        if (pathParts.length < 2) return null;

        const owner = pathParts[0] || '';
        const repo = (url.hostname.includes('gitlab') ? pathParts[pathParts.length - 1] || '' : pathParts[1] || '').replace(/\.git$/i, '');
        const repoPath = url.hostname.includes('gitlab')
            ? pathParts.join('/').replace(/\.git$/i, '')
            : `${owner}/${repo}`.replace(/\.git$/i, '');

        if (url.hostname.endsWith('github.com')) {
            return { provider: 'github', repoPath, owner, repo };
        }
        if (url.hostname.includes('gitlab')) {
            return { provider: 'gitlab', repoPath, owner, repo, domain: url.hostname };
        }
        if (url.hostname.endsWith('codeberg.org')) {
            return { provider: 'codeberg', repoPath, owner, repo };
        }
    } catch (error) {
        return null;
    }

    return null;
};

const extractPackageName = (content: string): string | undefined => {
    for (const pattern of PACKAGE_PATTERNS) {
        const match = content.match(pattern);
        if (match?.[1]) return match[1];
    }
    return undefined;
};

const humanizeRepoName = (repo: string): string => repo
    .replace(/\.git$/i, '')
    .replace(/[-_]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (char) => char.toUpperCase());

const fetchJson = async (url: string, init?: RequestInit) => {
    const response = await fetch(url, init);
    if (!response.ok) {
        throw new Error(`Request failed with status ${response.status}`);
    }
    return response.json();
};

const fetchTextIfExists = async (url: string, init?: RequestInit) => {
    const response = await fetch(url, init);
    if (!response.ok) return null;
    return response.text();
};

const detectRepoAutofill = async (source: RepoSource, githubToken?: string): Promise<RepoAutofillResult> => {
    let description = '';
    let defaultBranch = 'main';
    const githubHeaders = source.provider === 'github' && githubToken
        ? { Authorization: `Bearer ${githubToken}` }
        : undefined;

    if (source.provider === 'github') {
        const repoInfo = await fetchJson(`https://api.github.com/repos/${source.owner}/${source.repo}`, {
            headers: githubHeaders
        });
        description = repoInfo.description || '';
        defaultBranch = repoInfo.default_branch || defaultBranch;
    } else if (source.provider === 'gitlab') {
        const repoInfo = await fetchJson(`https://${source.domain}/api/v4/projects/${encodeURIComponent(source.repoPath)}`);
        description = repoInfo.description || '';
        defaultBranch = repoInfo.default_branch || defaultBranch;
    } else if (source.provider === 'codeberg') {
        const repoInfo = await fetchJson(`https://codeberg.org/api/v1/repos/${source.owner}/${source.repo}`);
        description = repoInfo.description || '';
        defaultBranch = repoInfo.default_branch || defaultBranch;
    }

    const rawBase =
        source.provider === 'github'
            ? `https://raw.githubusercontent.com/${source.owner}/${source.repo}/${defaultBranch}`
            : source.provider === 'gitlab'
                ? `https://${source.domain}/${source.repoPath}/-/raw/${defaultBranch}`
                : `https://codeberg.org/${source.owner}/${source.repo}/raw/branch/${defaultBranch}`;

    let packageName = '';
    for (const path of COMMON_ANDROID_PATHS) {
        const content = await fetchTextIfExists(`${rawBase}/${path}`, {
            headers: githubHeaders
        });
        if (!content) continue;
        const detectedPackage = extractPackageName(content);
        if (detectedPackage) {
            packageName = detectedPackage;
            break;
        }
    }

    return {
        name: humanizeRepoName(source.repo),
        description: description || undefined,
        packageName: packageName || undefined,
        author: source.owner || undefined
    };
};

const SubmissionModal: React.FC<SubmissionModalProps> = ({ onClose, currentStoreVersion, onSuccess, submissionCount = 0, activeTab }) => {
  useScrollLock(true);
  const githubToken = useSettingsStore((state) => state.githubToken);

  // --- DRAFT PERSISTENCE KEY ---
  const DRAFT_KEY = 'orion_submission_draft';

  // Restore draft from sessionStorage on mount
  const loadDraft = () => {
    try {
      const raw = sessionStorage.getItem(DRAFT_KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) {}
    return null;
  };
  const savedDraft = loadDraft();

  // Default mode depends on context. Android -> Obtainium by default. PC/TV -> Manual.
  const [mode, setMode] = useState<'obtainium' | 'manual'>(savedDraft?.mode || (activeTab === 'android' ? 'obtainium' : 'manual'));
  const [jsonInput, setJsonInput] = useState(savedDraft?.jsonInput || '');
  const [error, setError] = useState('');
  const [activeHelpText, setActiveHelpText] = useState<string | null>(null);

  // Screenshot Management
  const [screenshotInput, setScreenshotInput] = useState('');
  const [addedScreenshots, setAddedScreenshots] = useState<string[]>(savedDraft?.addedScreenshots || []);

  // Obtainium Mode Overrides
  const [obtainiumIcon, setObtainiumIcon] = useState(savedDraft?.obtainiumIcon || '');
  const [obtainiumKeyword, setObtainiumKeyword] = useState(savedDraft?.obtainiumKeyword || '');
  const [obtainiumDescription, setObtainiumDescription] = useState(savedDraft?.obtainiumDescription || '');

  // Manual Form State
  type FormDataType = {
    name: string; id: string; description: string; icon: string; repoUrl: string;
    githubRepo: string; gitlabRepo: string; gitlabDomain: string; codebergRepo: string;
    releaseKeyword: string; packageName: string; category: string; author: string; officialSite: string;
  };
  const [isManualKeyword, setIsManualKeyword] = useState(savedDraft?.isManualKeyword || false);
  const [formData, setFormData] = useState<FormDataType>(savedDraft?.formData || {
    name: '',
    id: '', 
    description: '',
    icon: '',
    repoUrl: '',
    githubRepo: '',
    gitlabRepo: '',
    gitlabDomain: '',
    codebergRepo: '',
    releaseKeyword: 'apk',
    packageName: '',
    category: AppCategory.UTILITY,
    author: '',
    officialSite: '', 
  });
  const [autoFilledPackageName, setAutoFilledPackageName] = useState(savedDraft?.autoFilledPackageName || '');
  const [autoFilledDescription, setAutoFilledDescription] = useState(savedDraft?.autoFilledDescription || '');
  const [autoFilledName, setAutoFilledName] = useState(savedDraft?.autoFilledName || '');
  const [repoAutofillState, setRepoAutofillState] = useState<{ status: RepoDetectionStatus; message: string }>(
    savedDraft?.repoAutofillState || { status: 'idle', message: '' }
  );
  const lastAutofillRepo = useRef('');
  const [issueUrlToOpen, setIssueUrlToOpen] = useState<string | null>(null);

  // Save draft to sessionStorage on key state changes
  useEffect(() => {
    try {
      sessionStorage.setItem(DRAFT_KEY, JSON.stringify({
        mode, jsonInput, addedScreenshots, obtainiumIcon, obtainiumKeyword,
        obtainiumDescription, isManualKeyword, formData,
        autoFilledPackageName, autoFilledDescription, autoFilledName, repoAutofillState
      }));
    } catch (e) {}
  }, [mode, jsonInput, addedScreenshots, obtainiumIcon, obtainiumKeyword, obtainiumDescription, isManualKeyword, formData, autoFilledPackageName, autoFilledDescription, autoFilledName, repoAutofillState]);

  const clearDraft = () => { try { sessionStorage.removeItem(DRAFT_KEY); } catch (e) {} };

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

  // Helper to fix GitHub blob links (Auto-convert to raw)
  const sanitizeGitHubImageUrl = (url: string) => {
      if (!url) return '';
      try {
          const trimmed = url.trim();
          if (!trimmed.startsWith('http')) return trimmed;
          
          const urlObj = new URL(trimmed);
          // Check for github.com (or www.github.com)
          if (urlObj.hostname.endsWith('github.com')) {
              const segments = urlObj.pathname.split('/').filter(Boolean);
              // Expected: [user, repo, blob, ref, ...path]
              if (segments.length >= 4 && segments[2] === 'blob') {
                  segments.splice(2, 1); // Remove 'blob'
                  return `https://raw.githubusercontent.com/${segments.join('/')}`;
              }
          }
      } catch (e) { return url; }
      return url;
  };

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
                const urlParts = formData.repoUrl.split('github.com/');
                if (urlParts.length > 1 && urlParts[1]) {
                    const parts = urlParts[1].split('/');
                    if (parts.length >= 2) {
                        const owner = parts[0];
                        // FIX: Ensure parts[1] is treated as a string even if TS doubts it
                        const repoPart = parts[1] || '';
                        const repo = repoPart.replace('.git', '').replace(/\/$/, '');
                        setFormData(prev => ({ 
                            ...prev, 
                            githubRepo: `${owner}/${repo}`,
                            gitlabRepo: '',
                            gitlabDomain: '',
                            codebergRepo: '',
                            author: prev.author || owner || ''
                        }));
                    }
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
                        codebergRepo: '',
                        author: prev.author || pathParts[0] || ''
                    }));
                }
            } catch (e) {}
        }
        // Codeberg Parsing
        else if (formData.repoUrl.includes('codeberg.org')) {
            try {
                const urlObj = new URL(formData.repoUrl);
                const pathParts = urlObj.pathname.split('/').filter(p => p);
                if (pathParts.length >= 2) {
                    setFormData(prev => ({
                        ...prev,
                        githubRepo: '',
                        gitlabRepo: '',
                        gitlabDomain: '',
                        codebergRepo: pathParts.join('/'),
                        author: prev.author || pathParts[0] || ''
                    }));
                }
            } catch (e) {}
        }
    }
  }, [formData.repoUrl, activeTab]);

  const repoSource = useMemo(() => {
      if (activeTab !== 'android' || mode !== 'manual' || !formData.repoUrl.trim()) return null;
      return parseRepoSource(formData.repoUrl);
  }, [activeTab, mode, formData.repoUrl]);

  useEffect(() => {
      if (activeTab !== 'android' || mode !== 'manual') return;
      if (!repoSource) {
          lastAutofillRepo.current = '';
          setRepoAutofillState({ status: 'idle', message: '' });
          return;
      }

      const repoKey = `${repoSource.provider}:${repoSource.domain || ''}:${repoSource.repoPath}`;
      if (lastAutofillRepo.current === repoKey) return;

      let cancelled = false;
      setRepoAutofillState({ status: 'loading', message: 'Detecting description and package name from the repo...' });

      const timer = window.setTimeout(async () => {
          try {
              const detected = await detectRepoAutofill(repoSource, githubToken);
              if (cancelled) return;

              lastAutofillRepo.current = repoKey;

              setFormData((prev) => {
                  const next = { ...prev };
                  if (detected.author && !prev.author) next.author = detected.author;
                  if (detected.name && (!prev.name || prev.name === autoFilledName)) {
                      next.name = detected.name;
                  }
                  if (detected.description && (!prev.description || prev.description === autoFilledDescription)) {
                      next.description = detected.description;
                  }
                  if (detected.packageName && (!prev.packageName || prev.packageName === autoFilledPackageName)) {
                      next.packageName = detected.packageName;
                  }
                  return next;
              });

              if (detected.name) setAutoFilledName(detected.name);
              if (detected.description) setAutoFilledDescription(detected.description);
              if (detected.packageName) setAutoFilledPackageName(detected.packageName);

              const statusMessage = detected.name && detected.packageName && detected.description
                  ? 'App name, package name, and description detected automatically.'
                  : detected.name && detected.packageName
                      ? 'App name and package name detected automatically.'
                  : detected.packageName
                      ? 'Package name detected. Add a custom description if needed.'
                      : detected.name
                          ? 'App name detected. Orion still needs Android metadata.'
                      : detected.description
                          ? 'Description detected. Package name still needs confirmation.'
                          : 'Repo found, but Orion could not detect Android metadata.';

              setRepoAutofillState({
                  status: detected.packageName || detected.description ? 'success' : 'error',
                  message: statusMessage
              });
          } catch (error) {
              if (cancelled) return;
              setRepoAutofillState({ status: 'error', message: 'Could not auto-detect repo metadata right now.' });
          }
      }, 650);

      return () => {
          cancelled = true;
          window.clearTimeout(timer);
      };
  }, [activeTab, mode, repoSource, githubToken, autoFilledDescription, autoFilledName, autoFilledPackageName]);

  // Derived Releases URL for Manual Mode Helper
  const releasesUrl = useMemo(() => {
      const url = formData.repoUrl;
      if (!url) return null;
      const clean = url.trim().replace(/\/$/, '');
      if (clean.includes('github.com')) return `${clean}/releases`;
      if (clean.includes('gitlab')) return `${clean}/-/releases`;
      if (clean.includes('codeberg.org')) return `${clean}/releases`;
      return null;
  }, [formData.repoUrl]);

  const releaseKeywordTooltip = `
1. Click the 'Check Releases' link below.
2. Go to the 'Assets' section of the latest version.
3. Look at the APK filename.
4. Copy a unique word from it.

EXAMPLE:
File: "app-release-arm64.apk"
Keyword: "app-release"

If the file is just "app.apk", simply use "apk".
  `.trim();

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
          // Automatically sanitize GitHub URLs
          const fixedUrl = sanitizeGitHubImageUrl(screenshotInput.trim());
          setAddedScreenshots([...addedScreenshots, fixedUrl]);
          setScreenshotInput('');
      }
  };

  const handleRemoveScreenshot = (index: number) => {
      setAddedScreenshots(prev => prev.filter((_, i) => i !== index));
  };

  const isValidRepoUrl = (url: string) => {
      if (!url) return false;
      const lower = url.toLowerCase();
      return lower.includes('github.com') || lower.includes('gitlab') || lower.includes('codeberg.org');
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
                  let codebergPath = undefined;
                  
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
                              // FIX: Provide fallbacks for potentially undefined array items
                              owner = pathParts[0] || 'unknown';
                              repo = pathParts[pathParts.length - 1] || 'unknown';
                              gitlabPath = pathParts.join('/');
                              gitlabDomain = urlObj.hostname;
                          }
                      } catch(e) {}
                  } else if (item.url.includes('codeberg.org')) {
                      // Codeberg Parsing Logic
                      try {
                          const urlObj = new URL(item.url);
                          const pathParts = urlObj.pathname.split('/').filter((p: string) => p);
                          if (pathParts.length >= 2) {
                              owner = pathParts[0] || 'unknown';
                              repo = pathParts[pathParts.length - 1] || 'unknown';
                              codebergPath = pathParts.join('/');
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
                      icon: sanitizeGitHubImageUrl(obtainiumIcon), 
                      version: "Latest",
                      latestVersion: "Latest",
                      downloadUrl: "#",
                      repoUrl: item.url,
                      githubRepo: githubPath,
                      gitlabRepo: gitlabPath,
                      gitlabDomain: gitlabDomain,
                      codebergRepo: codebergPath,
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
                  if (!isValidRepoUrl(formData.repoUrl)) { setError("Only GitHub, GitLab, or Codeberg repositories are allowed."); return; }
                  if (!formData.packageName) { setError("Package Name is required."); return; }
                  if (isManualKeyword && !formData.releaseKeyword) { setError("Release Keyword is required when manual mode is on."); return; }
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
                  icon: sanitizeGitHubImageUrl(formData.icon),
                  version: "Latest", 
                  latestVersion: "Latest",
                  downloadUrl: formData.officialSite || "#", 
                  repoUrl: formData.repoUrl,
                  githubRepo: formData.githubRepo || undefined,
                  gitlabRepo: formData.gitlabRepo || undefined,
                  gitlabDomain: formData.gitlabDomain || undefined,
                  codebergRepo: formData.codebergRepo || undefined,
                  releaseKeyword: isManualKeyword ? formData.releaseKeyword : 'apk',
                  packageName: formData.packageName,
                  category: formData.category,
                  platform: platform,
                  size: "Varies",
                  author: formData.author,
                  officialSite: formData.officialSite,
                  screenshots: addedScreenshots
              }];
          }

          const url = generateIssueUrl(appsToSubmit);
          if (onSuccess) onSuccess();
          setIssueUrlToOpen(url);

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
                          <img src={url} alt={`Screenshot ${idx + 1}`} className="w-8 h-8 rounded object-cover bg-theme-element" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
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
      setAutoFilledName('');
      setIssueUrlToOpen(null);
      clearDraft();
  };

  const isAndroid = activeTab === 'android';

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 animate-fade-in">
        <div className="backdrop-scrim absolute inset-0 bg-black/80 backdrop-blur-md touch-none" onClick={onClose}></div>
        
        {/* Main Content Modal */}
        <div className="bg-surface border border-theme-border rounded-3xl p-0 w-full max-w-lg relative z-10 animate-slide-up shadow-2xl overflow-hidden flex flex-col max-h-[90vh] compact-allow">
            
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

            {/* Scrollable Content - Added overscroll-contain */}
            <div className="overflow-y-auto p-6 flex-1 no-scrollbar overscroll-contain">
                
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
                                    {formData.repoUrl.trim() && (
                                        <div className={`mt-2 flex items-center gap-2 rounded-xl border px-3 py-2 text-[10px] font-bold ${
                                            repoAutofillState.status === 'error'
                                                ? 'border-red-500/20 bg-red-500/10 text-red-500'
                                                : repoAutofillState.status === 'success'
                                                    ? 'border-green-500/20 bg-green-500/10 text-green-600'
                                                    : 'border-theme-border bg-theme-element/50 text-theme-sub'
                                        }`}>
                                            <i className={`fas ${
                                                repoAutofillState.status === 'loading'
                                                    ? 'fa-spinner fa-spin'
                                                    : repoAutofillState.status === 'success'
                                                        ? 'fa-check-circle'
                                                        : repoAutofillState.status === 'error'
                                                            ? 'fa-triangle-exclamation'
                                                            : 'fa-wand-magic-sparkles'
                                            }`}></i>
                                            <span>
                                                {repoAutofillState.message || 'Orion will try to detect the package name and description from this repo.'}
                                            </span>
                                        </div>
                                    )}
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
                                    {formData.codebergRepo && (
                                        <p className="text-[10px] text-blue-500 mt-1 font-mono flex items-center gap-1">
                                            <svg viewBox="0 0 512 512" fill="currentColor" className="w-3 h-3">
                                                <path d="M256 48l240 416H16L256 48zm0 128L96 400h320L256 176z" />
                                            </svg>
                                            Codeberg: {formData.codebergRepo}
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

                        {/* Release Keyword & Package Name (New Mandatory Fields for Android) */}
                        {isAndroid && (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                                <div>
                                    <LabelWithTooltip 
                                        label="Package Name" 
                                        tooltip="The Android package identifier (e.g. com.example.app)."
                                        required 
                                        onHelp={setActiveHelpText}
                                    />
                                    <input 
                                        type="text"
                                        className="w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none font-mono"
                                        placeholder="com.example.app"
                                        value={formData.packageName}
                                        onChange={(e) => handleInputChange('packageName', e.target.value)}
                                    />
                                </div>
                                <div>
                                    <div className="flex items-center justify-between mb-1.5">
                                        <LabelWithTooltip 
                                            label="Release Keyword" 
                                            tooltip={releaseKeywordTooltip} 
                                            required
                                            onHelp={setActiveHelpText} 
                                        />
                                        <div className="flex items-center gap-2">
                                            <span className="text-[10px] font-bold text-theme-sub uppercase">Manual</span>
                                            <button 
                                                type="button"
                                                onClick={() => setIsManualKeyword(!isManualKeyword)}
                                                className={`relative h-4 w-8 overflow-hidden rounded-full transition-colors ${isManualKeyword ? 'bg-primary' : 'bg-theme-border'}`}
                                                aria-pressed={isManualKeyword}
                                            >
                                                <div className={`absolute left-0.5 top-0.5 h-3 w-3 rounded-full bg-white shadow-sm transition-transform ${isManualKeyword ? 'translate-x-4' : 'translate-x-0'}`}></div>
                                            </button>
                                        </div>
                                    </div>
                                    <input 
                                        type="text"
                                        className={`w-full bg-theme-input border border-theme-border rounded-xl px-3 py-3 text-sm focus:border-primary outline-none ${!isManualKeyword ? 'opacity-50 cursor-not-allowed' : ''}`}
                                        placeholder="e.g. arm64, universal"
                                        value={isManualKeyword ? formData.releaseKeyword : 'apk'}
                                        onChange={(e) => handleInputChange('releaseKeyword', e.target.value)}
                                        disabled={!isManualKeyword}
                                    />
                                </div>
                            </div>
                        )}
                        {isAndroid && releasesUrl && (
                             <a 
                                href={releasesUrl} 
                                target="_blank" 
                                rel="noreferrer"
                                className="inline-flex items-center gap-1 text-[10px] font-bold text-primary hover:underline bg-primary/5 px-2 py-1 rounded-lg border border-primary/10 transition-colors hover:bg-primary/10 mb-2"
                            >
                                <i className="fas fa-external-link-alt"></i>
                                Check Releases Page
                            </a>
                        )}

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
                    <p className="text-sm font-medium whitespace-pre-wrap leading-relaxed">{activeHelpText}</p>
                    <button onClick={() => setActiveHelpText(null)} className="mt-4 text-xs font-bold text-theme-sub uppercase tracking-widest hover:text-white">Close</button>
                </div>
            </div>
        )}

        {issueUrlToOpen && (
            <div className="absolute inset-0 z-[80] flex items-center justify-center p-6 bg-black/70 animate-fade-in" onClick={() => setIssueUrlToOpen(null)}>
                <div className="w-full max-w-sm rounded-[2rem] border border-theme-border bg-surface p-6 shadow-2xl animate-slide-up" onClick={(e) => e.stopPropagation()}>
                    <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                        <i className="fas fa-triangle-exclamation text-xl"></i>
                    </div>
                    <h3 className="text-center text-xl font-black text-theme-text">Before You Continue</h3>
                    <p className="mt-3 text-center text-sm leading-relaxed text-theme-sub">
                        Orion already generated the correct JSON for your submission. Please do not manually edit the JSON block on the GitHub issue page, or the submission may break.
                    </p>
                    <div className="mt-5 flex flex-col gap-2">
                        <button
                            onClick={() => {
                                window.open(issueUrlToOpen, '_blank');
                                clearDraft();
                                setIssueUrlToOpen(null);
                                onClose();
                            }}
                            className="w-full rounded-2xl bg-primary px-4 py-3 font-bold text-white shadow-lg shadow-primary/20 transition-all active:scale-95"
                        >
                            Open GitHub Issue
                        </button>
                        <button
                            onClick={() => setIssueUrlToOpen(null)}
                            className="w-full rounded-2xl bg-theme-element px-4 py-3 text-sm font-bold text-theme-text transition-colors hover:bg-theme-hover"
                        >
                            Go Back
                        </button>
                    </div>
                </div>
            </div>
        )}
    </div>
  );
};

export default SubmissionModal;
