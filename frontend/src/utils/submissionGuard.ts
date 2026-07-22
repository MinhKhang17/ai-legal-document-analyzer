export type SubmissionState = { current: boolean };

export function tryStartSubmission(state: SubmissionState): boolean {
  if (state.current) return false;
  state.current = true;
  return true;
}

export function finishSubmission(state: SubmissionState): void {
  state.current = false;
}
