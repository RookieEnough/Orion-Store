import React, { memo, useCallback, useEffect, useMemo, useRef, useState, useTransition } from 'react';
import { AppCategory, Platform, Tab } from '../types';
import { useScrollLock } from '../hooks/useScrollLock';
import { useSettingsStore } from '../store/useAppStore';
import { explainRejectedImageLink } from '../utils/imageLink';

interface SubmissionModalProps {
    onClose: () => void;
    currentStoreVersion: string;
    onSuccess?: () => void;
    submissionCount?: number;
    activeTab: Tab;
}

const InlineHelp: React.FC<{ text: string; label?: string }> = memo(({ text, label }) => {
    const [open, setOpen] = useState(false);
    const [sticky, setSticky] = useState(false);
    const rootRef = useRef<HTMLSpanElement | null>(null);

    useEffect(() => {
        if (!open) return;
        const onPointerDown = (e: PointerEvent) => {
            const root = rootRef.current;
            if (!root) return;
            if (root.contains(e.target as Node)) return;
            setOpen(false);
            setSticky(false);
        };
        document.addEventListener('pointerdown', onPointerDown);
        return () => document.removeEventListener('pointerdown', onPointerDown);
    }, [open]);

    const show = () => {
        if (!sticky) setOpen(true);
    };

    const hide = () => {
        if (!sticky) setOpen(false);
    };

    const toggleSticky = () => {
        setOpen((prev) => !prev || !sticky);
        setSticky((prev) => !prev);
    };

    return (
        <span
            ref={rootRef}
            className="relative ml-1 inline-flex items-center"
            onMouseEnter={show}
            onMouseLeave={hide}
        >
            <button
                type="button"
                onClick={toggleSticky}
                className="inline-flex h-3.5 w-3.5 items-center justify-center rounded-full text-[9px] text-slate-400 transition hover:text-slate-200 focus:outline-none"
                aria-label={label ? `Help: ${label}` : 'Help'}
            >
                <i className="fas fa-circle-info"></i>
            </button>
            {open && (
                <span className="absolute left-1/2 top-full z-30 mt-2 w-56 -translate-x-1/2 rounded-xl border border-white/10 bg-[#1e1f2c] px-3 py-2 text-[11px] font-medium normal-case leading-relaxed text-slate-300 shadow-2xl">
                    {text}
                </span>
            )}
        </span>
    );
});

// A label without the info button
const SimpleLabel: React.FC<{ label: string; required?: boolean }> = ({ label, required }) => (
    <label className="mb-1.5 flex items-center text-[10px] font-bold uppercase tracking-wide text-slate-400">
        {label}
        {required && <span className="ml-0.5 text-red-400">*</span>}
    </label>
);

const LabelWithTooltip: React.FC<{
    label: string;
    tooltip: string;
    required?: boolean;
}> = memo(({ label, tooltip, required }) => (
    <label className="mb-1.5 flex items-center text-[10px] font-bold uppercase tracking-wide text-slate-400">
        {label}
        {required && <span className="ml-0.5 text-red-400">*</span>}
        <InlineHelp text={tooltip} label={label} />
    </label>
));

const Section: React.FC<React.PropsWithChildren<{ title?: string; subtitle?: string; className?: string; innerClassName?: string }>> = memo(({
    title,
    subtitle,
    className = '',
    innerClassName = '',
    children
}) => (
    <div className={`mb-5 ${className}`}>
        {(title || subtitle) && (
            <div className="mb-2">
                {title && <h4 className="text-[13px] font-black tracking-tight text-white">{title}</h4>}
                {subtitle && <p className="mt-0.5 text-[10px] leading-relaxed text-slate-400">{subtitle}</p>}
            </div>
        )}
        <div className={innerClassName}>{children}</div>
    </div>
));

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

    const DRAFT_KEY = 'orion_submission_draft';

    const loadDraft = () => {
        try {
            const raw = sessionStorage.getItem(DRAFT_KEY);
            if (raw) return JSON.parse(raw);
        } catch (e) { }
        return null;
    };
    const savedDraft = loadDraft();

    const [mode, setMode] = useState<'obtainium' | 'manual'>(savedDraft?.mode || (activeTab === 'android' ? 'obtainium' : 'manual'));
    const [jsonInput, setJsonInput] = useState(savedDraft?.jsonInput || '');
    const [error, setError] = useState('');
    const [, startTransition] = useTransition();
    const [screenshotInput, setScreenshotInput] = useState('');
    const [addedScreenshots, setAddedScreenshots] = useState<string[]>(savedDraft?.addedScreenshots || []);
    const [obtainiumIcon, setObtainiumIcon] = useState(savedDraft?.obtainiumIcon || '');
    const [obtainiumKeyword, setObtainiumKeyword] = useState(savedDraft?.obtainiumKeyword || '');
    const [obtainiumDescription, setObtainiumDescription] = useState(savedDraft?.obtainiumDescription || '');

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

    useEffect(() => {
        try {
            sessionStorage.setItem(DRAFT_KEY, JSON.stringify({
                mode, jsonInput, addedScreenshots, obtainiumIcon, obtainiumKeyword,
                obtainiumDescription, isManualKeyword, formData,
                autoFilledPackageName, autoFilledDescription, autoFilledName, repoAutofillState
            }));
        } catch (e) { }
    }, [mode, jsonInput, addedScreenshots, obtainiumIcon, obtainiumKeyword, obtainiumDescription, isManualKeyword, formData, autoFilledPackageName, autoFilledDescription, autoFilledName, repoAutofillState]);

    const clearDraft = () => { try { sessionStorage.removeItem(DRAFT_KEY); } catch (e) { } };

    const baseCooldown = 180;
    const reductionPerSub = 15;
    const maxReduction = 150;
    const currentReduction = Math.min(submissionCount * reductionPerSub, maxReduction);
    const currentCooldown = baseCooldown - currentReduction;
    const currentLevel = submissionCount;

    const getRankInfo = (level: number) => {
        if (level >= 10) return { title: 'Elite', color: 'text-green-400', bg: 'bg-acid/20', icon: 'fa-crown' };
        if (level >= 5) return { title: 'Expert', color: 'text-green-400', bg: 'bg-purple-500/20', icon: 'fa-star' };
        if (level >= 1) return { title: 'Contributor', color: 'text-green-400', bg: 'bg-blue-500/20', icon: 'fa-shield-alt' };
        return { title: 'Newcomer', color: 'text-green-400', bg: 'bg-white/5', icon: 'fa-user' };
    };

    const rank = getRankInfo(currentLevel);

    const sanitizeGitHubImageUrl = (url: string) => {
        if (!url) return '';
        try {
            const trimmed = url.trim();
            if (!trimmed.startsWith('http')) return trimmed;
            const urlObj = new URL(trimmed);
            if (urlObj.hostname.endsWith('github.com')) {
                const segments = urlObj.pathname.split('/').filter(Boolean);
                if (segments.length >= 4 && segments[2] === 'blob') {
                    segments.splice(2, 1);
                    return `https://raw.githubusercontent.com/${segments.join('/')}`;
                }
            }
        } catch (e) { return url; }
        return url;
    };

    useEffect(() => {
        if (formData.name) {
            const generatedId = formData.name.toLowerCase().replace(/[^a-z0-9]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
            setFormData(prev => ({ ...prev, id: generatedId }));
        }
    }, [formData.name]);

    useEffect(() => {
        if (activeTab === 'android' && formData.repoUrl) {
            if (formData.repoUrl.includes('github.com')) {
                try {
                    const urlParts = formData.repoUrl.split('github.com/');
                    if (urlParts.length > 1 && urlParts[1]) {
                        const parts = urlParts[1].split('/');
                        if (parts.length >= 2) {
                            const owner = parts[0];
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
                } catch (e) { }
            } else if (formData.repoUrl.includes('gitlab')) {
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
                } catch (e) { }
            } else if (formData.repoUrl.includes('codeberg.org')) {
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
                } catch (e) { }
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
                    if (detected.name && (!prev.name || prev.name === autoFilledName)) next.name = detected.name;
                    if (detected.description && (!prev.description || prev.description === autoFilledDescription)) next.description = detected.description;
                    if (detected.packageName && (!prev.packageName || prev.packageName === autoFilledPackageName)) next.packageName = detected.packageName;
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
                setError('Screenshot must be a valid URL starting with http/https');
                setTimeout(() => setError(''), 3000);
                return;
            }
            const fixedUrl = sanitizeGitHubImageUrl(screenshotInput.trim());
            setAddedScreenshots(prev => [...prev, fixedUrl]);
            setScreenshotInput('');
        }
    };

    const handleRemoveScreenshot = (index: number) => {
        setAddedScreenshots(prev => prev.filter((_, i) => i !== index));
    };

    const generateIssueUrl = useCallback((appsToSubmit: any[]) => {
        const title = encodeURIComponent(`[Submission] ${appsToSubmit[0]?.name || 'New app'}`);
        const body = encodeURIComponent(JSON.stringify(appsToSubmit, null, 2));
        return `https://github.com/RookieEnough/Orion-Data/issues/new?title=${title}&body=${body}`;
    }, []);

    const handleSubmit = useCallback(() => {
        setError('');
        if (addedScreenshots.length < 3) {
            setError('At least 3 screenshots are required.');
            return;
        }
        for (let i = 0; i < addedScreenshots.length; i++) {
            const screenshot = addedScreenshots[i];
            if (!screenshot) continue;
            const reason = explainRejectedImageLink(screenshot);
            if (reason) {
                setError(`Screenshot #${i + 1}: ${reason}`);
                return;
            }
        }

        let appsToSubmit: any[] = [];
        try {
            if (mode === 'obtainium' && activeTab === 'android') {
                if (!jsonInput.trim()) { setError('Please paste JSON content.'); return; }
                if (!obtainiumIcon.trim()) { setError('Icon URL is required.'); return; }
                const obtainiumIconReason = explainRejectedImageLink(obtainiumIcon);
                if (obtainiumIconReason) { setError(`Icon: ${obtainiumIconReason}`); return; }
                const parsed = JSON.parse(jsonInput);
                appsToSubmit = [{
                    ...parsed,
                    icon: sanitizeGitHubImageUrl(obtainiumIcon.trim()),
                    releaseKeyword: obtainiumKeyword.trim() || 'apk',
                    description: obtainiumDescription.trim(),
                    screenshots: addedScreenshots
                }];
            } else {
                if (!formData.name.trim()) { setError('App name is required.'); return; }
                if (!formData.description.trim()) { setError('Description is required.'); return; }
                if (!formData.icon.trim()) { setError('Icon URL is required.'); return; }
                const iconReason = explainRejectedImageLink(formData.icon);
                if (iconReason) { setError(`Icon: ${iconReason}`); return; }
                if (activeTab === 'android') {
                    if (!formData.repoUrl.trim()) { setError('Repo URL is required.'); return; }
                    if (!formData.packageName.trim()) { setError('Package name is required.'); return; }
                } else if (!formData.officialSite.trim()) {
                    setError('Official website / repo link is required.');
                    return;
                }

                appsToSubmit = [{
                    ...formData,
                    icon: sanitizeGitHubImageUrl(formData.icon.trim()),
                    releaseKeyword: activeTab === 'android' ? (isManualKeyword ? formData.releaseKeyword.trim() || 'apk' : 'apk') : formData.releaseKeyword,
                    screenshots: addedScreenshots,
                    platform: activeTab
                }];
            }

            const issueUrl = generateIssueUrl(appsToSubmit);
            startTransition(() => setIssueUrlToOpen(issueUrl));
            onSuccess?.();
        } catch (e) {
            setError('Failed to parse data. Please check inputs.');
        }
    }, [activeTab, addedScreenshots, formData, generateIssueUrl, isManualKeyword, jsonInput, mode, obtainiumDescription, obtainiumIcon, obtainiumKeyword, onSuccess, startTransition]);

    const renderScreenshotSection = () => (
        <div className="mt-1">
            <div className="mb-3 flex items-center justify-between">
                <LabelWithTooltip
                    label="Screenshots (Min 3)"
                    tooltip="Add direct image URLs. Copy links from the repo's Readme, Play Store, or F-Droid."
                    required
                />
                <span className="rounded-full bg-white/10 px-2 py-0.5 text-[10px] font-bold text-slate-300">
                    {addedScreenshots.length}/3+
                </span>
            </div>
            <div className="mb-4 flex gap-2">
                <input
                    type="text"
                    className="h-9 flex-1 rounded-xl border border-white/5 bg-[#2d3147] px-3 text-sm text-slate-100 outline-none placeholder:text-slate-500 focus:border-primary/70"
                    placeholder="https://image.url/screenshot.jpg"
                    value={screenshotInput}
                    onChange={(e) => setScreenshotInput(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleAddScreenshot()}
                />
                <button
                    onClick={handleAddScreenshot}
                    className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10 text-slate-200 transition hover:bg-white/20"
                >
                    <i className="fas fa-plus text-sm"></i>
                </button>
            </div>

            <div className="grid grid-cols-4 gap-1.5 max-w-xs mx-auto">
                {addedScreenshots.map((url, idx) => (
                    <div key={idx} className="relative aspect-[9/19] overflow-hidden rounded-xl bg-[#2d3147]">
                        <img
                            src={url}
                            alt={`Screenshot ${idx + 1}`}
                            className="h-full w-full object-cover"
                            onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                        />
                        <div className="absolute inset-x-0 top-0 h-8 bg-gradient-to-b from-black/40 to-transparent" />
                        <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent px-1.5 pb-1 pt-4">
                            <span className="text-[9px] font-bold text-white/90">#{idx + 1}</span>
                        </div>
                        <button
                            onClick={() => handleRemoveScreenshot(idx)}
                            className="absolute right-1 top-1 flex h-4 w-4 items-center justify-center rounded-full bg-black/60 text-white backdrop-blur transition hover:bg-red-500/80"
                        >
                            <i className="fas fa-times text-[8px]"></i>
                        </button>
                    </div>
                ))}

                {Array.from({ length: Math.max(4 - addedScreenshots.length, 1) }).map((_, num) => {
                    const displayNum = addedScreenshots.length + num + 1;
                    return (
                        <div key={`empty-${num}`} className="relative flex aspect-[9/19] items-center justify-center rounded-xl border border-dashed border-white/10 bg-[#2d3147]/20">
                            <div className="text-center">
                                <i className="fas fa-image mb-1 block text-sm text-slate-600"></i>
                                <span className="text-[10px] font-medium text-slate-600">{displayNum}</span>
                            </div>
                        </div>
                    );
                })}
            </div>
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
        <div className="fixed inset-0 z-[60] animate-fade-in bg-[#232634] text-white">
            <div className="flex h-full w-full flex-col overflow-y-auto">
                <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 pb-5 pt-[calc(1rem+env(safe-area-inset-top))] sm:px-5">
                    {/* Header */}
                    <div className="mb-5 flex items-start justify-between">
                        <div className="flex items-center gap-3">
                            <div className="flex items-center justify-center">
                                {activeTab === 'android' ? (
                                    <i className="fab fa-android text-3xl text-emerald-400 drop-shadow-md"></i>
                                ) : activeTab === 'pc' ? (
                                    <i className="fab fa-windows text-3xl text-blue-500 drop-shadow-md"></i>
                                ) : (
                                    <i className="fas fa-tv text-3xl text-purple-400 drop-shadow-md"></i>
                                )}
                            </div>
                            <div>
                                <h3 className="text-xl font-black leading-tight tracking-tight text-white">Submit {activeTab === 'android' ? 'App' : activeTab === 'tv' ? 'TV App' : 'Software'}</h3>
                                <div className="mt-1.5 flex flex-wrap gap-2">
                                    <div className={`flex items-center gap-1.5 rounded-full ${rank.bg} px-2 py-0.5 text-[10px] font-bold ${rank.color}`}>
                                        <i className={`fas ${rank.icon} text-xs`}></i>
                                        <span>{rank.title}</span>
                                    </div>
                                    <div className="flex items-center gap-1.5 rounded-full bg-white/5 px-2 py-0.5 text-[10px] font-bold text-slate-300">
                                        <i className="fas fa-clock text-[10px] text-blue-400"></i>
                                        <span>{Math.floor(currentCooldown / 60)}h {currentCooldown % 60}m</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <button onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-full bg-white/5 text-slate-300 transition hover:bg-white/10">
                            <i className="fas fa-times text-xs"></i>
                        </button>
                    </div>

                    <div className="no-scrollbar">
                        {isAndroid ? (
                            <div className="mb-6 rounded-xl bg-white/5 p-1">
                                <div className="grid grid-cols-2 gap-1">
                                    <button
                                        onClick={() => { setMode('obtainium'); resetForm(); }}
                                        className={`flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-xs font-bold transition-all ${mode === 'obtainium' ? 'bg-white/10 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'}`}
                                    >
                                        <i className="fas fa-file-import text-xs"></i>
                                        <span>Obtainium</span>
                                    </button>
                                    <button
                                        onClick={() => { setMode('manual'); resetForm(); }}
                                        className={`flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-xs font-bold transition-all ${mode === 'manual' ? 'bg-white/10 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'}`}
                                    >
                                        <i className="fas fa-code-branch text-xs"></i>
                                        <span>Repo</span>
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div className="mb-6 rounded-xl bg-white/5 px-4 py-2 text-center text-xs font-bold text-slate-300">
                                Submitting request for <span className="uppercase text-white">{activeTab}</span>
                            </div>
                        )}

                        {error && (
                            <div className="mb-4 flex items-center gap-2 rounded-xl border border-red-500/30 bg-red-500/10 p-2.5 text-xs font-bold text-red-400">
                                <i className="fas fa-exclamation-circle"></i>
                                <span>{error}</span>
                            </div>
                        )}

                        {mode === 'obtainium' && isAndroid ? (
                            <div className="space-y-5">
                                <Section title="Obtainium import" subtitle="Paste your export and add the Orion-specific metadata needed for review.">
                                    <textarea
                                        className="h-32 w-full resize-none rounded-xl bg-[#2d3147] p-3 text-xs font-mono text-slate-200 outline-none focus:ring-1 focus:ring-primary/50"
                                        placeholder='{"apps": [{"url": "https://gitlab.com/..."}]}'
                                        value={jsonInput}
                                        onChange={(e) => setJsonInput(e.target.value)}
                                    ></textarea>
                                </Section>

                                <Section title="Overrides" subtitle="Optional tweaks for how the submission appears in Orion.">
                                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                                        <div>
                                            <SimpleLabel label="Icon URL" required />
                                            <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                                <input
                                                    type="text"
                                                    className="w-full bg-transparent text-sm outline-none placeholder:text-slate-500"
                                                    placeholder="https://..."
                                                    value={obtainiumIcon}
                                                    onChange={(e) => setObtainiumIcon(e.target.value)}
                                                />
                                            </div>
                                        </div>
                                        <div>
                                            <SimpleLabel label="Release Keyword" />
                                            <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                                <input
                                                    type="text"
                                                    className="w-full bg-transparent text-sm outline-none placeholder:text-slate-500"
                                                    placeholder="e.g. app-release"
                                                    value={obtainiumKeyword}
                                                    onChange={(e) => setObtainiumKeyword(e.target.value)}
                                                />
                                            </div>
                                        </div>
                                    </div>
                                    <div className="mt-3">
                                        <SimpleLabel label="Description Override" />
                                        <textarea
                                            className="h-20 w-full resize-none rounded-xl bg-[#2d3147] px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-primary/50"
                                            placeholder="What does this app do?"
                                            value={obtainiumDescription}
                                            onChange={(e) => setObtainiumDescription(e.target.value)}
                                        />
                                    </div>
                                </Section>

                                <Section title="Media">
                                    {renderScreenshotSection()}
                                </Section>
                            </div>
                        ) : (
                            <div className="space-y-5">
                                <Section title="Source" innerClassName="space-y-3">
                                    {isAndroid ? (
                                        <>
                                            <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                                <input
                                                    type="text"
                                                    className="w-full bg-transparent text-sm outline-none placeholder:text-slate-500"
                                                    placeholder="https://github.com/owner/repo"
                                                    value={formData.repoUrl}
                                                    onChange={(e) => handleInputChange('repoUrl', e.target.value)}
                                                />
                                            </div>
                                            {formData.repoUrl.trim() && (
                                                <div className={`flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-medium ${repoAutofillState.status === 'error'
                                                    ? 'bg-red-500/10 text-red-300'
                                                    : repoAutofillState.status === 'success'
                                                        ? 'bg-emerald-500/10 text-emerald-200'
                                                        : 'bg-white/5 text-slate-300'
                                                    }`}>
                                                    <i className={`fas ${repoAutofillState.status === 'loading'
                                                        ? 'fa-spinner fa-spin'
                                                        : repoAutofillState.status === 'success'
                                                            ? 'fa-check-circle'
                                                            : repoAutofillState.status === 'error'
                                                                ? 'fa-triangle-exclamation'
                                                                : 'fa-wand-magic-sparkles'
                                                        } text-xs`}></i>
                                                    <span>{repoAutofillState.message || 'Orion will try to detect the package name and description from this repo.'}</span>
                                                </div>
                                            )}
                                        </>
                                    ) : (
                                        <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                            <input
                                                type="text"
                                                className="w-full bg-transparent text-sm outline-none placeholder:text-slate-500"
                                                placeholder="https://example.com/download"
                                                value={formData.officialSite}
                                                onChange={(e) => handleInputChange('officialSite', e.target.value)}
                                            />
                                        </div>
                                    )}
                                </Section>

                                <div className="space-y-4">
                                    <div>
                                        <SimpleLabel label="Name" required />
                                        <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                            <input
                                                type="text"
                                                className="w-full bg-transparent text-sm outline-none placeholder:text-slate-500"
                                                placeholder="App name"
                                                value={formData.name}
                                                onChange={(e) => handleInputChange('name', e.target.value)}
                                            />
                                        </div>
                                    </div>

                                    <div>
                                        <SimpleLabel label="Category" required />
                                        <div className="rounded-xl bg-[#2d3147] px-3 py-2 text-slate-200">
                                            <select
                                                className="w-full appearance-none bg-transparent text-sm outline-none"
                                                value={formData.category}
                                                onChange={(e) => handleInputChange('category', e.target.value)}
                                            >
                                                {Object.values(AppCategory).map(c => (
                                                    <option key={c} value={c} className="bg-[#2d3147] text-white">{c}</option>
                                                ))}
                                            </select>
                                        </div>
                                    </div>

                                    <div>
                                        <SimpleLabel label="Author" required />
                                        <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                            <input
                                                type="text"
                                                className="w-full bg-transparent text-sm outline-none placeholder:text-slate-500"
                                                placeholder="Developer / publisher"
                                                value={formData.author}
                                                onChange={(e) => handleInputChange('author', e.target.value)}
                                            />
                                        </div>
                                    </div>

                                    <div>
                                        <SimpleLabel label="APP ID" />
                                        <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                            <input
                                                type="text"
                                                className="w-full bg-transparent text-sm outline-none"
                                                value={formData.id}
                                                readOnly
                                            />
                                        </div>
                                    </div>
                                </div>

                                <Section title="Description" innerClassName="space-y-3">
                                    <textarea
                                        className="h-24 w-full resize-none rounded-xl bg-[#2d3147] px-3 py-2 text-sm text-slate-200 outline-none placeholder:text-slate-500 focus:ring-1 focus:ring-primary/50"
                                        placeholder="A short summary users will read first."
                                        value={formData.description}
                                        onChange={(e) => handleInputChange('description', e.target.value)}
                                    />
                                </Section>

                                <Section title="Icon URL" innerClassName="space-y-3">
                                    <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                        <input
                                            type="text"
                                            className="w-full bg-transparent text-sm outline-none placeholder:text-slate-500"
                                            placeholder="https://..."
                                            value={formData.icon}
                                            onChange={(e) => handleInputChange('icon', e.target.value)}
                                        />
                                    </div>
                                </Section>

                                {isAndroid && (
                                    <Section title="Release" innerClassName="space-y-3">
                                        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                                            <div>
                                                <SimpleLabel label="Package Name" required />
                                                <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                                    <input
                                                        type="text"
                                                        className="w-full bg-transparent text-sm outline-none placeholder:text-slate-500"
                                                        placeholder="com.example.app"
                                                        value={formData.packageName}
                                                        onChange={(e) => handleInputChange('packageName', e.target.value)}
                                                    />
                                                </div>
                                            </div>
                                            <div>
                                                <div className="mb-1.5 flex items-center justify-between">
                                                    <SimpleLabel label="Release Keyword" required />
                                                    <div className="flex items-center gap-2">
                                                        <span className="text-[10px] font-bold uppercase text-slate-400">Auto</span>
                                                        <button
                                                            type="button"
                                                            onClick={() => setIsManualKeyword(!isManualKeyword)}
                                                            className={`relative h-4 w-8 rounded-full transition-colors ${isManualKeyword ? 'bg-blue-500' : 'bg-white/10'}`}
                                                            aria-pressed={isManualKeyword}
                                                        >
                                                            <div className={`absolute top-0.5 h-3 w-3 rounded-full bg-white transition-transform ${isManualKeyword ? 'left-4' : 'left-0.5'}`}></div>
                                                        </button>
                                                        <span className="text-[10px] font-bold uppercase text-slate-400">Manual</span>
                                                    </div>
                                                </div>
                                                <div className="flex items-center gap-2 rounded-xl bg-[#2d3147] px-3 py-2 text-slate-300">
                                                    <input
                                                        type="text"
                                                        className={`w-full bg-transparent text-sm outline-none placeholder:text-slate-500 ${!isManualKeyword ? 'cursor-not-allowed opacity-60' : ''}`}
                                                        placeholder="apk"
                                                        value={isManualKeyword ? formData.releaseKeyword : 'apk'}
                                                        onChange={(e) => handleInputChange('releaseKeyword', e.target.value)}
                                                        disabled={!isManualKeyword}
                                                    />
                                                </div>
                                            </div>
                                        </div>
                                        {releasesUrl && (
                                            <a
                                                href={releasesUrl}
                                                target="_blank"
                                                rel="noreferrer"
                                                className="inline-flex w-fit items-center gap-1.5 rounded-full bg-white/5 px-3 py-1 text-xs font-medium text-slate-300 transition hover:bg-white/10"
                                            >
                                                <i className="fas fa-external-link-alt text-[10px]"></i>
                                                Check Releases Page
                                            </a>
                                        )}
                                    </Section>
                                )}

                                <Section title="Screenshots">
                                    {renderScreenshotSection()}
                                </Section>
                            </div>
                        )}
                    </div>

                    {/* Sticky footer */}
                    <div className="sticky bottom-0 mt-6 border-t border-white/5 bg-[#232634] pb-1 pt-3 outline-none">
                        <button
                            onClick={handleSubmit}
                            className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 py-3 text-sm font-bold text-white transition hover:bg-blue-700 active:scale-[0.99] focus:outline-none"
                        >
                            <i className="fas fa-arrow-right text-xs"></i>
                            <span>Generate GitHub Issue</span>
                        </button>
                        <p className="mt-2 text-center text-[10px] text-slate-500">
                            Opens GitHub Issues in a new tab.
                        </p>
                    </div>
                </div>
            </div>

            {/* Confirmation modal */}
            {issueUrlToOpen && (
                <div className="absolute inset-0 z-[80] flex items-center justify-center bg-black/70 p-6 animate-fade-in" onClick={() => setIssueUrlToOpen(null)}>
                    <div className="w-full max-w-sm rounded-2xl border border-white/10 bg-[#1e1f2c] p-5 shadow-2xl animate-slide-up" onClick={(e) => e.stopPropagation()}>
                        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-white/10 text-white">
                            <i className="fas fa-triangle-exclamation text-lg"></i>
                        </div>
                        <h3 className="text-center text-base font-black text-white">Before You Continue</h3>
                        <p className="mt-2 text-center text-xs leading-relaxed text-slate-300">
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
                                className="w-full rounded-xl bg-white/10 px-4 py-2.5 font-bold text-white transition hover:bg-white/20 active:scale-[0.99]"
                            >
                                Open GitHub Issue
                            </button>
                            <button
                                onClick={() => setIssueUrlToOpen(null)}
                                className="w-full rounded-xl bg-white/5 px-4 py-2.5 text-sm font-medium text-slate-300 transition hover:bg-white/10"
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