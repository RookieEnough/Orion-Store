import { create } from 'zustand';

export interface TutorialStep {
  targetId?: string;
  title: string;
  description: string;
  buttonLabel?: string;
  onTargetClick?: () => void;
}

interface TutorialState {
  isActive: boolean;
  steps: TutorialStep[];
  currentStepIndex: number;
  startTutorial: (steps: TutorialStep[]) => void;
  nextStep: () => void;
  endTutorial: () => void;
}

export const useTutorialStore = create<TutorialState>((set, get) => ({
  isActive: false,
  steps: [],
  currentStepIndex: 0,
  startTutorial: (steps) => set({ isActive: true, steps, currentStepIndex: 0 }),
  nextStep: () => {
    const { currentStepIndex, steps } = get();
    if (currentStepIndex < steps.length - 1) {
      set({ currentStepIndex: currentStepIndex + 1 });
    } else {
      set({ isActive: false, steps: [], currentStepIndex: 0 });
    }
  },
  endTutorial: () => set({ isActive: false, steps: [], currentStepIndex: 0 }),
}));
