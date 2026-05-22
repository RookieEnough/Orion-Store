# Badge Showcase Flow Design

**Date:** 2026-05-22

## Goal
Refine badge showcasing so the About tab is the only place where users add showcased badges, while the badge archive stays visually clean and focused on viewing unlocked badges.

## Scope
- Keep badge tracking in the profile badge archive.
- Remove add/remove/showcase controls from the badge archive.
- Let the About tab drive badge showcasing through a single glowing `Add Badge` pill.
- Reuse the existing profile badge popup in a dedicated selection flow.
- Auto-flip viewed badges back to their front side after 4 seconds.
- Allow swapping showcased badges from the About tab once all 3 slots are filled.
- Add a reset action in selection mode to clear all showcased badges.

## Current Problems
- The badge archive mixes browsing with editing, which makes the flow feel noisy.
- The About tab currently suggests multiple empty slots instead of a single progressive add action.
- Badge flipping stays open too long and feels unfinished.

## Approved UX

### Badge Archive
- The archive remains a clean grid of badges.
- Each badge can still be tapped to flip and reveal its label.
- No add, remove, or showcase buttons appear in the archive.
- A flipped badge automatically returns to its default face after 4 seconds.

### About Tab Showcase
- The About tab renders showcased badges in order.
- If the user has fewer than 3 showcased badges, show exactly one small glowing `Add Badge` pill after the last showcased badge.
- The About tab never shows 3 empty add placeholders.
- After the third badge is added, the `Add Badge` pill disappears.
- Once 3 badges are showcased, tapping any showcased badge reopens selection mode for swapping that specific slot.

### Add Badge Flow
- Clicking the About tab `Add Badge` pill opens the existing badge popup in selection mode.
- In selection mode, tapping any unlocked badge immediately:
  - adds that badge to showcased badges,
  - closes the popup,
  - returns the user to the About tab.
- Locked badges do not add and do not close the popup.
- The existing 3-badge cap remains enforced in store logic.
- Selection mode includes a small `Reset` action that clears all showcased badges.

## Component Behavior

### `components/AboutView.tsx`
- Render showcased badges from the existing `showcasedBadges` state.
- Limit the rendered list to 3 badges.
- Replace multiple empty placeholder pills with one glowing add pill.
- Open the profile stats modal in badge-selection mode when the add pill is tapped.
- When all 3 showcased slots are full, let each showcased badge reopen selection mode for swapping.

### `components/ProfileStatsModal.tsx`
- Support separate modal behavior for:
  - archive viewing mode,
  - About-driven selection mode.
- In archive viewing mode:
  - preserve badge flipping,
  - remove action controls.
- In selection mode:
  - tapping an unlocked badge immediately adds it and closes the modal,
  - tapping a locked badge has no effect,
  - badge flip behavior should not block selection.
- Show showcased highlight rings only in selection mode, not in archive browsing mode.
- Add a 4-second timer that resets flipped badges automatically.

### `store/useAppStore.ts`
- Keep the current `showcasedBadges` max-3 enforcement.
- Reuse existing add/toggle logic only if it supports the new selection flow cleanly.
- If toggle behavior creates ambiguous UX in selection mode, the modal should guard usage so a selection tap only adds when appropriate.

## Error Handling
- If the user already has 3 showcased badges, the About add pill is hidden.
- If the user taps a locked badge in selection mode, do nothing.
- If the selected badge is already showcased, selection mode should not duplicate it.

## Testing Notes
- Verify About shows:
  - `badge + add`,
  - `badge + badge + add`,
  - `badge + badge + badge`.
- Verify tapping `Add Badge` opens the badge popup in selection mode.
- Verify tapping an unlocked badge adds it instantly and closes the popup.
- Verify tapping a locked badge does not close the popup.
- Verify badge archive no longer shows add/remove/showcase controls.
- Verify flipped badges return to normal after 4 seconds.

## Non-Goals
- No new badge removal UX in this change.
- No badge reordering UX in this change.
- No store schema redesign in this change.
