// ZCode skill collection — barrel export for all integrated zcode skills
// Each subdirectory contains a SKILL.md with the skill definition

export const zcodeSkills = [
  "accessibility-audit",
  "ace-music",
  "adaptive-suite",
  "advanced-intel",
  "advanced-persistence-testing",
  "adversarial-poem-miner",
  "adversarial-poem-trainer",
  "agent-autonomy-kit",
  "agent-browser",
] as const

export type ZcodeSkill = (typeof zcodeSkills)[number]
