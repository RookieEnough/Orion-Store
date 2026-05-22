# Maintenance Mini-Game Polish Design

**Date:** 2026-05-22

## Goal
Improve the maintenance mini-game so controls are clearer on small screens, the light-mode helper text remains readable, Dino badges require more dedicated play, and game speed ramps up more smoothly.

## Scope
- Fix the `Tap to Play` helper text contrast for light mode while preserving strong visibility in darker themes.
- Replace the in-panel back button with a stateful gameplay action button.
- Keep the top-right `X` as the only close control.
- Raise Dino badge unlock thresholds to more demanding scores.
- Smooth out game acceleration so difficulty increases gradually instead of feeling sudden.

## Approved UX

### Maintenance Helper Text
- In light mode, `Tap to Play` uses a dark bold style.
- In dusk, dark, and oled themes, `Tap to Play` uses a yellow bold style.

### Mini-Game Action Button
- The bottom action button becomes the primary control for the mini-game.
- Button states:
  - `Play` before the run starts,
  - `Jump` while the run is active,
  - `Restart` after game-over.
- The top-right `X` remains the only close action.
- Screen tap may remain available as a secondary input, but the button becomes the clear primary control.

### Dino Badge Difficulty
- New unlock milestones:
  - `Dino Rookie`: `300`
  - `Dino Master`: `650`
  - `Dino Legend`: `1200`
- The score card and XP flow remain unchanged unless required by implementation.

### Speed Ramp
- The game should accelerate progressively using a smoother curve.
- Difficulty should feel steadily increasing rather than jumping abruptly at specific moments.

## Component Behavior

### `components/MaintenanceMode.tsx`
- Apply theme-sensitive helper text color so light mode uses dark text and other themes use yellow.

### `components/DinoGameModal.tsx`
- Replace the current `Go Back` button with a stateful gameplay button.
- Reflect the game state in the button label: `Play`, `Jump`, `Restart`.
- Keep the `X` button untouched for closing.
- Continue showing the last-score panel after game-over.

### `components/DinoGame.tsx`
- Expose enough state to let the modal know whether the game is idle, active, or crashed.
- Support explicit commands from the modal button for:
  - start,
  - jump,
  - restart.
- Preserve tap interaction if possible, but the explicit button should be reliable on small screens.
- Smooth the runner speed progression instead of abrupt acceleration.

### Badge Unlock Logic
- Update the Dino badge unlock thresholds wherever final score is processed.
- Unlock all earned badge tiers based on the new thresholds.

## Error Handling
- If the runner instance is unavailable momentarily, button commands should fail safely without breaking the modal.
- Restart should always reset the game into a fresh playable run after crash.

## Testing Notes
- Verify `Tap to Play` is dark and readable in light mode.
- Verify `Tap to Play` remains yellow and readable in dusk/dark/oled.
- Verify the bottom button shows `Play` before start.
- Verify it changes to `Jump` during an active run.
- Verify it changes to `Restart` after a crash.
- Verify `X` still closes the modal.
- Verify Dino badges unlock at `300 / 650 / 1200`.
- Verify speed increases gradually and no longer feels sudden.

## Non-Goals
- No full mini-game redesign.
- No changes to XP math unless required by the control refactor.
