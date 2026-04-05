# CoreBanking-Nubeero Figma Plugin

Creates the complete Nubeero design system in Figma with 9 pages:
- 🎨 Design Tokens — Color swatches, typography, spacing, buttons, badges
- 🏠 Backoffice / Dashboard — KPI cards, transaction table, loan portfolio
- 👥 Backoffice / Customers — Stats, customer table with KYC badges
- 🏦 Backoffice / Loans — Pipeline stages, active loans, detail panel
- 💳 Backoffice / Accounts — (placeholder)
- ↔️ Backoffice / Payments — (placeholder)
- 📊 Backoffice / Reports — (placeholder)
- 🔑 Auth / Login — (placeholder)
- 📱 Mobile / Customer App — (placeholder)

## How to Run (One-time Setup)

### Option A — Via Figma Desktop (Recommended)

1. Open **Figma desktop app** and create a new empty file
2. Name it: `CoreBanking-Nubeero`
3. Go to: **Menu → Plugins → Development → Import plugin from manifest...**
4. Select the `manifest.json` file from this folder
5. Go to: **Menu → Plugins → Development → CoreBanking-Nubeero**
6. The plugin runs automatically — all 9 pages are created in ~15 seconds

### Option B — Figma Plugin Console (No install needed)

1. Open any Figma file in the desktop or web app
2. Press `Ctrl+/` (or `Cmd+/` on Mac) → search "Open console"
3. Copy the entire contents of `code.js`
4. Paste into the Figma Plugin console and press Enter
   > Note: You may need to wrap in `(async () => { /* paste code here */ })()` if needed

### Option C — Publish to Figma Team Library (Optional)

1. Follow Option A to run once
2. In Figma, go to: **Assets panel → Publish styles and components**
3. The Nubeero tokens become shared across the Nubeero Pro workspace

## What Gets Created

```
CoreBanking-Nubeero.fig
├── Page 1: 🎨 Design Tokens
│   ├── Color swatches (12 tokens with names and hex values)
│   ├── Typography scale (Display → Caps, 6 levels)
│   ├── Spacing grid (4px → 64px)
│   ├── Border radius examples (8px, 12px, 9999px)
│   ├── Button styles (Primary pill, Secondary, Danger)
│   └── Status badges (Approved, Pending, Suspended, Info, Neutral)
│
├── Page 2: 🏠 Dashboard (1440 × 900)
│   ├── Sidebar (260px, #0a1628)
│   ├── Topbar (64px, white)
│   ├── 4 KPI cards (customers, accounts, loans, transactions)
│   ├── Recent Transactions table (5 rows)
│   └── Loan Portfolio widget (progress bars)
│
├── Page 3: 👥 Customers (1440 × 960)
│   ├── Toolbar (search + 3 filters + New Customer button)
│   ├── 4 stat cards (total, approved, pending, suspended)
│   ├── Customer table (5 rows, KYC badges, avatars)
│   └── Pagination
│
└── Page 4: 🏦 Loans (1440 × 900)
    ├── 4 KPI cards (portfolio, disbursed, at-risk, write-offs)
    ├── Loan Pipeline (5 stages with colored indicators)
    ├── Active Loans table (5 rows, status badges)
    └── Loan Detail panel (repayment schedule)
```

## Screenshots (already captured)

High-resolution HTML prototypes are in `../screenshots/`:
- `dashboard.png` — 1440×900 render of the dashboard screen
- `customers.png` — 1440×900 render of the customers screen
- `loans.png` — 1440×900 render of the loans screen

Use these as reference when reviewing the Figma plugin output. To import them as image fills:
1. In Figma, select any frame
2. Fill → Image → choose screenshot file

## Notes

- The plugin uses **Inter** (Figma's built-in font) since `Instrument Sans` requires a
  Google Fonts connection. After running, select all text nodes and change font to
  `Instrument Sans` if the Nubeero font is loaded in your workspace.
- Placeholder pages are created for accounts, payments, reports, login, and mobile.
  Run the CBA skill's Phase D to scaffold those additional screens.

## Architecture Diagram (FigJam)

Already created in Figma:
https://www.figma.com/online-whiteboard/create-diagram/e885ae04-d781-4c9c-ac0e-5cd8bcf17daa
