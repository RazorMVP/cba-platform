// CoreBanking-Nubeero Figma Plugin
// Creates the complete design system structure in Figma
// Run via: Plugins → Development → Run Last Plugin (after loading manifest)

// ─────────────────────────────────────────────────
// DESIGN TOKENS — Nubeero Brand
// ─────────────────────────────────────────────────
const T = {
  // Backgrounds
  bgApp:      { r: 0.016, g: 0.024, b: 0.035, a: 1 },   // #040609
  bgSidebar:  { r: 0.039, g: 0.086, b: 0.157, a: 1 },   // #0a1628
  bgCard:     { r: 1,     g: 1,     b: 1,     a: 1 },   // #ffffff
  bgSubtle:   { r: 0.957, g: 0.961, b: 0.969, a: 1 },   // #f4f5f7
  bgContent:  { r: 0.941, g: 0.949, b: 0.961, a: 1 },   // #f0f2f5
  // Brand
  primary:    { r: 0.118, g: 0.157, b: 0.200, a: 1 },   // #1e2833
  primaryHov: { r: 0.165, g: 0.227, b: 0.302, a: 1 },   // #2a3a4d
  // Text
  textMain:   { r: 0,     g: 0.012, b: 0.078, a: 1 },   // #000314
  textMuted:  { r: 0.533, g: 0.533, b: 0.533, a: 1 },   // #888888
  textPH:     { r: 0.494, g: 0.506, b: 0.529, a: 1 },   // #7E8187
  // Semantic
  success:    { r: 0.086, g: 0.639, b: 0.290, a: 1 },   // #16a34a
  successBg:  { r: 0.863, g: 0.988, b: 0.906, a: 1 },   // #dcfce7
  warning:    { r: 0.792, g: 0.541, b: 0.016, a: 1 },   // #ca8a04
  warningBg:  { r: 0.996, g: 0.976, b: 0.765, a: 1 },   // #fef9c3
  error:      { r: 0.863, g: 0.149, b: 0.149, a: 1 },   // #dc2626
  errorBg:    { r: 0.996, g: 0.886, b: 0.886, a: 1 },   // #fee2e2
  info:       { r: 0.145, g: 0.380, b: 0.922, a: 1 },   // #2563eb
  infoBg:     { r: 0.859, g: 0.929, b: 1,     a: 1 },   // #dbeafe
  // Misc
  white:      { r: 1, g: 1, b: 1, a: 1 },
  border:     { r: 0.184, g: 0.169, b: 0.263, a: 0.10 },
  navItem:    { r: 1, g: 1, b: 1, a: 0.55 },
  navActive:  { r: 1, g: 1, b: 1, a: 0.10 },
};

// ─────────────────────────────────────────────────
// HELPER FUNCTIONS
// ─────────────────────────────────────────────────

function rgb(c) { return [{ type: 'SOLID', color: { r: c.r, g: c.g, b: c.b }, opacity: c.a }]; }

function rgba(c) { return [{ type: 'SOLID', color: { r: c.r, g: c.g, b: c.b }, opacity: c.a }]; }

function frame(name, x, y, w, h) {
  const f = figma.createFrame();
  f.name = name;
  f.x = x; f.y = y;
  f.resize(w, h);
  f.clipsContent = true;
  return f;
}

function rect(name, x, y, w, h, color) {
  const r = figma.createRectangle();
  r.name = name;
  r.x = x; r.y = y;
  r.resize(w, h);
  r.fills = rgb(color);
  return r;
}

async function text(content, x, y, size, weight, color, parent) {
  const t = figma.createText();
  await figma.loadFontAsync({ family: 'Inter', style: weight === 700 ? 'Bold' : weight === 600 ? 'SemiBold' : weight === 500 ? 'Medium' : 'Regular' });
  t.fontName = { family: 'Inter', style: weight === 700 ? 'Bold' : weight === 600 ? 'SemiBold' : weight === 500 ? 'Medium' : 'Regular' };
  t.characters = content;
  t.fontSize = size;
  t.fills = rgb(color);
  t.x = x; t.y = y;
  if (parent) parent.appendChild(t);
  return t;
}

function badge(label, bgColor, textColor, x, y, parent) {
  const g = figma.createFrame();
  g.name = 'Badge / ' + label;
  g.x = x; g.y = y;
  g.resize(80, 22);
  g.cornerRadius = 999;
  g.fills = rgb(bgColor);
  g.layoutMode = 'HORIZONTAL';
  g.paddingLeft = 8; g.paddingRight = 8;
  g.primaryAxisAlignItems = 'CENTER';
  g.counterAxisAlignItems = 'CENTER';
  if (parent) parent.appendChild(g);
  return g;
}

// ─────────────────────────────────────────────────
// SIDEBAR COMPONENT
// ─────────────────────────────────────────────────

async function createSidebar(parent, screenH) {
  const sb = frame('Sidebar', 0, 0, 260, screenH);
  sb.fills = rgb(T.bgSidebar);
  parent.appendChild(sb);

  // Logo area
  const logo = rect('Logo Area', 0, 0, 260, 64);
  logo.fills = [{ type: 'SOLID', color: T.bgSidebar }];
  sb.appendChild(logo);
  await text('⬛ CoreBanking', 24, 20, 16, 600, T.white, sb);

  // Separator
  const sep = rect('Logo Sep', 24, 68, 212, 1);
  sep.fills = [{ type: 'SOLID', color: { r: 1, g: 1, b: 1 }, opacity: 0.08 }];
  sb.appendChild(sep);

  // Nav items
  const navItems = [
    { label: 'Dashboard',  active: false, y: 88  },
    { label: 'Customers',  active: false, y: 128 },
    { label: 'Accounts',   active: false, y: 168 },
    { label: 'Loans',      active: false, y: 208 },
    { label: 'Payments',   active: false, y: 248 },
    { label: 'Products',   active: false, y: 288 },
    { label: 'Reports',    active: false, y: 328 },
    { label: 'Settings',   active: false, y: screenH - 60 },
  ];

  for (const item of navItems) {
    const nav = frame('Nav / ' + item.label, 12, item.y, 236, 36);
    nav.cornerRadius = 8;
    nav.fills = item.active ? rgba(T.navActive) : [{ type: 'SOLID', color: T.bgSidebar }];
    sb.appendChild(nav);
    await text(item.label, 12, 9, 14, 500, item.active ? T.white : T.navItem, nav);
  }

  return sb;
}

// ─────────────────────────────────────────────────
// TOPBAR COMPONENT
// ─────────────────────────────────────────────────

async function createTopbar(parent, pageTitle, subtitle, x, w) {
  const tb = frame('Topbar', x, 0, w, 64);
  tb.fills = rgb(T.bgCard);
  const bord = rect('Topbar Border', 0, 63, w, 1);
  bord.fills = rgba(T.border);
  tb.appendChild(bord);
  parent.appendChild(tb);
  await text(pageTitle, 24, 14, 18, 600, T.textMain, tb);
  if (subtitle) await text(subtitle, 24, 40, 13, 400, T.textMuted, tb);
  return tb;
}

// ─────────────────────────────────────────────────
// KPI CARD COMPONENT
// ─────────────────────────────────────────────────

async function createKpiCard(parent, label, value, sub, subColor, x, y) {
  const card = frame('KPI / ' + label, x, y, 264, 96);
  card.cornerRadius = 12;
  card.fills = rgb(T.bgCard);
  card.strokeWeight = 1;
  card.strokes = rgba(T.border);
  parent.appendChild(card);

  await text(label.toUpperCase(), 20, 16, 11, 600, T.textMuted, card);
  await text(value, 20, 34, 24, 700, T.textMain, card);
  await text(sub, 20, 70, 12, 400, subColor, card);
  return card;
}

// ─────────────────────────────────────────────────
// TABLE HEADER
// ─────────────────────────────────────────────────

async function createTableHeader(parent, columns, x, y, w) {
  const hdr = frame('Table Header', x, y, w, 36);
  hdr.fills = rgb(T.bgSubtle);
  const bord = rect('Hdr Border', 0, 35, w, 1);
  bord.fills = rgba(T.border);
  hdr.appendChild(bord);
  parent.appendChild(hdr);

  let cx = 20;
  for (const col of columns) {
    await text(col.label.toUpperCase(), cx, 12, 11, 600, T.textMuted, hdr);
    cx += col.width;
  }
  return hdr;
}

// ─────────────────────────────────────────────────
// TABLE ROW
// ─────────────────────────────────────────────────

async function createTableRow(parent, cells, columns, x, y, w) {
  const row = frame('Table Row', x, y, w, 48);
  row.fills = rgb(T.bgCard);
  const bord = rect('Row Border', 0, 47, w, 1);
  bord.fills = [{ type: 'SOLID', color: { r: 0, g: 0, b: 0 }, opacity: 0.04 }];
  row.appendChild(bord);
  parent.appendChild(row);

  let cx = 20;
  for (let i = 0; i < cells.length; i++) {
    const col = columns[i];
    await text(cells[i], cx, 15, 13, 400, T.textMain, row);
    cx += col.width;
  }
  return row;
}

// ─────────────────────────────────────────────────
// PAGE: DESIGN TOKENS
// ─────────────────────────────────────────────────

async function createTokensPage(page) {
  page.name = '🎨 Design Tokens';
  figma.currentPage = page;

  const titleFrame = frame('Design Tokens', 0, 0, 1440, 3600);
  titleFrame.fills = [{ type: 'SOLID', color: { r: 0.97, g: 0.97, b: 0.97 } }];
  page.appendChild(titleFrame);

  await text('Nubeero Design System', 60, 60, 36, 700, T.textMain, titleFrame);
  await text('CoreBanking • Backoffice & Mobile', 60, 110, 16, 400, T.textMuted, titleFrame);

  // ── Color Swatches ──
  await text('COLORS', 60, 180, 11, 700, T.textMuted, titleFrame);

  const swatches = [
    { name: 'App Background',  hex: '#040609', color: T.bgApp,    dark: true  },
    { name: 'Sidebar',         hex: '#0a1628', color: T.bgSidebar, dark: true  },
    { name: 'Card White',      hex: '#ffffff', color: T.bgCard,    dark: false },
    { name: 'Subtle BG',       hex: '#f4f5f7', color: T.bgSubtle,  dark: false },
    { name: 'Primary',         hex: '#1e2833', color: T.primary,   dark: true  },
    { name: 'Primary Hover',   hex: '#2a3a4d', color: T.primaryHov,dark: true  },
    { name: 'Text',            hex: '#000314', color: T.textMain,  dark: true  },
    { name: 'Muted',           hex: '#888888', color: T.textMuted, dark: true  },
    { name: 'Success',         hex: '#16a34a', color: T.success,   dark: true  },
    { name: 'Warning',         hex: '#ca8a04', color: T.warning,   dark: true  },
    { name: 'Error',           hex: '#dc2626', color: T.error,     dark: true  },
    { name: 'Info',            hex: '#2563eb', color: T.info,      dark: true  },
  ];

  let sx = 60, sy = 210;
  for (let i = 0; i < swatches.length; i++) {
    const sw = swatches[i];
    const swatch = frame('Swatch / ' + sw.name, sx + (i % 6) * 220, sy + Math.floor(i / 6) * 160, 200, 140);
    swatch.cornerRadius = 12;
    swatch.fills = rgb(sw.color);
    titleFrame.appendChild(swatch);

    const textColor = sw.dark ? T.white : T.textMain;
    await text(sw.name, 16, 90, 13, 600, textColor, swatch);
    await text(sw.hex, 16, 112, 12, 400, sw.dark ? { r: 1, g: 1, b: 1, a: 0.7 } : T.textMuted, swatch);
  }

  // ── Typography Scale ──
  const typoY = 570;
  await text('TYPOGRAPHY — Instrument Sans', 60, typoY, 11, 700, T.textMuted, titleFrame);

  const typoScales = [
    { label: 'Display / 36px Bold',  size: 36, weight: 700, sample: 'KPI Value 24,831' },
    { label: 'H1 / 24px SemiBold',   size: 24, weight: 600, sample: 'Page Title' },
    { label: 'H2 / 18px SemiBold',   size: 18, weight: 600, sample: 'Section Heading' },
    { label: 'Body / 16px Regular',  size: 16, weight: 400, sample: 'Body text and labels' },
    { label: 'Small / 13px Regular', size: 13, weight: 400, sample: 'Table data, secondary text' },
    { label: 'Caps / 11px Bold',     size: 11, weight: 700, sample: 'COLUMN HEADER, BADGE LABEL' },
  ];

  let ty = typoY + 32;
  for (const t of typoScales) {
    await text(t.label, 60, ty, 10, 500, T.textMuted, titleFrame);
    await text(t.sample, 60, ty + 14, t.size, t.weight, T.textMain, titleFrame);
    ty += Math.max(t.size + 40, 60);
  }

  // ── Spacing ──
  const spacingY = ty + 40;
  await text('SPACING — 8px base grid', 60, spacingY, 11, 700, T.textMuted, titleFrame);

  const spacings = [4, 8, 12, 16, 20, 24, 32, 48, 64];
  let spx = 60;
  for (const sp of spacings) {
    const spBox = rect('Spacing/' + sp, spx, spacingY + 30, sp, sp);
    spBox.fills = rgb(T.primary);
    titleFrame.appendChild(spBox);
    await text(sp + 'px', spx, spacingY + 30 + sp + 6, 10, 500, T.textMuted, titleFrame);
    spx += sp + 40;
  }

  // ── Border Radius ──
  const radY = spacingY + 130;
  await text('BORDER RADIUS', 60, radY, 11, 700, T.textMuted, titleFrame);
  const radii = [{ r: 8, label: 'Tags & Buttons' }, { r: 12, label: 'Cards & Inputs' }, { r: 24, label: 'Pill CTA' }];
  let rx = 60;
  for (const rad of radii) {
    const box = frame('Radius/' + rad.r, rx, radY + 30, 80, 80);
    box.cornerRadius = rad.r;
    box.fills = rgb(T.bgSubtle);
    box.strokeWeight = 1;
    box.strokes = rgba(T.border);
    titleFrame.appendChild(box);
    await text(rad.r + 'px', rx + 4, radY + 30 + 86, 10, 500, T.textMuted, titleFrame);
    await text(rad.label, rx + 4, radY + 30 + 98, 10, 400, T.textMuted, titleFrame);
    rx += 140;
  }

  // ── Button Styles ──
  const btnY = radY + 200;
  await text('BUTTON STYLES', 60, btnY, 11, 700, T.textMuted, titleFrame);

  // Primary button
  const primaryBtn = frame('Button / Primary', 60, btnY + 30, 160, 48);
  primaryBtn.cornerRadius = 9999;
  primaryBtn.fills = rgb(T.primary);
  titleFrame.appendChild(primaryBtn);
  await text('Save Changes', 32, 14, 16, 500, T.white, primaryBtn);

  // Secondary button
  const secondaryBtn = frame('Button / Secondary', 240, btnY + 30, 160, 48);
  secondaryBtn.cornerRadius = 8;
  secondaryBtn.fills = rgb(T.bgCard);
  secondaryBtn.strokeWeight = 1;
  secondaryBtn.strokes = rgba(T.border);
  titleFrame.appendChild(secondaryBtn);
  await text('Cancel', 48, 14, 16, 400, T.textMain, secondaryBtn);

  // Danger button
  const dangerBtn = frame('Button / Danger', 420, btnY + 30, 160, 48);
  dangerBtn.cornerRadius = 8;
  dangerBtn.fills = [{ type: 'SOLID', color: { r: 1, g: 0.961, b: 0.961 } }];
  dangerBtn.strokeWeight = 1;
  dangerBtn.strokes = [{ type: 'SOLID', color: { r: 0.996, g: 0.886, b: 0.886 } }];
  titleFrame.appendChild(dangerBtn);
  await text('Delete Account', 16, 14, 16, 400, T.error, dangerBtn);

  // ── Status Badges ──
  const badgeY = btnY + 130;
  await text('STATUS BADGES', 60, badgeY, 11, 700, T.textMuted, titleFrame);
  const badges = [
    { label: 'Approved',  bg: T.successBg, fg: T.success  },
    { label: 'Pending',   bg: T.warningBg, fg: T.warning  },
    { label: 'Suspended', bg: T.errorBg,   fg: T.error    },
    { label: '3 accounts',bg: T.infoBg,    fg: T.info     },
    { label: 'Active',    bg: T.bgSubtle,  fg: T.textMuted },
  ];
  let bdx = 60;
  for (const bd of badges) {
    const bdg = frame('Badge/' + bd.label, bdx, badgeY + 28, 100, 24);
    bdg.cornerRadius = 999;
    bdg.fills = rgb(bd.bg);
    titleFrame.appendChild(bdg);
    await text(bd.label, 12, 5, 11, 600, bd.fg, bdg);
    bdx += 120;
  }
}

// ─────────────────────────────────────────────────
// PAGE: DASHBOARD
// ─────────────────────────────────────────────────

async function createDashboardPage(page) {
  page.name = '🏠 Backoffice / Dashboard';
  figma.currentPage = page;

  const W = 1440, H = 900;
  const screen = frame('Dashboard Screen', 0, 0, W, H);
  screen.fills = rgb(T.bgApp);
  page.appendChild(screen);

  // Sidebar
  await createSidebar(screen, H);

  // Main area
  const mainW = W - 260;
  const mainArea = frame('Main Area', 260, 0, mainW, H);
  mainArea.fills = rgb(T.bgApp);
  screen.appendChild(mainArea);

  // Topbar
  await createTopbar(mainArea, 'Dashboard', '25 March 2025', 0, mainW);

  // Content area
  const content = frame('Content', 0, 64, mainW, H - 64);
  content.fills = rgb(T.bgContent);
  mainArea.appendChild(content);

  const cPad = 24;

  // KPI Cards
  const kpis = [
    { label: 'Total Customers',  value: '24,831',  sub: '↑ 8.2% this month',  subC: T.success },
    { label: 'Active Accounts',  value: '31,204',  sub: '↑ 5.1% this month',  subC: T.success },
    { label: 'Total Loans',      value: '$48.7M',  sub: '↓ 2.3% this quarter',subC: T.error   },
    { label: 'Total Transactions',value: '3,847',  sub: 'Today',               subC: T.textMuted},
  ];

  for (let i = 0; i < kpis.length; i++) {
    await createKpiCard(content, kpis[i].label, kpis[i].value, kpis[i].sub, kpis[i].subC,
      cPad + i * (264 + 16), cPad);
  }

  // Recent Transactions Table
  const tableY = 24 + 96 + 24;
  const tableW = mainW - cPad * 2 - 360 - 16;

  const tableCard = frame('Table / Recent Transactions', cPad, tableY, tableW, 400);
  tableCard.cornerRadius = 12;
  tableCard.fills = rgb(T.bgCard);
  tableCard.strokeWeight = 1;
  tableCard.strokes = rgba(T.border);
  content.appendChild(tableCard);

  // Table header row
  const tableHeader = frame('Table Header Row', 0, 0, tableW, 52);
  tableHeader.fills = rgb(T.bgCard);
  const headerBord = rect('Header Border', 0, 51, tableW, 1);
  headerBord.fills = rgba(T.border);
  tableHeader.appendChild(headerBord);
  tableCard.appendChild(tableHeader);
  await text('Recent Transactions', 20, 18, 14, 600, T.textMain, tableHeader);

  // Column headers
  const cols = [
    { label: 'Customer',    width: 180 },
    { label: 'Amount',      width: 120 },
    { label: 'Type',        width: 130 },
    { label: 'Status',      width: 110 },
    { label: 'Date',        width: 100 },
  ];
  await createTableHeader(tableCard, cols, 0, 52, tableW);

  // Table rows
  const txRows = [
    ['Kwame Mensah',   '+₵1,500.00', 'Transfer In',  'Completed', 'Jan 15'],
    ['Amara Diallo',   '-$2,000.00', 'Loan Repay',   'Completed', 'Jan 14'],
    ['Fatima Al-Hassan','+£800.00',  'Deposit',       'Pending',   'Jan 13'],
    ['Emmanuel Osei',  '-₦50,000',  'Withdrawal',    'Flagged',   'Jan 12'],
    ['Ngozi Okonkwo',  '+$3,200.00', 'Transfer In',  'Completed', 'Jan 11'],
  ];

  for (let i = 0; i < txRows.length; i++) {
    await createTableRow(tableCard, txRows[i], cols, 0, 88 + i * 48, tableW);
  }

  // Loan Portfolio Widget
  const loanW = 344;
  const loanCard = frame('Widget / Loan Portfolio', cPad + tableW + 16, tableY, loanW, 400);
  loanCard.cornerRadius = 12;
  loanCard.fills = rgb(T.bgCard);
  loanCard.strokeWeight = 1;
  loanCard.strokes = rgba(T.border);
  content.appendChild(loanCard);

  await text('Loan Portfolio', 20, 18, 14, 600, T.textMain, loanCard);

  const loanItems = [
    { label: 'Current (0-30 days)',  pct: 82, color: T.success },
    { label: '30-60 days past due',  pct: 9,  color: T.warning },
    { label: '60-90 days past due',  pct: 6,  color: T.error   },
    { label: '90+ days (write-off)', pct: 3,  color: T.textMuted },
  ];

  for (let i = 0; i < loanItems.length; i++) {
    const li = loanItems[i];
    const ly = 60 + i * 64;
    await text(li.label, 20, ly, 12, 400, T.textMain, loanCard);
    await text(li.pct + '%', loanW - 50, ly, 14, 600, li.color, loanCard);
    // Progress bar track
    const track = rect('Track/' + i, 20, ly + 22, loanW - 40, 8);
    track.cornerRadius = 4;
    track.fills = rgb(T.bgSubtle);
    loanCard.appendChild(track);
    // Progress bar fill
    const fill = rect('Fill/' + i, 20, ly + 22, Math.round((loanW - 40) * li.pct / 100), 8);
    fill.cornerRadius = 4;
    fill.fills = rgb(li.color);
    loanCard.appendChild(fill);
  }
}

// ─────────────────────────────────────────────────
// PAGE: CUSTOMERS
// ─────────────────────────────────────────────────

async function createCustomersPage(page) {
  page.name = '👥 Backoffice / Customers';
  figma.currentPage = page;

  const W = 1440, H = 960;
  const screen = frame('Customers Screen', 0, 0, W, H);
  screen.fills = rgb(T.bgApp);
  page.appendChild(screen);

  // Sidebar
  await createSidebar(screen, H);

  // Main
  const mainW = W - 260;
  const mainArea = frame('Main Area', 260, 0, mainW, H);
  mainArea.fills = rgb(T.bgApp);
  screen.appendChild(mainArea);

  // Topbar
  await createTopbar(mainArea, 'Customer Management', 'Manage customer profiles, KYC, and accounts', 0, mainW);

  // Toolbar
  const toolbar = frame('Toolbar', 0, 64, mainW, 56);
  toolbar.fills = rgb(T.bgCard);
  const tbBord = rect('Toolbar Border', 0, 55, mainW, 1);
  tbBord.fills = rgba(T.border);
  toolbar.appendChild(tbBord);
  mainArea.appendChild(toolbar);

  // Search bar
  const searchBox = frame('Search Bar', 24, 8, 360, 40);
  searchBox.cornerRadius = 8;
  searchBox.fills = rgb(T.bgSubtle);
  searchBox.strokeWeight = 1;
  searchBox.strokes = rgba(T.border);
  toolbar.appendChild(searchBox);
  await text('🔍 Search by name, ID, email, phone...', 12, 11, 13, 400, T.textPH, searchBox);

  // Filter buttons
  const filters = ['Date Range', 'KYC Status', 'Account Type'];
  for (let i = 0; i < filters.length; i++) {
    const fb = frame('Filter/' + filters[i], 400 + i * 130, 8, 120, 40);
    fb.cornerRadius = 8;
    fb.fills = rgb(T.bgCard);
    fb.strokeWeight = 1;
    fb.strokes = rgba(T.border);
    toolbar.appendChild(fb);
    await text(filters[i], 12, 11, 13, 500, T.textMain, fb);
  }

  // New Customer button
  const newBtn = frame('Button / New Customer', mainW - 160, 8, 136, 40);
  newBtn.cornerRadius = 9999;
  newBtn.fills = rgb(T.primary);
  toolbar.appendChild(newBtn);
  await text('+ New Customer', 16, 11, 13, 500, T.white, newBtn);

  // Content
  const content = frame('Content', 0, 120, mainW, H - 120);
  content.fills = rgb(T.bgContent);
  mainArea.appendChild(content);

  // Stats row
  const statCards = [
    { label: 'Total Customers', value: '24,831', sub: '↑ 8.2% this month', subC: T.success },
    { label: 'KYC Approved',    value: '23,104', sub: '93.0% approval rate', subC: T.textMuted },
    { label: 'Pending KYC',     value: '1,412',  sub: 'Awaiting review', subC: T.warning, valC: T.warning },
    { label: 'Suspended',       value: '315',    sub: '↑ 12 this week', subC: T.error, valC: T.error },
  ];

  for (let i = 0; i < statCards.length; i++) {
    const sc = statCards[i];
    const card = frame('Stat/' + sc.label, 24 + i * (264 + 16), 24, 264, 90);
    card.cornerRadius = 12;
    card.fills = rgb(T.bgCard);
    card.strokeWeight = 1;
    card.strokes = rgba(T.border);
    content.appendChild(card);
    await text(sc.label.toUpperCase(), 20, 14, 11, 600, T.textMuted, card);
    await text(sc.value, 20, 32, 24, 600, sc.valC || T.textMain, card);
    await text(sc.sub, 20, 66, 12, 400, sc.subC, card);
  }

  // Customer Table
  const tableW = mainW - 48;
  const tableCard = frame('Table / Customers', 24, 138, tableW, 600);
  tableCard.cornerRadius = 12;
  tableCard.fills = rgb(T.bgCard);
  tableCard.strokeWeight = 1;
  tableCard.strokes = rgba(T.border);
  content.appendChild(tableCard);

  // Table title + tabs
  const tableHdr = frame('Table Title Row', 0, 0, tableW, 52);
  tableHdr.fills = rgb(T.bgCard);
  const thBord = rect('Hdr Border', 0, 51, tableW, 1);
  thBord.fills = rgba(T.border);
  tableHdr.appendChild(thBord);
  tableCard.appendChild(tableHdr);
  await text('All Customers', 20, 18, 14, 600, T.textMain, tableHdr);

  // Tab group
  const tabs = ['All', 'Active', 'Pending KYC', 'Suspended'];
  const tabGroup = frame('Tabs', tableW - 320, 10, 300, 32);
  tabGroup.cornerRadius = 8;
  tabGroup.strokeWeight = 1;
  tabGroup.strokes = rgba(T.border);
  tabGroup.fills = rgb(T.bgCard);
  tabGroup.layoutMode = 'HORIZONTAL';
  tableHdr.appendChild(tabGroup);

  for (let i = 0; i < tabs.length; i++) {
    const tab = frame('Tab/' + tabs[i], 0, 0, 72, 32);
    tab.fills = i === 0 ? rgb(T.primary) : rgb(T.bgCard);
    tab.layoutMode = 'HORIZONTAL';
    tab.primaryAxisAlignItems = 'CENTER';
    tab.counterAxisAlignItems = 'CENTER';
    tabGroup.appendChild(tab);
    await text(tabs[i], 8, 8, 12, 500, i === 0 ? T.white : T.textMuted, tab);
  }

  // Column headers
  const custCols = [
    { label: 'Customer',    width: 220 },
    { label: 'Customer ID', width: 130 },
    { label: 'Phone',       width: 150 },
    { label: 'Accounts',    width: 110 },
    { label: 'KYC Status',  width: 110 },
    { label: 'Joined',      width: 100 },
    { label: 'Actions',     width: 80  },
  ];
  await createTableHeader(tableCard, custCols, 0, 52, tableW);

  // Customer rows
  const customers = [
    { name: 'Kwame Mensah',    id: 'CUS-0012345', phone: '+233 24 123 4567', accts: '3 accounts', kyc: 'Approved',  date: 'Jan 15, 2025' },
    { name: 'Amara Diallo',    id: 'CUS-0012344', phone: '+221 77 234 5678', accts: '1 account',  kyc: 'Approved',  date: 'Jan 14, 2025' },
    { name: 'Fatima Al-Hassan',id: 'CUS-0012343', phone: '+234 80 345 6789', accts: '2 accounts', kyc: 'Pending',   date: 'Jan 13, 2025' },
    { name: 'Emmanuel Osei',   id: 'CUS-0012342', phone: '+233 20 456 7890', accts: '0 accounts', kyc: 'Suspended', date: 'Jan 12, 2025' },
    { name: 'Ngozi Okonkwo',   id: 'CUS-0012341', phone: '+234 81 567 8901', accts: '4 accounts', kyc: 'Approved',  date: 'Jan 11, 2025' },
  ];

  const kycColors = { 'Approved': { bg: T.successBg, fg: T.success }, 'Pending': { bg: T.warningBg, fg: T.warning }, 'Suspended': { bg: T.errorBg, fg: T.error } };
  const acctColors = { bg: T.infoBg, fg: T.info };

  for (let i = 0; i < customers.length; i++) {
    const c = customers[i];
    const row = frame('Customer Row / ' + c.name, 0, 88 + i * 52, tableW, 52);
    row.fills = rgb(T.bgCard);
    const rb = rect('Row Border', 0, 51, tableW, 1);
    rb.fills = [{ type: 'SOLID', color: { r: 0, g: 0, b: 0 }, opacity: 0.04 }];
    row.appendChild(rb);
    tableCard.appendChild(row);

    await text(c.name, 20, 17, 13, 500, T.textMain, row);
    await text(c.id, 240, 17, 12, 400, T.textPH, row);
    await text(c.phone, 370, 17, 13, 400, T.textMain, row);

    // Account badge
    const ab = frame('Badge/Accts', 520, 14, 90, 22);
    ab.cornerRadius = 999;
    ab.fills = rgb(acctColors.bg);
    row.appendChild(ab);
    await text(c.accts, 8, 4, 11, 600, acctColors.fg, ab);

    // KYC badge
    const kc = kycColors[c.kyc];
    const kb = frame('Badge/KYC', 630, 14, 88, 22);
    kb.cornerRadius = 999;
    kb.fills = rgb(kc.bg);
    row.appendChild(kb);
    await text(c.kyc, 8, 4, 11, 600, kc.fg, kb);

    await text(c.date, 740, 17, 12, 400, T.textMuted, row);

    // Action button
    const actBtn = frame('Action/View', tableW - 80, 12, 60, 28);
    actBtn.cornerRadius = 6;
    actBtn.fills = rgb(T.bgCard);
    actBtn.strokeWeight = 1;
    actBtn.strokes = rgba(T.border);
    row.appendChild(actBtn);
    await text('View →', 10, 6, 12, 500, T.textMain, actBtn);
  }

  // Pagination
  const pagination = frame('Pagination', 0, 608, tableW, 48);
  pagination.fills = rgb(T.bgSubtle);
  const pgBord = rect('Pag Border', 0, 0, tableW, 1);
  pgBord.fills = rgba(T.border);
  pagination.appendChild(pgBord);
  tableCard.appendChild(pagination);
  await text('Showing 1–20 of 24,831 customers', 20, 14, 13, 400, T.textMuted, pagination);
}

// ─────────────────────────────────────────────────
// PAGE: LOANS
// ─────────────────────────────────────────────────

async function createLoansPage(page) {
  page.name = '🏦 Backoffice / Loans';
  figma.currentPage = page;

  const W = 1440, H = 900;
  const screen = frame('Loans Screen', 0, 0, W, H);
  screen.fills = rgb(T.bgApp);
  page.appendChild(screen);

  // Sidebar
  await createSidebar(screen, H);

  const mainW = W - 260;
  const mainArea = frame('Main Area', 260, 0, mainW, H);
  mainArea.fills = rgb(T.bgApp);
  screen.appendChild(mainArea);

  // Topbar
  await createTopbar(mainArea, 'Loan Management', 'Origination, repayment tracking, and arrears', 0, mainW);

  // Content
  const content = frame('Content', 0, 64, mainW, H - 64);
  content.fills = rgb(T.bgContent);
  mainArea.appendChild(content);

  const cPad = 24;

  // KPI Cards
  const loanKpis = [
    { label: 'Total Portfolio', value: '$48.7M',  sub: '423 active loans',   subC: T.textMuted },
    { label: 'Disbursed (MTD)', value: '$3.2M',   sub: '↑ 14.3% vs last month', subC: T.success },
    { label: 'At Risk',         value: '$4.1M',   sub: '8.4% of portfolio',  subC: T.warning, valC: T.warning },
    { label: 'Write-offs (YTD)',value: '$890K',   sub: '↓ 2.1% vs last year',subC: T.error   },
  ];

  for (let i = 0; i < loanKpis.length; i++) {
    await createKpiCard(content, loanKpis[i].label, loanKpis[i].value, loanKpis[i].sub, loanKpis[i].subC,
      cPad + i * (264 + 16), cPad);
  }

  // Loan Pipeline
  const pipelineY = cPad + 96 + cPad;
  const pipelineCard = frame('Card / Loan Pipeline', cPad, pipelineY, mainW - cPad * 2, 120);
  pipelineCard.cornerRadius = 12;
  pipelineCard.fills = rgb(T.bgCard);
  pipelineCard.strokeWeight = 1;
  pipelineCard.strokes = rgba(T.border);
  content.appendChild(pipelineCard);

  await text('Loan Pipeline', 20, 18, 14, 600, T.textMain, pipelineCard);

  const stages = [
    { name: 'Applied',    count: 47, color: T.info    },
    { name: 'Under Review',count: 23, color: T.warning },
    { name: 'Approved',   count: 18, color: T.success  },
    { name: 'Disbursed',  count: 12, color: T.primary  },
    { name: 'Rejected',   count: 9,  color: T.error    },
  ];

  const stageW = Math.floor((mainW - cPad * 2 - 40) / stages.length) - 8;

  for (let i = 0; i < stages.length; i++) {
    const st = stages[i];
    const stageBox = frame('Stage/' + st.name, 20 + i * (stageW + 8), 44, stageW, 64);
    stageBox.cornerRadius = 8;
    stageBox.fills = rgb(T.bgSubtle);
    pipelineCard.appendChild(stageBox);

    // Top color bar
    const topBar = rect('Stage Bar', 0, 0, stageW, 3);
    topBar.fills = rgb(st.color);
    stageBox.appendChild(topBar);

    await text(st.name, 12, 12, 12, 500, T.textMain, stageBox);
    await text(st.count.toString(), 12, 36, 20, 700, st.color, stageBox);
  }

  // Active Loans Table + Detail Panel side-by-side
  const tableAreaY = pipelineY + 120 + cPad;
  const tableW = mainW - cPad * 2 - 360 - 16;

  const loansTableCard = frame('Table / Active Loans', cPad, tableAreaY, tableW, H - 64 - tableAreaY - cPad);
  loansTableCard.cornerRadius = 12;
  loansTableCard.fills = rgb(T.bgCard);
  loansTableCard.strokeWeight = 1;
  loansTableCard.strokes = rgba(T.border);
  content.appendChild(loansTableCard);

  await text('Active Loans', 20, 18, 14, 600, T.textMain, loansTableCard);

  const loanCols = [
    { label: 'Borrower', width: 160 },
    { label: 'Loan ID',  width: 120 },
    { label: 'Amount',   width: 110 },
    { label: 'Balance',  width: 110 },
    { label: 'Rate',     width: 70  },
    { label: 'Status',   width: 100 },
  ];
  await createTableHeader(loansTableCard, loanCols, 0, 52, tableW);

  const loanRows = [
    ['Kwame Mensah',    'LN-009821', '$15,000', '$12,400', '18%', 'Current' ],
    ['Amara Diallo',    'LN-009820', '$8,500',  '$7,200',  '22%', 'Current' ],
    ['Fatima Al-Hassan','LN-009819', '$25,000', '$23,100', '15%', '30+ days'],
    ['Emmanuel Osei',   'LN-009818', '$5,000',  '$3,800',  '25%', '60+ days'],
    ['Ngozi Okonkwo',   'LN-009817', '$40,000', '$38,500', '14%', 'Current' ],
  ];

  for (let i = 0; i < loanRows.length; i++) {
    const statusColor = loanRows[i][5] === 'Current' ? T.success : T.error;
    await createTableRow(loansTableCard, loanRows[i].slice(0, 5), loanCols.slice(0, 5), 0, 88 + i * 48, tableW);

    // Status badge in last col
    const sRow = loansTableCard.children[loansTableCard.children.length - 1];
    const sBadge = frame('Status/Badge', loanCols.reduce((a, c) => a + c.width, 0) - loanCols[5].width - 100, 13, 80, 22);
    sBadge.cornerRadius = 999;
    sBadge.fills = rgb(loanRows[i][5] === 'Current' ? T.successBg : T.errorBg);
    sRow.appendChild(sBadge);
    await text(loanRows[i][5], 8, 4, 11, 600, statusColor, sBadge);
  }

  // Loan Detail Panel
  const detailPanel = frame('Panel / Loan Detail', cPad + tableW + 16, tableAreaY, 344, H - 64 - tableAreaY - cPad);
  detailPanel.cornerRadius = 12;
  detailPanel.fills = rgb(T.bgCard);
  detailPanel.strokeWeight = 1;
  detailPanel.strokes = rgba(T.border);
  content.appendChild(detailPanel);

  await text('Loan Detail', 20, 18, 14, 600, T.textMain, detailPanel);
  await text('LN-009821', 20, 42, 12, 400, T.textMuted, detailPanel);

  const detailSep = rect('Detail Sep', 0, 62, 344, 1);
  detailSep.fills = rgba(T.border);
  detailPanel.appendChild(detailSep);

  // Borrower info
  const detailRows = [
    { label: 'Borrower',    value: 'Kwame Mensah'  },
    { label: 'Product',     value: 'Personal Loan'  },
    { label: 'Amount',      value: '$15,000.00'     },
    { label: 'Balance',     value: '$12,400.00'     },
    { label: 'Rate',        value: '18% per annum'  },
    { label: 'Disbursed',   value: 'Feb 1, 2025'    },
    { label: 'Maturity',    value: 'Feb 1, 2027'    },
    { label: 'Status',      value: 'Current'        },
  ];

  for (let i = 0; i < detailRows.length; i++) {
    const dy = 76 + i * 40;
    await text(detailRows[i].label, 20, dy, 11, 600, T.textMuted, detailPanel);
    await text(detailRows[i].value, 20, dy + 14, 13, 500, T.textMain, detailPanel);
  }

  // Repayment schedule mini-table
  const schedY = 76 + detailRows.length * 40 + 16;
  const schedSep = rect('Sched Sep', 0, schedY - 8, 344, 1);
  schedSep.fills = rgba(T.border);
  detailPanel.appendChild(schedSep);
  await text('REPAYMENT SCHEDULE', 20, schedY, 10, 700, T.textMuted, detailPanel);

  const schedRows = [
    { date: 'Mar 1, 2025', payment: '$625.00', status: 'Paid'    },
    { date: 'Apr 1, 2025', payment: '$625.00', status: 'Upcoming'},
    { date: 'May 1, 2025', payment: '$625.00', status: 'Upcoming'},
  ];

  for (let i = 0; i < schedRows.length; i++) {
    const sy = schedY + 24 + i * 36;
    await text(schedRows[i].date, 20, sy, 12, 400, T.textMain, detailPanel);
    await text(schedRows[i].payment, 140, sy, 12, 500, T.textMain, detailPanel);
    const sc = schedRows[i].status === 'Paid' ? { bg: T.successBg, fg: T.success } : { bg: T.infoBg, fg: T.info };
    const sBadge = frame('Sched Status', 260, sy - 4, 64, 22);
    sBadge.cornerRadius = 999;
    sBadge.fills = rgb(sc.bg);
    detailPanel.appendChild(sBadge);
    await text(schedRows[i].status, 8, 4, 10, 600, sc.fg, sBadge);
  }
}

// ─────────────────────────────────────────────────
// MAIN ENTRY POINT
// ─────────────────────────────────────────────────

async function main() {
  figma.notify('Building CoreBanking-Nubeero design system...', { timeout: 30000 });

  // Create pages (use existing first page for tokens, add new pages for screens)
  const pages = figma.root.children;

  // Page 1: Design Tokens (use existing page)
  const tokenPage = pages[0] || figma.createPage();
  await createTokensPage(tokenPage);

  // Page 2: Dashboard
  const dashPage = figma.createPage();
  await createDashboardPage(dashPage);

  // Page 3: Customers
  const custPage = figma.createPage();
  await createCustomersPage(custPage);

  // Page 4: Loans
  const loanPage = figma.createPage();
  await createLoansPage(loanPage);

  // Page 5: Placeholder pages for remaining screens
  const placeholders = [
    '💳 Backoffice / Accounts',
    '↔️ Backoffice / Payments',
    '📊 Backoffice / Reports',
    '🔑 Auth / Login',
    '📱 Mobile / Customer App',
  ];
  for (const name of placeholders) {
    const p = figma.createPage();
    p.name = name;
    figma.currentPage = p;
    const ph = frame('Placeholder — ' + name, 0, 0, 1440, 900);
    ph.fills = [{ type: 'SOLID', color: { r: 0.97, g: 0.97, b: 0.97 } }];
    p.appendChild(ph);
    await text('Coming Soon', 60, 60, 36, 700, T.textMuted, ph);
    await text('This screen will be scaffolded when the module is implemented.', 60, 110, 16, 400, T.textMuted, ph);
    await text('Run the CBA skill → Phase D for full designs.', 60, 140, 16, 400, T.info, ph);
  }

  // Navigate to tokens page to show result
  figma.currentPage = tokenPage;

  figma.notify('✅ CoreBanking-Nubeero created! 9 pages with Nubeero design system.', { timeout: 8000 });
  figma.closePlugin();
}

main().catch(err => {
  console.error(err);
  figma.notify('❌ Error: ' + err.message, { error: true });
  figma.closePlugin();
});
