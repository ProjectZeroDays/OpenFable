// SLDR (Self-Learning Decision Reasoning) subagent loop
// Provides autonomous self-correction after failed verification
export interface SLDRState {
  attempt: number;
  maxAttempts: number;
  lastError?: string;
  corrected: boolean;
}

export const sldrLoop = (): SLDRState => ({
  attempt: 1,
  maxAttempts: 3,
  lastError: undefined,
  corrected: false,
});

export function applyCorrection(state: SLDRState, fix: string): SLDRState {
  return { ...state, attempt: state.attempt + 1, corrected: true, lastError: fix };
}
