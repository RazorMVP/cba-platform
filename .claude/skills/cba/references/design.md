# CBA Design System Reference — Nubeero Brand

## Source
Design system extracted from the Nubeero brand — maintained in **Stitch** (`stitch.design`).
Project: `CoreBanking-Nubeero` | Workspace: Nubeero Pro
Design engine: Stitch (replaces Figma — do not use Figma for this project)

---

## Design Language

The Nubeero design system uses a **dark-shell, light-card** pattern:
- **App shell** (sidebar, background): very dark near-black `#040609`
- **Content panels** (cards, tables): clean white with subtle borders
- **CTAs**: deep navy `#1e2833` with fully rounded (pill) shape
- **Typography**: `Instrument Sans` — clean, professional, slightly warm

This creates a high-contrast, professional look suited to financial applications.

---

## Design Tokens

See `designs/tokens.scss` for the full SCSS variable set. Key values:

### Colors
| Token | Value | Usage |
|-------|-------|-------|
| `--bg-app` | `#040609` | App shell background |
| `--bg-sidebar` | `#0a1628` | Left navigation panel |
| `--bg-card` | `#ffffff` | Content cards, panels |
| `--bg-subtle` | `#f4f5f7` | Table zebra, input bg |
| `--color-primary` | `#1e2833` | CTA buttons, active states |
| `--color-text` | `#000314` | Primary text, headings |
| `--color-muted` | `#888888` | Secondary text, labels |
| `--color-placeholder` | `#7E8187` | Input placeholder text |
| `--color-border` | `rgba(47,43,67,0.1)` | All borders |

### Semantic Colors
| Purpose | Hex | Background |
|---------|-----|-----------|
| Success | `#16a34a` | `#dcfce7` |
| Warning | `#ca8a04` | `#fef9c3` |
| Error   | `#dc2626` | `#fee2e2` |
| Info    | `#2563eb` | `#dbeafe` |

### Typography — Instrument Sans
```
Regular (400)  — body text, labels, placeholders
Medium (500)   — nav items, button text, semi-emphasis
SemiBold (600) — headings, KPI values, table column names
Bold (700)     — large numbers, key metrics
```

Font sizes: 10px (caps labels) → 12px (small) → 13px (table) → 14px (card title) → 16px (body) → 18px (page title) → 24px+ (KPI numbers)

### Spacing
Base unit: **8px**. All spacing values are multiples: 4, 8, 12, 16, 20, 24, 32, 48, 64px

### Radius
- `8px` — tags, badges, small buttons, table action buttons
- `12px` — cards, panels, input fields, modals
- `9999px` — primary CTA buttons (pill shape)

### Input Fields
```
height:    48px
border:    1px solid rgba(47,43,67,0.1)
radius:    12px
bg:        #ffffff
padding:   0 16px
font:      Instrument Sans 16px Regular
color:     #000314
placeholder: #7E8187
```

### Buttons
```
Primary:  bg #1e2833 | color white | height 48px | radius 9999px | font Medium 16px
Secondary: bg white  | color #000314 | border 1px rgba(47,43,67,0.1) | radius 8px
Danger:    bg #fff5f5 | color #dc2626 | border #fee2e2 | radius 8px
Small:     height 36–40px | same style rules
```

### Shadows
```
Card:   0 1px 3px rgba(0,0,0,0.08), 0 4px 12px rgba(0,0,0,0.04)
Input:  inset 0 -1px 0 rgba(47,43,67,0.1), 0 1px 3px rgba(47,43,67,0.1)
Modal:  0 8px 32px rgba(0,0,0,0.16), 0 2px 8px rgba(0,0,0,0.08)
```

---

## Layout System

### Backoffice Web (1440px base width)
```
┌─────────────────────────────────────────┐
│  Sidebar (260px fixed)                   │
│  ┌──────────────────────────────────────┐│
│  │  Topbar (64px height, sticky)        ││
│  │  ──────────────────────────────────  ││
│  │  [Optional toolbar: search + filters]││
│  │  ──────────────────────────────────  ││
│  │  Content area (padding: 24px)        ││
│  │    KPI cards grid (4 col)            ││
│  │    Main content grid (1fr + 360px)   ││
│  └──────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

### Sidebar Anatomy
```
Logo (24px padding) → border
Nav section label (10px caps, 30% white)
Nav items (10px 12px padding, 8px radius)
  → active: rgba(255,255,255,0.1) bg
  → hover:  rgba(255,255,255,0.06) bg
  → icon + label + optional badge
User profile (bottom, fixed)
```

### Badge Sizes
All badges: `inline-flex`, `font-size: 11px`, `font-weight: 600`, `padding: 3px 8px`, `border-radius: 999px`, colored dot prefix `●`

---

## Screen Inventory

### Backoffice Web Screens (`designs/screens/backoffice/`)

| File | Screen | Status |
|------|--------|--------|
| `dashboard.html` | Dashboard — KPIs, transactions, loan portfolio, charts | ✅ Done |
| `customers.html` | Customer list, KYC status, search/filter | ✅ Done |
| `loans.html` | Loan pipeline, active loans, repayment detail panel | ✅ Done |
| `accounts.html` | Account list and detail | Scaffold when invoked |
| `payments.html` | Transfer, history, standing orders | Scaffold when invoked |
| `reports.html` | Statement generator, export | Scaffold when invoked |
| `login.html` | Auth — matches Nubeero Figma exactly | Scaffold when invoked |

### Mobile Screens (Flutter spec, `designs/screens/mobile/`)
Scaffold these when implementing Flutter:
- Splash / Login / Biometric auth
- Home dashboard (balance summary, quick actions)
- Account detail + transaction list
- Transfer / Pay bill
- Loan detail + repayment schedule
- Profile / Settings

---

## Angular Component Map

| Design Component | Angular Component | Module |
|-----------------|-------------------|--------|
| Sidebar nav | `SidebarComponent` | `LayoutModule` |
| Topbar | `TopbarComponent` | `LayoutModule` |
| KPI card | `KpiCardComponent` | `SharedModule` |
| Data table | `DataTableComponent` | `SharedModule` |
| Status badge | `StatusBadgeComponent` | `SharedModule` |
| Search bar | `SearchInputComponent` | `SharedModule` |
| Primary button | Angular Material `mat-button` + custom theme | `MaterialModule` |
| Input field | Angular Material `mat-form-field` + custom theme | `MaterialModule` |
| Loan detail panel | `LoanDetailPanelComponent` | `LoansModule` |
| Progress bar | Angular Material `mat-progress-bar` + Nubeero theme | `MaterialModule` |
| Chart | `ngx-charts` or `Chart.js` via `ng2-charts` | `ChartsModule` |

---

## Angular Material Theme (Nubeero)

```scss
// src/styles/theme.scss
@use '@angular/material' as mat;

$cba-primary: mat.define-palette((
  50: #eef4f8,
  100: #d9e6f0,
  500: #4a6c93,
  700: #2a3a4d,
  900: #1e2833,
  contrast: (900: white)
), 900, 700, 500);

$cba-theme: mat.define-light-theme((
  color: (
    primary: $cba-primary,
    accent:  mat.define-palette(mat.$blue-palette),
    warn:    mat.define-palette(mat.$red-palette)
  ),
  typography: mat.define-typography-config(
    $font-family: 'Instrument Sans, system-ui, sans-serif'
  )
));
```

---

## Stitch Design Page: "CoreBanking-Nubeero"

**Design Engine**: [Stitch](https://stitch.design) — AI-powered design tool. All UI screens for this project are created and maintained in Stitch. Figma is no longer used.

**Target Stitch project**: `CoreBanking-Nubeero` in the Nubeero workspace.

**Sections to create** (as artboards/frames in Stitch):
1. `Design Tokens` — color swatches, typography scale, spacing grid, shadows
2. `Auth` — Login, Forgot Password, OTP, Reset Password
3. `Backoffice / Dashboard` — KPI cards, transaction table, charts
4. `Backoffice / Customers` — List view, detail/KYC panel
5. `Backoffice / Accounts` — Account list, account detail
6. `Backoffice / Loans` — Pipeline view, loan list, repayment detail
7. `Backoffice / Payments` — Transfer flow, history
8. `Backoffice / Reports` — Report generator, export
9. `Mobile / Customer App` — Splash, Home, Accounts, Loans, Transfer, Profile

**Workflow**:
1. Use the HTML prototypes in `designs/screens/backoffice/` as pixel-level references
2. Apply tokens from `designs/tokens.scss` when building components in Stitch
3. Export component specs from Stitch for handoff to Angular and Flutter teams
4. All design tokens, component variants, and screen flows live in Stitch — not Figma

**Note**: The Nubeero design token set (`designs/tokens.scss`) is the source of truth. Stitch should reflect these exact values — do not derive colors or spacing from Stitch independently.
