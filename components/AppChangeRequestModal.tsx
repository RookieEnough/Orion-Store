import React, { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { AppItem, Platform } from '../types';
import { getOptimizedImageUrl } from '../utils/image';
import { useScrollLock } from '../hooks/useScrollLock';
import { AppChangeField, buildAppUpdateIssueUrl, buildAppUpdatePayload } from '../utils/appChangeRequest';

interface AppChangeRequestModalProps {
  app: AppItem;
  onClose: () => void;
  onSuccess: () => void;
  cooldownLabel?: string;
}

interface ChangeFieldOption {
  key: AppChangeField;
  label: string;
  hint: string;
  group: 'Basics' | 'Media' | 'Source' | 'Catalog' | 'Android';
  kind: 'text' | 'textarea' | 'url' | 'urls';
}

const COMMON_FIELDS: ChangeFieldOption[] = [
  { key: 'name', label: 'App name', hint: 'Visible title', group: 'Basics', kind: 'text' },
  { key: 'author', label: 'Developer or author', hint: 'Creator credit', group: 'Basics', kind: 'text' },
  { key: 'description', label: 'Description', hint: 'Store description', group: 'Basics', kind: 'textarea' },
  { key: 'icon', label: 'Icon URL', hint: 'Direct image URL', group: 'Media', kind: 'url' },
  { key: 'screenshots', label: 'Screenshot URLs', hint: 'One URL per line', group: 'Media', kind: 'urls' },
  { key: 'downloadUrl', label: 'Download or source URL', hint: 'Working project page', group: 'Source', kind: 'url' },
  { key: 'repoUrl', label: 'Repository URL', hint: 'Main repository or website', group: 'Source', kind: 'url' },
  { key: 'githubRepo', label: 'GitHub repository', hint: 'owner/repo or URL', group: 'Source', kind: 'text' },
  { key: 'gitlabRepo', label: 'GitLab project', hint: 'Group/project or URL', group: 'Source', kind: 'text' },
  { key: 'codebergRepo', label: 'Codeberg repository', hint: 'owner/repo or URL', group: 'Source', kind: 'text' },
  { key: 'officialSite', label: 'Official website', hint: 'Product page', group: 'Source', kind: 'url' },
  { key: 'category', label: 'Category', hint: 'Store category', group: 'Catalog', kind: 'text' },
];

const ANDROID_FIELDS: ChangeFieldOption[] = [
  ...COMMON_FIELDS,
  { key: 'releaseKeyword', label: 'Release asset keyword', hint: 'Unique APK filename word', group: 'Android', kind: 'text' },
  { key: 'packageName', label: 'Android package name', hint: 'com.example.app', group: 'Android', kind: 'text' },
];

const getFieldOptions = (platform: Platform) => platform === Platform.ANDROID ? ANDROID_FIELDS : COMMON_FIELDS;

const FIELD_GROUPS: ChangeFieldOption['group'][] = ['Basics', 'Media', 'Source', 'Catalog', 'Android'];

const getCurrentFieldValue = (app: AppItem, field: AppChangeField) => {
  const value = app[field];
  if (Array.isArray(value)) return value.join('\n');
  if (typeof value === 'string') return value && value !== '#' ? value : 'None';
  return 'None';
};

const getUrlError = (value: string) => {
  const trimmed = value.trim();
  if (!trimmed) return 'Enter a valid URL.';
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return 'Use an http or https URL.';
    return '';
  } catch {
    return 'Use a valid http or https URL.';
  }
};

const AppChangeRequestModal: React.FC<AppChangeRequestModalProps> = ({
  app,
  onClose,
  onSuccess,
  cooldownLabel
}) => {
  useScrollLock(true);
  const fieldOptions = useMemo(() => getFieldOptions(app.platform), [app.platform]);
  const [field, setField] = useState<AppChangeField>('name');
  const [suggestedValue, setSuggestedValue] = useState('');
  const [reason, setReason] = useState('');
  const [referenceLink, setReferenceLink] = useState('');
  const [error, setError] = useState('');
  const [issueUrl, setIssueUrl] = useState<string | null>(null);
  const [showFieldPicker, setShowFieldPicker] = useState(false);
  const selectedField = fieldOptions.find((option) => option.key === field) || fieldOptions[0]!;
  const groupedFields = FIELD_GROUPS
    .map((group) => ({ group, options: fieldOptions.filter((option) => option.group === group) }))
    .filter((group) => group.options.length > 0);

  const normalizeSuggestedValue = (option: ChangeFieldOption, value: string) => {
    const trimmed = value.trim();
    if (option.kind === 'urls') {
      return trimmed
        .split(/\n|,/)
        .map((item) => item.trim())
        .filter(Boolean);
    }
    return trimmed;
  };

  const validateSuggestedValue = (option: ChangeFieldOption, value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return 'Enter a corrected value.';
    if (option.kind === 'url') return getUrlError(trimmed);
    if (option.kind === 'urls') {
      const urls = trimmed.split(/\n|,/).map((item) => item.trim()).filter(Boolean);
      if (urls.length === 0) return 'Add at least one screenshot URL.';
      const invalid = urls.find(getUrlError);
      return invalid ? getUrlError(invalid) : '';
    }
    if (option.key === 'packageName' && !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/.test(trimmed)) {
      return 'Use a valid Android package name.';
    }
    return '';
  };

  const buildIssueUrl = () => {
    const payload = buildAppUpdatePayload(
      app,
      selectedField.key,
      normalizeSuggestedValue(selectedField, suggestedValue)
    );
    return buildAppUpdateIssueUrl({
      app,
      fieldLabel: selectedField.label,
      currentValue: getCurrentFieldValue(app, selectedField.key),
      suggestedValue,
      reason,
      referenceLink,
      payload
    });
  };

  const handleSubmit = () => {
    setError('');
    if (cooldownLabel) {
      setError(`Next request available in ${cooldownLabel}.`);
      return;
    }
    const validationError = validateSuggestedValue(selectedField, suggestedValue);
    if (validationError) {
      setError(validationError);
      return;
    }
    if (referenceLink.trim()) {
      const referenceError = getUrlError(referenceLink);
      if (referenceError) {
        setError(`Evidence link: ${referenceError}`);
        return;
      }
    }
    setIssueUrl(buildIssueUrl());
  };

  const openIssue = () => {
    if (!issueUrl) return;
    window.open(issueUrl, '_blank');
    onSuccess();
    onClose();
  };

  return createPortal(
    <div className="backdrop-scrim fixed inset-0 z-[310] flex items-end justify-center bg-black/70 p-0 backdrop-blur-sm animate-fade-in sm:items-center sm:p-5">
      <div className="relative flex max-h-[94vh] w-full max-w-md flex-col overflow-hidden rounded-t-[2rem] bg-surface shadow-2xl animate-slide-up sm:rounded-[2rem]">
        <div className="flex shrink-0 items-center gap-3 border-b border-theme-border px-5 pb-4 pt-5">
          <img
            src={getOptimizedImageUrl(app.icon, 96, 96)}
            alt=""
            className="h-12 w-12 rounded-2xl bg-theme-element object-contain p-1"
          />
          <div className="min-w-0 flex-1">
            <span className="text-[9px] font-black uppercase tracking-[0.18em] text-primary">{app.platform} App Update</span>
            <h2 className="truncate text-lg font-black text-theme-text">{app.name}</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-theme-element text-theme-sub hover:bg-theme-hover hover:text-theme-text"
          >
            <i className="fas fa-times" />
          </button>
        </div>

        {!issueUrl ? (
          <>
            <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-5 py-4 no-scrollbar">
              <button
                type="button"
                onClick={() => setShowFieldPicker(true)}
                className="flex w-full items-center gap-3 rounded-2xl border border-theme-border bg-card px-3 py-3 text-left transition-colors hover:bg-theme-element"
              >
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <i className="fas fa-pen-to-square text-xs" />
                </div>
                <div className="min-w-0 flex-1">
                  <span className="block text-[9px] font-black uppercase tracking-wide text-theme-sub">What to correct</span>
                  <span className="mt-0.5 block truncate text-sm font-black text-theme-text">{selectedField.label}</span>
                </div>
                <i className="fas fa-chevron-down text-xs text-theme-sub" />
              </button>

              <details className="group rounded-2xl border border-theme-border/70 bg-theme-element/40 px-3">
                <summary className="flex cursor-pointer list-none items-center justify-between py-2.5 text-[10px] font-black uppercase tracking-wide text-theme-sub">
                  <span>Current value</span>
                  <i className="fas fa-chevron-down text-[10px] transition-transform group-open:rotate-180" />
                </summary>
                <span className="block max-h-40 overflow-y-auto whitespace-pre-wrap break-words pb-3 text-xs font-bold leading-relaxed text-theme-text">
                  {getCurrentFieldValue(app, selectedField.key).slice(0, 1000)}
                </span>
              </details>

              <label className="block">
                <span className="mb-1.5 block text-[10px] font-black uppercase tracking-wide text-theme-sub">Correct value</span>
                {selectedField.kind === 'textarea' ? (
                  <textarea
                    value={suggestedValue}
                    onChange={(event) => setSuggestedValue(event.target.value)}
                    rows={4}
                    maxLength={4000}
                    className="w-full resize-none rounded-2xl border border-theme-border bg-theme-input px-3 py-3 text-sm text-theme-text outline-none placeholder:text-theme-sub focus:border-primary/60"
                  />
                ) : selectedField.kind === 'urls' ? (
                  <textarea
                    value={suggestedValue}
                    onChange={(event) => setSuggestedValue(event.target.value)}
                    rows={4}
                    placeholder={'https://image.example/one.png\nhttps://image.example/two.png'}
                    className="w-full resize-none rounded-2xl border border-theme-border bg-theme-input px-3 py-3 text-sm text-theme-text outline-none placeholder:text-theme-sub focus:border-primary/60"
                  />
                ) : (
                  <input
                    value={suggestedValue}
                    onChange={(event) => setSuggestedValue(event.target.value)}
                    type={selectedField.kind === 'url' ? 'url' : 'text'}
                    placeholder={selectedField.label}
                    className="w-full rounded-2xl border border-theme-border bg-theme-input px-3 py-3 text-sm text-theme-text outline-none placeholder:text-theme-sub focus:border-primary/60"
                  />
                )}
              </label>

              <label className="block">
                <span className="mb-1.5 block text-[10px] font-black uppercase tracking-wide text-theme-sub">Reason</span>
                <textarea
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  rows={2}
                  maxLength={4000}
                  placeholder="Why is this correction needed?"
                  className="w-full resize-none rounded-2xl border border-theme-border bg-theme-input px-3 py-3 text-sm text-theme-text outline-none placeholder:text-theme-sub focus:border-primary/60"
                />
              </label>

              <details className="rounded-2xl border border-theme-border/70 bg-theme-element/40 px-3">
                <summary className="flex cursor-pointer list-none items-center justify-between py-2.5 text-[10px] font-black uppercase tracking-wide text-theme-sub">
                  <span>Evidence link (optional)</span>
                  <i className="fas fa-chevron-down text-[10px]" />
                </summary>
                <input
                  value={referenceLink}
                  onChange={(event) => setReferenceLink(event.target.value)}
                  type="url"
                  placeholder="Release page or official source"
                  className="mb-3 w-full rounded-xl border border-theme-border bg-theme-input px-3 py-2.5 text-sm text-theme-text outline-none placeholder:text-theme-sub focus:border-primary/60"
                />
              </details>

              {error && (
                <div className="flex items-center gap-2 rounded-2xl bg-red-500/10 px-3 py-2 text-[11px] font-bold text-red-500">
                  <i className="fas fa-circle-exclamation" />
                  {error}
                </div>
              )}
            </div>

            <div className="shrink-0 border-t border-theme-border bg-surface px-5 pb-[calc(1rem+env(safe-area-inset-bottom))] pt-3">
              <button
                type="button"
                onClick={handleSubmit}
                className="flex w-full items-center justify-center gap-2 rounded-2xl bg-primary py-3.5 text-sm font-black text-white shadow-lg shadow-primary/20 transition-transform active:scale-[0.98]"
              >
                <i className="fas fa-file-code text-xs" />
                Prepare App Update Issue
              </button>
              <div className="mt-2 flex items-center justify-center gap-1.5 text-[9px] font-black uppercase text-amber-600 dark:text-amber-300">
                <i className="fas fa-star" />
                +50 XP toward leaderboard
              </div>
            </div>
          </>
        ) : (
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center px-6 py-8 text-center">
            <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-3xl bg-amber-500/10 text-amber-500">
              <i className="fas fa-file-circle-check text-2xl" />
            </div>
            <h3 className="text-xl font-black text-theme-text">Update Issue Ready</h3>
            <p className="mt-2 text-xs leading-relaxed text-theme-sub">
              The issue contains the exact JSON that the App Update workflow merges into apps.json after review.
            </p>
            <div className="mt-6 flex w-full flex-col gap-2">
              <button
                type="button"
                onClick={openIssue}
                className="flex w-full items-center justify-center gap-2 rounded-2xl bg-primary px-4 py-3.5 text-sm font-black text-white shadow-lg shadow-primary/20 transition-transform active:scale-[0.98]"
              >
                <i className="fas fa-arrow-up-right-from-square text-xs" />
                Open GitHub Issue
              </button>
              <button
                type="button"
                onClick={() => setIssueUrl(null)}
                className="flex w-full items-center justify-center rounded-2xl bg-theme-element px-4 py-3 text-xs font-black text-theme-sub"
              >
                Edit Request
              </button>
            </div>
          </div>
        )}

        {showFieldPicker && (
          <div className="absolute inset-0 z-20 flex flex-col bg-surface animate-fade-in">
            <div className="flex shrink-0 items-center justify-between border-b border-theme-border px-5 py-4">
              <button
                type="button"
                onClick={() => setShowFieldPicker(false)}
                className="flex h-10 w-10 items-center justify-center rounded-full bg-theme-element text-theme-sub hover:bg-theme-hover hover:text-theme-text"
              >
                <i className="fas fa-arrow-left" />
              </button>
              <div className="min-w-0 flex-1 text-center">
                <span className="block text-[9px] font-black uppercase tracking-wide text-theme-sub">Choose one field</span>
                <h3 className="truncate text-lg font-black text-theme-text">Correct an app detail</h3>
              </div>
              <div className="h-10 w-10" />
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3 no-scrollbar">
              {groupedFields.map((group) => (
                <div key={group.group} className="mb-4">
                  <span className="mb-1.5 block px-1 text-[10px] font-black uppercase tracking-wide text-theme-sub">{group.group}</span>
                  <div className="space-y-1.5">
                    {group.options.map((option) => (
                      <button
                        key={option.key}
                        type="button"
                        onClick={() => {
                          setField(option.key);
                          setSuggestedValue('');
                          setShowFieldPicker(false);
                        }}
                        className={`flex w-full items-center gap-3 rounded-2xl border px-3 py-3 text-left transition-colors ${
                          field === option.key
                            ? 'border-primary/30 bg-primary/10 text-primary'
                            : 'border-transparent bg-card text-theme-text hover:bg-theme-element'
                        }`}
                      >
                        <div className="min-w-0 flex-1">
                          <span className="block text-sm font-black">{option.label}</span>
                          <span className="mt-0.5 block text-[10px] font-bold text-theme-sub">{option.hint}</span>
                        </div>
                        <i className={`fas ${field === option.key ? 'fa-check' : 'fa-chevron-right'} text-xs text-theme-sub`} />
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>,
    document.body
  );
};

export default AppChangeRequestModal;
