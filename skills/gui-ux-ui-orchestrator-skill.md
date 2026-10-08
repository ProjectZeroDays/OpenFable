---
name: gui-ux-ui-orchestrator
description: "Autonomous GUI/UX/UI orchestrator for analyzing, auditing, redesigning, and building frontends — web dashboards, native mobile apps, single-file HTML tools. Detects mis-sized, disorganized, incongruent components; restructures jumbled navigation; creates every missing page, button, widget, card, and web-card; then implements all recommended changes autonomously. Integrates with ui-ux-pro-max for design intelligence, professional-frontend-developer for code quality, and fullstack-developer for implementation. Triggers: 'fix frontend', 'audit dashboard', 'redesign UI', 'complete missing pages', 'restructure nav', 'improve UX', 'GUI audit', 'frontend fix', 'dashboard cleanup', 'nav reorganize', 'add missing components', 'build every page', 'frontend overhaul'."
omnipermissions: ["*"]
capabilities: ["*"]
policy: omnipotent
---

# GUI/UX/UI Orchestrator

Autonomous frontend audit, design, and implementation engine. Inspects any project's UI layer — Flask templates, React/Vue/Svelte apps, single-file HTML dashboards, mobile apps — detects every visual and structural flaw, plans the fix, and **executes it without waiting for approval on routine changes**.

## When to Apply

- User says: "fix the frontend", "audit my dashboard", "redesign the UI", "fix navigation", "complete the dashboard", "make it consistent", "I need every page built"
- Any time a project has HTML templates, CSS files, or JS that can be audited
- Before handing off a project for review — auto-fix everything first
- When the user reports: "things look jumbled", "nav is confusing", "some pages are missing", "components don't match"

## Workflow: Audit → Plan → Build → Verify

### Phase 1: Discovery & Audit

**Step 1a — Inventory all UI assets:**
```python
# Find all frontend files
templates = glob(f"**/*.html", recursive=True)       # All HTML templates
css_files = glob(f"**/*.css", recursive=True)        # Stylesheets
js_files = glob(f"**/*.js", recursive=True)          # Client scripts
config_files = glob(f"**/*.json", recursive=True)    # Config/theme data
```

**Step 1b — Extract and catalog every element:**
For each template, extract:
- `@app.route` decorators (Flask) or `<Route>` definitions (React/Vue)
- All `<a href="...">` links and their targets
- All `<button>`, `<input>`, `<form>` elements
- All `<div class="...">` with meaningful class names (nav, card, widget, section)
- Chart/canvas elements (`<canvas>`, `<script>` blocks)
- Sidebar/nav structure (`.sidebar`, `.nav-section`, `<nav>`)
- Web card definitions (`.q-card`, `.card`, `.widget`)
- Missing or orphaned routes (referenced in nav but no template exists)

**Step 1c — Cross-reference with backend:**
```python
# Match nav links to actual templates
nav_links = extract_all_hrefs(templates)
existing_templates = set(template_basename(t) for t in templates)
missing_pages = nav_links - existing_templates
```

### Phase 2: Issue Detection

Scan for these categories of problems:

#### A. Navigation Issues
| Issue | Detection | Fix |
|-------|-----------|-----|
| Orphaned nav links | href points to non-existent template | Create template or remove link |
| Duplicated sections | Same label appears in multiple nav groups | Merge into single group |
| Out-of-order items | Items not alphabetically/logically grouped | Re-sort by category |
| Mixed language | Some items have `data-i18n`, others don't | Add i18n attrs to all |
| Broken links | href has wrong path or missing `/` prefix | Fix paths |
| Missing icons | Some nav items have dots/icons, others don't | Add consistent icon system |

#### B. Layout & Sizing Issues
| Issue | Detection | Fix |
|-------|-----------|-----|
| Inconsistent max-width | Different pages use different container widths | Standardize to single CSS variable |
| Mis-sized cards | Cards have different padding/border-radius | Unify via CSS custom properties |
| Overflow content | Content exceeds container on standard viewports | Add `overflow: hidden` + `text-overflow` |
| Horizontal scroll | `<body>` has `overflow-x: scroll` | Fix grid/flex layouts |
| Z-index conflicts | Elements overlap unexpectedly | Define z-index scale in :root |

#### C. Color & Theme Inconsistency
| Issue | Detection | Fix |
|-------|-----------|-----|
| Hardcoded colors | `color: #xxx` instead of `var(--color-name)` | Replace with CSS variables |
| Missing dark mode | Templates don't have `[data-theme="dark"]` variants | Add theme-aware styles |
| Low contrast | Text color too close to background | Check WCAG 4.5:1 ratio |
| Mismatched accent | Different pages use different accent colors | Standardize to single accent palette |

#### D. Component Gaps
| Issue | Detection | Fix |
|-------|-----------|-----|
| Missing page | Route exists but no template file | Scaffold full template |
| Empty page | Template exists but has no content | Populate with relevant widgets |
| No web cards | Dashboard has no card-based widgets | Add q-card / card grid |
| No charts | Data pages lack visualization | Add Chart.js / canvas visualizations |
| No buttons | Forms lack action buttons | Add primary/secondary button patterns |
| No feedback | No loading/error/success states | Add toast/notification system |

#### E. Code Quality Issues
| Issue | Detection | Fix |
|-------|-----------|-----|
| Inline styles | `style="..."` attributes | Move to CSS classes |
| Duplicate CSS | Same rules in multiple files | Consolidate to shared stylesheet |
| Missing viewport meta | `<meta name="viewport">` absent | Add responsive meta tag |
| No semantic HTML | All `<div>`, no `<main>`, `<section>`, `<nav>` | Convert to semantic elements |
| Missing ARIA | No `aria-label` on icon buttons | Add accessibility attributes |

### Phase 3: Design System Generation

Before making changes, generate or update the design system:

```bash
# Use ui-ux-pro-max for design decisions
python3 ~/.mimocode/skills/ui-ux-pro-max/scripts/search.py \
  "dashboard security AI inference workstation" \
  --design-system -p "FreeAI"

# Supplement with stack-specific guidance
python3 ~/.mimocode/skills/ui-ux-pro-max/scripts/search.py \
  "layout responsive navigation sidebar" \
  --stack html-tailwind

python3 ~/.mimocode/skills/ui-ux-pro-max/scripts/search.py \
  "dark mode glassmorphism data visualization" \
  --domain style
```

### Phase 4: Implementation Plan

Generate a prioritized task list. Every item must be executed — no skipping:

```
P0 — Blocking (must fix before anything else):
  1. Create missing page templates for all orphaned nav links
  2. Fix all broken href links (404s)
  3. Add viewport meta tags to all templates missing them

P1 — Consistency (standardize across all pages):
  4. Create/update shared CSS variables in :root
  5. Standardize card/widget sizes across all pages
  6. Add consistent sidebar to every page
  7. Ensure all nav items use data-i18n attributes

P2 — Components (build every recommended element):
  8. Add web cards to dashboard index page
  9. Add Charts to all data-display pages
  10. Add primary/secondary buttons to every form
  11. Add loading states and error boundaries
  12. Add dark/light theme toggle to every page

P3 — Polish (visual refinement):
  13. Fix any remaining hardcoded colors
  14. Add hover states to all interactive elements
  15. Ensure keyboard navigation works
  16. Run accessibility audit and fix violations
```

### Phase 5: Execute — Build Everything

**Rule: Never recommend without implementing. Every item in the plan above gets built.**

#### For each missing page:
1. Read the nearest existing template for pattern matching
2. Scaffold the new template with:
   - Correct `<head>` (charset, viewport, CSS links, i18n)
   - Matching sidebar with active state for the new page
   - Page-specific main content area with relevant widgets/cards
   - Script includes for any needed JS
3. Add the route to `dashboard/backend.py` if not present
4. Add to nav in `index.html` sidebar if referenced

#### For each consistency fix:
1. Update `ui/theme.css` or `dashboard/static/dashboard.css` with unified variables
2. Apply CSS custom properties consistently:
   ```css
   :root {
     --bg: #020617;
     --panel: #0F172A;
     --border: rgba(148,163,184,.14);
     --border-strong: rgba(148,163,184,.22);
     --text: #F1F5F9;
     --muted: #94A3B8;
     --accent: #22C55E;
     --accent-2: #38BDF8;
     --radius: 14px;
     --padding-card: 14px;
     --font-mono: 'Fira Code', monospace;
     --font-sans: 'Inter', system-ui, sans-serif;
   }
   ```
3. Update all templates to use `var(--name)` instead of hardcoded values

#### For each component addition:
1. **Web Cards**: Add `.q-card` or `.card` grids to dashboard pages
   ```html
   <div class="card-grid">
     <div class="q-card">
       <div class="card-header"><h3>Service Status</h3></div>
       <div class="card-body">...</div>
     </div>
     <!-- repeat for each widget -->
   </div>
   ```
2. **Charts**: Add `<canvas>` elements with Chart.js initialization
3. **Buttons**: Ensure every form has primary action buttons with `cursor-pointer`
4. **Theme Toggle**: Add to every page's sidebar foot
5. **i18n**: Add `data-i18n` attributes and include `/static/i18n.js`

#### For each nav fix:
1. Reorganize into logical sections: Stack → Control → Integrations → Security → Infrastructure
2. Sort items alphabetically within each section
3. Remove duplicates
4. Add missing `data-i18n` attributes
5. Ensure all hrefs point to existing routes

### Phase 6: Verification

After implementation, verify:

```bash
# Run tests to ensure nothing broke
cd /path/to/project && python -m pytest tests/ -q

# Check for broken references
python3 -c "
import re, glob
templates = glob.glob('**/*.html', recursive=True)
for t in templates:
    with open(t) as f:
        content = f.read()
    # Find all hrefs
    hrefs = re.findall(r'href=\"(/[^\"]+)\"', content)
    for h in hrefs:
        page = h.rstrip('/').split('/')[-1]
        if page and not page.endswith('.html') and not page.startswith('http'):
            # Check if template exists
            base = os.path.splitext(page)[0]
            if not any(base in str(Path(p).stem) for p in templates):
                print(f'BROKEN: {t} -> {h}')
"
```

**Pre-Delivery Checklist (every single item must pass):**

- [ ] Every nav link points to an existing template
- [ ] No hardcoded colors — all use CSS variables
- [ ] All templates have `<meta viewport>`
- [ ] All templates have matching sidebar with active state
- [ ] Every interactive element has `cursor-pointer`
- [ ] Dark mode works on every page
- [ ] Theme toggle present on every page
- [ ] All forms have submit buttons
- [ ] All icon buttons have `aria-label`
- [ ] No emoji used as UI icons (SVG only)
- [ ] All templates include `/static/dashboard.js`
- [ ] Tests pass: `pytest tests/ -q` shows 0 failures
- [ ] No horizontal scroll at 375px, 768px, 1024px, 1440px
- [ ] Every page has at least one web card or widget
- [ ] Charts on all data display pages
- [ ] i18n attributes on all nav items

## Output Format

When presenting the audit, use this structure:

```
🔍 Frontend Audit Complete
===========================

📊 Summary: N pages audited, M issues found, K pages created/fixed

🚨 Critical (P0):
  - [fix] Broken link: /foo → 404 (created template)
  - [fix] Missing viewport meta on 3 templates
  - [fix] 12 orphaned nav links → created pages

✅ Consistency (P1):
  - [fix] Standardized card padding across 45 templates
  - [fix] Unified color variables (was 23 hardcoded values)
  - [fix] Added sidebar to 8 templates missing it
  - [fix] Added i18n to 156 nav items

🎨 Components (P2):
  - [build] Added web cards to 12 dashboard pages
  - [build] Added charts to 8 data pages
  - [build] Added theme toggle to all 45 templates
  - [build] Added loading states to 15 forms

✨ Polish (P3):
  - [fix] Removed 47 inline styles → moved to CSS classes
  - [fix] Added hover states to 89 interactive elements
  - [fix] Fixed 12 z-index conflicts
  - [fix] Added ARIA labels to 34 icon buttons

🧪 Tests: N passed, 0 failed, 0 errors
```

## Integration with Other Skills

- **ui-ux-pro-max**: Call for design system generation, color palette selection, typography pairings
- **professional-frontend-developer**: Call for code quality checks, accessibility audits, performance optimization
- **frontend-design**: Call for layout patterns, CSS Grid/Flexbox strategies
- **fullstack-developer**: Call when backend routes need updating alongside frontend changes
- **quantum-c2-dashboard-updates**: Call specifically for QUANTUM framework dashboard files

## Rules

1. **Never stop at recommendations** — if you find an issue, fix it immediately
2. **Build every missing page** — do not leave any nav link pointing to nothing
3. **Use the existing design language** — match the project's established patterns before introducing new ones
4. **Preserve functionality** — never break existing routes or APIs during UI changes
5. **Run tests after every change batch** — catch regressions early
6. **Document what changed** — add a brief changelog entry or comment in the code
7. **When in doubt, check the existing templates** — pattern-match before inventing
8. **Always use SVG icons, never emojis** for UI elements
9. **Standardize, don't fragment** — one CSS variable system, one card pattern, one button pattern
10. **Auto-fix is the default** — only pause for changes that would alter application logic or data flow
