import { AppItem } from '../types';

export type AppChangeField =
  | 'name'
  | 'author'
  | 'description'
  | 'icon'
  | 'downloadUrl'
  | 'repoUrl'
  | 'githubRepo'
  | 'gitlabRepo'
  | 'codebergRepo'
  | 'releaseKeyword'
  | 'packageName'
  | 'category'
  | 'officialSite'
  | 'screenshots';

export const buildAppUpdatePayload = (
  app: AppItem,
  field: AppChangeField,
  value: string | string[]
): Record<string, string | string[]> => ({
  id: app.id,
  platform: app.platform,
  [field]: value
});

export const buildAppUpdateIssueUrl = (input: {
  app: AppItem;
  fieldLabel: string;
  currentValue: string;
  suggestedValue: string;
  reason: string;
  referenceLink: string;
  payload: Record<string, string | string[]>;
}) => {
  const title = `App Update - ${input.app.name} (${input.app.platform})`;
  const body = [
    '### App Update Request',
    '',
    `- App ID: \`${input.app.id}\``,
    `- Platform: **${input.app.platform}**`,
    `- Field: **${input.fieldLabel}**`,
    `- Current value: ${input.currentValue.slice(0, 500)}`,
    '',
    '### Suggested value',
    '```text',
    input.suggestedValue.trim().slice(0, 4000) || '(described in reason)',
    '```',
    '',
    '### Reason',
    input.reason.trim() || 'No additional details provided.',
    '',
    input.referenceLink.trim() ? `**Evidence:** ${input.referenceLink.trim()}` : '',
    '',
    '### Automation payload',
    '```json',
    JSON.stringify(input.payload, null, 2),
    '```',
    '',
    '*Apply the `approved` label and run the App Update workflow to merge this change.*'
  ].join('\n');

  return `https://github.com/RookieEnough/Orion-Data/issues/new?title=${encodeURIComponent(title)}&body=${encodeURIComponent(body)}`;
};
