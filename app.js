'use strict';

const PER_PAGE = 12;
let currentPage   = 1;
let currentFilter = 'all';
let searchQuery   = '';
let sortMode      = 'default';
let allContests   = [];

// ── D-Day 계산
function calcDday(deadlineStr) {
  if (!deadlineStr) return null;
  const matches = [...deadlineStr.matchAll(/(\d{4})[.\-\/](\d{1,2})[.\-\/](\d{1,2})/g)];
  if (!matches.length) return null;
  const m = matches[matches.length - 1];
  const d   = new Date(+m[1], +m[2]-1, +m[3]);
  const now = new Date();
  now.setHours(0,0,0,0);
  return Math.ceil((d - now) / 86400000);
}

// ── D-Day 뱃지 HTML
function ddayHTML(deadlineStr) {
  const d = calcDday(deadlineStr);
  if (d === null) return '';
  if (d < 0)  return `<span class="dday-badge dday-danger"><span class="dday-live-dot"></span> 마감</span>`;
  if (d === 0) return `<span class="dday-badge dday-danger"><span class="dday-live-dot"></span> D-DAY</span>`;
  if (d <= 3)  return `<span class="dday-badge dday-danger"><span class="dday-live-dot"></span> D-${d}</span>`;
  if (d <= 7)  return `<span class="dday-badge dday-warn">D-${d}</span>`;
  return `<span class="dday-badge dday-safe">D-${d}</span>`;
}

// ── 카드 HTML 생성
function cardHTML(c) {
  const devCls  = c.isDev ? ' dev' : '';
  const devBadge = c.isDev
    ? `<span class="dev-badge">▸ 개발</span>` : '';
  const dday = ddayHTML(c.deadline);
  const desc = c.description
    ? `<div class="card-desc">${esc(c.description)}</div>` : '';
  const host = c.host
    ? `<div class="meta-row">
        <span class="meta-icon">🏢</span>
        <span class="meta-label">주최</span>
        <span>${esc(c.host)}</span>
       </div>` : '';
  const dl = c.deadline
    ? `<div class="meta-row">
        <span class="meta-icon">📅</span>
        <span class="meta-label">마감</span>
        <span class="deadline-text">${esc(c.deadline)}</span>
       </div>` : '';
  const collected = c.collectedDate
    ? `<div class="collected">수집일 ${esc(c.collectedDate)}</div>` : '';

  return `
    <div class="card${devCls}">
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:6px">
        ${devBadge}
        ${dday}
      </div>
      <div class="card-title">
        <a href="${esc(c.link)}" target="_blank" rel="noopener">${esc(c.title)}</a>
      </div>
      ${desc}
      <div class="card-meta">
        ${host}
        ${dl}
        ${collected}
      </div>
      <a class="card-btn" href="${esc(c.link)}" target="_blank" rel="noopener">자세히 보기 →</a>
    </div>`;
}

// ── 필터 + 검색 + 정렬 적용
function getFiltered() {
  let list = allContests;
  if (currentFilter === 'dev') list = list.filter(c => c.isDev);
  if (searchQuery) {
    const q = searchQuery.toLowerCase();
    list = list.filter(c =>
      (c.title  && c.title.toLowerCase().includes(q)) ||
      (c.host   && c.host.toLowerCase().includes(q))
    );
  }
  if (sortMode === 'deadline') {
    list = [...list].sort((a, b) => {
      const da = calcDday(a.deadline) ?? 9999;
      const db = calcDday(b.deadline) ?? 9999;
      return da - db;
    });
  }
  return list;
}

// ── 통계 업데이트
function updateStats() {
  const urgent = allContests.filter(c => {
    const d = calcDday(c.deadline);
    return d !== null && d >= 0 && d <= 3;
  }).length;
  document.getElementById('totalCount').textContent = allContests.length;
  document.getElementById('devCount').textContent   = allContests.filter(c => c.isDev).length;
  document.getElementById('urgentCount').textContent = urgent;
}

// ── 렌더
function render() {
  const filtered   = getFiltered();
  const total      = filtered.length;
  const totalPages = Math.max(1, Math.ceil(total / PER_PAGE));
  if (currentPage > totalPages) currentPage = totalPages;

  const start = (currentPage - 1) * PER_PAGE;
  const slice = filtered.slice(start, start + PER_PAGE);

  const grid = document.getElementById('cardGrid');
  if (total === 0) {
    grid.innerHTML = `<div class="empty-msg">😶 해당 조건의 공모전이 없습니다.</div>`;
  } else {
    grid.innerHTML = slice.map(cardHTML).join('');
  }

  // 카드 페이드인 애니메이션
  grid.querySelectorAll('.card').forEach((el, i) => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(16px)';
    setTimeout(() => {
      el.style.transition = 'opacity .35s ease, transform .35s ease';
      el.style.opacity    = '1';
      el.style.transform  = 'translateY(0)';
    }, i * 40);
  });

  const filterLabel = currentFilter === 'dev' ? '💻 개발 공모전' : '📋 전체 공모전';
  document.getElementById('pageInfo').textContent =
    `${filterLabel} ${total}개 · ${currentPage} / ${totalPages} 페이지`;

  renderPagination(totalPages);
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ── 페이지네이션
function renderPagination(totalPages) {
  const pg = document.getElementById('pagination');
  if (totalPages <= 1) { pg.innerHTML = ''; return; }
  let html = '';
  html += `<button onclick="goPage(${currentPage-1})" ${currentPage===1?'disabled':''}>◀</button>`;
  let s = Math.max(1, currentPage-2), e = Math.min(totalPages, s+4);
  if (e-s < 4) s = Math.max(1, e-4);
  if (s > 1) {
    html += `<button onclick="goPage(1)">1</button>`;
    if (s > 2) html += `<button disabled>…</button>`;
  }
  for (let i=s; i<=e; i++)
    html += `<button onclick="goPage(${i})" class="${i===currentPage?'active':''}">${i}</button>`;
  if (e < totalPages) {
    if (e < totalPages-1) html += `<button disabled>…</button>`;
    html += `<button onclick="goPage(${totalPages})">${totalPages}</button>`;
  }
  html += `<button onclick="goPage(${currentPage+1})" ${currentPage===totalPages?'disabled':''}>▶</button>`;
  pg.innerHTML = html;
}

function goPage(n) {
  const tp = Math.max(1, Math.ceil(getFiltered().length / PER_PAGE));
  if (n < 1 || n > tp) return;
  currentPage = n; render();
}

// ── 이벤트 바인딩
document.querySelectorAll('.filter-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentFilter = btn.dataset.filter;
    currentPage = 1;
    render();
  });
});

let searchTimer;
document.getElementById('searchInput').addEventListener('input', e => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    searchQuery = e.target.value.trim();
    currentPage = 1;
    render();
  }, 250);
});

document.getElementById('sortSelect').addEventListener('change', e => {
  sortMode = e.target.value;
  currentPage = 1;
  render();
});

// ── XSS 방어
function esc(t) {
  if (!t) return '';
  return t.replace(/&/g,'&amp;').replace(/</g,'&lt;')
          .replace(/>/g,'&gt;').replace(/'/g,'&#39;')
          .replace(/"/g,'&quot;');
}

// ── contests.json 로드 후 시작
fetch('contests.json')
  .then(r => r.json())
  .then(data => {
    allContests = data;
    updateStats();
    render();
  })
  .catch(() => {
    document.getElementById('cardGrid').innerHTML =
      '<div class="empty-msg">⚠️ 데이터를 불러오지 못했습니다. contests.json을 확인해주세요.</div>';
  });
