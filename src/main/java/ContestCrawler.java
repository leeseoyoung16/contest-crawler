import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class ContestCrawler {

    static class Contest {
        public String title, host, deadline, description, link;
        public String collectedDate;
        public boolean isDev;

        public Contest() {}
        Contest(String title, String host, String deadline, String description, String link, boolean isDev) {
            this.title = title;
            this.host = host;
            this.deadline = deadline;
            this.description = description;
            this.link = link;
            this.isDev = isDev;
            this.collectedDate = LocalDate.now().toString();
        }
    }

    static final String JSON_PATH = "output/contests.json";
    static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    //  개발 관련 키워드 — 클래스 레벨로 분리
    static final String[] DEV_KEYWORDS = {
            "개발", "SW", "소프트웨어", "앱", "해커톤", "hackathon",
            "프로그래밍", "코딩", "인공지능", "AI", "빅데이터",
            "클라우드", "웹", "모바일", "게임", "보안", "사이버",
            "디지털", "ICT", "정보통신", "알고리즘", "데이터사이언스",
            "아이디어톤", "AX", "DX", "tech", "테크",
            "IT경시", "IT 경시", "전산", "시스템", "플랫폼",
            "자동화", "로봇"
    };

    public static void main(String[] args) {
        System.out.println("공모전 수집 시작");

        List<Contest> existing = loadExisting();
        System.out.println("기존 데이터: " + existing.size() + "개");

        List<Contest> crawled = crawl();
        System.out.println("오늘 수집: " + crawled.size() + "개");

        Map<String, Contest> merged = new LinkedHashMap<>();
        for (Contest c : existing) merged.put(c.link, c);
        for (Contest c : crawled)  merged.put(c.link, c);

        LocalDate today = LocalDate.now();
        List<Contest> filtered = new ArrayList<>();
        int expiredCount = 0;

        for (Contest c : merged.values()) {
            LocalDate deadline = parseDeadline(c.deadline);
            if (deadline != null && !deadline.isBefore(today)) {
                filtered.add(c);
            } else {
                System.out.println("만료 제거: " + c.title + " (" + c.deadline + ")");
                expiredCount++;
            }
        }
        System.out.println("만료 제거: " + expiredCount + "개 / 최종: " + filtered.size() + "개");

        saveJSON(filtered);
        generateHTML(filtered);
    }

    static List<Contest> loadExisting() {
        try {
            if (Files.exists(Paths.get(JSON_PATH))) {
                Contest[] arr = mapper.readValue(Paths.get(JSON_PATH).toFile(), Contest[].class);
                return new ArrayList<>(Arrays.asList(arr));
            }
        } catch (Exception e) {
            System.err.println("JSON 로드 실패 (첫 실행이면 정상): " + e.getMessage());
        }
        return new ArrayList<>();
    }

    static void saveJSON(List<Contest> contests) {
        try {
            Files.createDirectories(Paths.get("output"));
            mapper.writeValue(Paths.get(JSON_PATH).toFile(), contests);
            System.out.println("contests.json 저장 완료");
        } catch (IOException e) {
            System.err.println("JSON 저장 실패: " + e.getMessage());
        }
    }

    static LocalDate parseDeadline(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String[] patterns = {
                "yyyy.MM.dd", "yyyy-MM-dd", "yyyy/MM/dd",
                "yyyy년 MM월 dd일", "yyyy년MM월dd일",
                "MM.dd", "MM-dd"
        };

        String cleaned = raw.replaceAll("[^0-9.\\-/년월일]", "").trim();

        for (String pattern : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
                if (!pattern.contains("yyyy")) {
                    return LocalDate.parse(
                            LocalDate.now().getYear() + "." + cleaned,
                            DateTimeFormatter.ofPattern("yyyy." + pattern)
                    );
                }
                return LocalDate.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {}
        }

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})")
                .matcher(raw);
        LocalDate last = null;
        while (m.find()) {
            try {
                last = LocalDate.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))
                );
            } catch (Exception ignored) {}
        }
        return last;
    }

    //  개발 키워드 포함 여부 판별 유틸
    static boolean isDevRelated(String title) {
        String lower = title.toLowerCase();
        for (String kw : DEV_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    static List<Contest> crawl() {
        List<Contest> contests = new ArrayList<>();
        Set<String> seenLinks  = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();

        for (int page = 1; page <= 10; page++) {
            try {
                String url = "https://www.contestkorea.com/sub/list.php"
                        + "?display=1&int_gbn=1&Txt_bkk=0&Txt_stt=1&Txt_bcode=030310001&page=" + page;
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                        .referrer("https://www.contestkorea.com")
                        .timeout(15000)
                        .get();

                Elements links = doc.select("a[href*=str_no]");
                for (Element link : links) {
                    String rawTitle = link.text().trim();
                    if (rawTitle.matches("^\\d+\\..*")) continue;

                    String title = rawTitle.replaceAll(
                            "^((학문|과학|미술|사진|문학|네이밍|기획|아이디어|캐릭터|공연|건축|창업|기타|문예|IT|SW|디자인|웹툰|음악|체육|공예|스포츠|환경|인문|사회)[•·/\\s]*)+[^가-힣0-9a-zA-Z]*", ""
                    ).trim();

                    String cleanTitle = title.replaceAll("^\\d+\\.\\s*", "").trim();
                    if (cleanTitle.length() < 5) continue;

                    String href = link.attr("href");
                    String fullLink = href.startsWith("http") ? href
                            : "https://www.contestkorea.com/sub/" + href;

                    if (seenLinks.contains(fullLink))    continue;
                    if (seenTitles.contains(cleanTitle)) continue;
                    seenTitles.add(cleanTitle);
                    seenLinks.add(fullLink);

                    //  키워드 필터 제거 → isDev 플래그만 판별
                    boolean devFlag = isDevRelated(cleanTitle);

                    // 상세 페이지
                    String host = "", deadline = "", description = "";
                    try {
                        Document detail = Jsoup.connect(fullLink)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                                .referrer("https://www.contestkorea.com")
                                .timeout(15000)
                                .get();

                        for (Element tr : detail.select("tr")) {
                            String th = tr.select("th").text().trim();
                            String td = tr.select("td").text().trim();
                            if (host.isEmpty() && (th.contains("주최") || th.contains("주관")))
                                host = td.length() > 30 ? td.substring(0, 30) + "..." : td;
                            if (deadline.isEmpty() && (th.contains("접수") || th.contains("마감") || th.contains("기간")))
                                deadline = td.length() > 50 ? td.substring(0, 50) : td;
                        }

                        for (Element p : detail.select(".view_con p, .board_view p, .cont_view p, #content p")) {
                            String txt = p.text().trim();
                            if (txt.length() > 20) {
                                description = txt.length() > 100 ? txt.substring(0, 100) + "..." : txt;
                                break;
                            }
                        }
                        Thread.sleep(400);
                    } catch (Exception e) {
                        System.err.println("상세 실패: " + cleanTitle);
                    }

                    if (host.isEmpty() && deadline.isEmpty()) {
                        System.out.println("스킵(상세없음): " + cleanTitle);
                        continue;
                    }

                    //  devFlag 전달
                    contests.add(new Contest(cleanTitle, host, deadline, description, fullLink, devFlag));
                    System.out.println("수집" + (devFlag ? "[개발]" : "[일반]") + ": " + cleanTitle);
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println(page + "페이지 실패: " + e.getMessage());
            }
        }
        return contests;
    }

    static void generateHTML(List<Contest> contests) {
        // ── 1. contests.json 은 saveJSON()에서 이미 저장됨
        // ── 2. index.html 뼈대 생성
        String htmlPath = "output/index.html";
        String html = """
        <!DOCTYPE html>
        <html lang="ko">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>공모전 대시보드</title>
          <link rel="preconnect" href="https://fonts.googleapis.com" />
          <link href="https://fonts.googleapis.com/css2?family=Pretendard:wght@300;400;600;700;900&display=swap" rel="stylesheet" />
          <link rel="stylesheet" href="style.css" />
        </head>
        <body>
          <!-- 배경 Mesh Gradient Blobs -->
          <div class="bg-blob blob-1"></div>
          <div class="bg-blob blob-2"></div>
          <div class="bg-blob blob-3"></div>

          <!-- 헤더 -->
          <header class="site-header">
            <div class="header-inner">
              <p class="header-eyebrow">LIVE · AUTO UPDATED 10:00 AM</p>
              <h1 class="header-title">
                <span class="gradient-text">공모전</span> 대시보드
              </h1>
              <p class="header-sub">IT·SW·AI 포함 전체 공모전을 매일 자동 수집합니다</p>
            </div>
          </header>

          <!-- 메인 -->
          <main class="container">
            <!-- 통계 바 -->
            <div class="stats-bar">
              <div class="stat-item">
                <span class="stat-num" id="totalCount">-</span>
                <span class="stat-label">전체</span>
              </div>
              <div class="stat-divider"></div>
              <div class="stat-item">
                <span class="stat-num neon-green" id="devCount">-</span>
                <span class="stat-label">개발</span>
              </div>
              <div class="stat-divider"></div>
              <div class="stat-item">
                <span class="stat-num neon-red" id="urgentCount">-</span>
                <span class="stat-label">마감 임박</span>
              </div>
            </div>

            <!-- 검색 + 필터 -->
            <div class="toolbar">
              <div class="search-wrap">
                <span class="search-icon">⌕</span>
                <input type="text" id="searchInput" class="search-input" placeholder="공모전 이름 검색..." />
              </div>
              <div class="filter-group">
                <button class="filter-btn active" data-filter="all">전체</button>
                <button class="filter-btn" data-filter="dev">💻 개발</button>
              </div>
              <div class="sort-group">
                <select id="sortSelect" class="sort-select">
                  <option value="default">최신순</option>
                  <option value="deadline">마감 임박순</option>
                </select>
              </div>
            </div>

            <!-- 페이지 정보 -->
            <p class="page-info" id="pageInfo"></p>

            <!-- 카드 그리드 -->
            <div class="card-grid" id="cardGrid">
              <div class="loading-spinner">
                <div class="spinner-ring"></div>
                <p>데이터 로딩 중...</p>
              </div>
            </div>

            <!-- 페이지네이션 -->
            <div class="pagination" id="pagination"></div>
          </main>

          <footer class="site-footer">
            <p>데이터 출처:
              <a href="https://www.contestkorea.com" target="_blank">공모전코리아</a>
              · 매일 오전 10시 자동 업데이트
            </p>
          </footer>

          <script src="app.js"></script>
        </body>
        </html>
        """;

        // ── 3. style.css 생성
        String cssPath = "output/style.css";
        String css = generateCSS();

        // ── 4. app.js 생성
        String jsPath = "output/app.js";
        String js = generateJS();

        try {
            Files.createDirectories(Paths.get("output"));
            Files.write(Paths.get(htmlPath), html.getBytes("UTF-8"));
            Files.write(Paths.get(cssPath), css.getBytes("UTF-8"));
            Files.write(Paths.get(jsPath),  js.getBytes("UTF-8"));
            System.out.println("index.html / style.css / app.js 생성 완료");
        } catch (IOException e) {
            System.err.println("저장 실패: " + e.getMessage());
        }
    }

    static String generateCSS() {
        return """
        /* ── Reset & Base ── */
        *, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }

        :root {
          --bg:        #030712;
          --surface:   rgba(255,255,255,0.04);
          --border:    rgba(255,255,255,0.08);
          --text:      #e2e8f0;
          --text-muted:#64748b;
          --purple:    #a855f7;
          --blue:      #3b82f6;
          --green:     #22c55e;
          --red:       #ef4444;
          --gold:      #f59e0b;
          --font:      'Pretendard', 'Apple SD Gothic Neo', sans-serif;
        }

        html { scroll-behavior: smooth; }

        body {
          background: var(--bg);
          color: var(--text);
          font-family: var(--font);
          min-height: 100vh;
          overflow-x: hidden;
          letter-spacing: 0.01em;
        }

        /* ── Background Blobs ── */
        .bg-blob {
          position: fixed;
          border-radius: 50%;
          filter: blur(120px);
          opacity: 0.18;
          pointer-events: none;
          z-index: 0;
          animation: blobFloat 12s ease-in-out infinite alternate;
        }
        .blob-1 {
          width: 600px; height: 600px;
          background: var(--purple);
          top: -200px; left: -200px;
          animation-delay: 0s;
        }
        .blob-2 {
          width: 500px; height: 500px;
          background: var(--blue);
          top: 50%; right: -150px;
          animation-delay: -4s;
        }
        .blob-3 {
          width: 400px; height: 400px;
          background: #ec4899;
          bottom: -100px; left: 40%;
          animation-delay: -8s;
        }
        @keyframes blobFloat {
          from { transform: translate(0,0) scale(1); }
          to   { transform: translate(40px, 30px) scale(1.08); }
        }

        /* ── Header ── */
        .site-header {
          position: relative; z-index: 1;
          padding: 80px 20px 60px;
          text-align: center;
        }
        .header-inner { max-width: 700px; margin: 0 auto; }

        .header-eyebrow {
          font-size: .75rem;
          letter-spacing: .25em;
          color: var(--green);
          margin-bottom: 16px;
          display: flex; align-items: center; justify-content: center; gap: 8px;
        }
        .header-eyebrow::before {
          content: '';
          display: inline-block;
          width: 8px; height: 8px;
          border-radius: 50%;
          background: var(--green);
          box-shadow: 0 0 8px var(--green);
          animation: livePulse 1.4s ease-in-out infinite;
        }
        @keyframes livePulse {
          0%,100% { opacity:1; box-shadow: 0 0 8px var(--green); }
          50%      { opacity:.4; box-shadow: 0 0 18px var(--green); }
        }

        .header-title {
          font-size: clamp(2.4rem, 6vw, 4rem);
          font-weight: 900;
          line-height: 1.1;
          letter-spacing: -0.02em;
          color: #fff;
          margin-bottom: 16px;
        }
        .gradient-text {
          background: linear-gradient(135deg, var(--purple), var(--blue), #ec4899);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
        }
        .header-sub {
          font-size: 1rem;
          color: var(--text-muted);
          font-weight: 300;
          letter-spacing: .04em;
        }

        /* ── Container ── */
        .container {
          position: relative; z-index: 1;
          max-width: 1300px;
          margin: 0 auto;
          padding: 0 24px 80px;
        }

        /* ── Stats Bar ── */
        .stats-bar {
          display: flex; align-items: center; justify-content: center;
          gap: 0;
          background: var(--surface);
          border: 1px solid var(--border);
          backdrop-filter: blur(20px);
          border-radius: 16px;
          padding: 20px 40px;
          margin-bottom: 32px;
          width: fit-content;
          margin-left: auto; margin-right: auto;
        }
        .stat-item { text-align: center; padding: 0 32px; }
        .stat-num {
          display: block;
          font-size: 2rem; font-weight: 900;
          color: #fff;
          letter-spacing: -0.03em;
        }
        .stat-num.neon-green { color: var(--green); text-shadow: 0 0 20px rgba(34,197,94,.5); }
        .stat-num.neon-red   { color: var(--red);   text-shadow: 0 0 20px rgba(239,68,68,.5); }
        .stat-label { font-size: .78rem; color: var(--text-muted); letter-spacing: .1em; text-transform: uppercase; }
        .stat-divider { width: 1px; height: 40px; background: var(--border); }

        /* ── Toolbar ── */
        .toolbar {
          display: flex; align-items: center; gap: 14px;
          flex-wrap: wrap;
          margin-bottom: 20px;
        }
        .search-wrap {
          flex: 1; min-width: 220px;
          position: relative;
        }
        .search-icon {
          position: absolute; left: 14px; top: 50%;
          transform: translateY(-50%);
          color: var(--text-muted); font-size: 1.2rem;
        }
        .search-input {
          width: 100%;
          background: var(--surface);
          border: 1px solid var(--border);
          backdrop-filter: blur(12px);
          border-radius: 12px;
          padding: 11px 16px 11px 40px;
          color: var(--text);
          font-size: .9rem;
          font-family: var(--font);
          outline: none;
          transition: border-color .2s, box-shadow .2s;
        }
        .search-input:focus {
          border-color: var(--purple);
          box-shadow: 0 0 0 3px rgba(168,85,247,.15);
        }
        .search-input::placeholder { color: var(--text-muted); }

        .filter-group { display: flex; gap: 8px; }
        .filter-btn {
          padding: 10px 20px;
          border-radius: 10px;
          border: 1px solid var(--border);
          background: var(--surface);
          color: var(--text-muted);
          font-size: .85rem; font-weight: 600;
          font-family: var(--font);
          letter-spacing: .04em;
          cursor: pointer;
          transition: all .2s;
          backdrop-filter: blur(12px);
        }
        .filter-btn:hover { border-color: var(--purple); color: var(--text); }
        .filter-btn.active {
          background: linear-gradient(135deg, var(--purple), var(--blue));
          border-color: transparent;
          color: #fff;
          box-shadow: 0 0 20px rgba(168,85,247,.4);
        }

        .sort-select {
          background: var(--surface);
          border: 1px solid var(--border);
          backdrop-filter: blur(12px);
          border-radius: 10px;
          padding: 10px 14px;
          color: var(--text);
          font-size: .85rem;
          font-family: var(--font);
          outline: none;
          cursor: pointer;
        }

        /* ── Page Info ── */
        .page-info {
          font-size: .8rem; color: var(--text-muted);
          letter-spacing: .06em;
          margin-bottom: 20px;
        }

        /* ── Card Grid (Bento) ── */
        .card-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
          gap: 20px;
        }

        /* ── Card ── */
        .card {
          position: relative;
          background: var(--surface);
          border: 1px solid var(--border);
          border-radius: 20px;
          padding: 26px;
          display: flex; flex-direction: column; gap: 12px;
          backdrop-filter: blur(20px);
          -webkit-backdrop-filter: blur(20px);
          overflow: hidden;
          cursor: pointer;
          transition: transform .3s cubic-bezier(.34,1.56,.64,1),
                      box-shadow .3s ease,
                      border-color .3s ease;
        }
        /* Border Beam (hover glow sweep) */
        .card::before {
          content: '';
          position: absolute; inset: 0;
          border-radius: 20px;
          padding: 1px;
          background: linear-gradient(135deg, var(--purple), var(--blue), var(--green), var(--purple));
          background-size: 300% 300%;
          -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
          -webkit-mask-composite: destination-out;
          mask-composite: exclude;
          opacity: 0;
          pointer-events: none;
          transition: opacity .3s;
          animation: borderBeam 3s linear infinite;
        }
        .card:hover::before { opacity: 1; }
        @keyframes borderBeam {
          0%   { background-position: 0% 50%; }
          50%  { background-position: 100% 50%; }
          100% { background-position: 0% 50%; }
        }

        .card:hover {
          transform: translateY(-6px) scale(1.01);
          box-shadow: 0 20px 60px rgba(168,85,247,.15),
                      0 4px 20px rgba(0,0,0,.5);
        }
        /* 개발 카드 — Green Glow */
        .card.dev:hover {
          box-shadow: 0 20px 60px rgba(34,197,94,.15),
                      0 4px 20px rgba(0,0,0,.5);
        }

        /* ── Card — Dev Badge ── */
        .dev-badge {
          display: inline-flex; align-items: center; gap: 5px;
          background: rgba(34,197,94,.1);
          border: 1px solid rgba(34,197,94,.3);
          color: var(--green);
          padding: 3px 10px; border-radius: 6px;
          font-size: .72rem; font-weight: 700;
          letter-spacing: .08em;
          width: fit-content;
        }

        /* ── Card — D-Day Badge ── */
        .dday-badge {
          display: inline-flex; align-items: center; gap: 6px;
          padding: 4px 12px; border-radius: 8px;
          font-size: .78rem; font-weight: 700;
          letter-spacing: .06em;
        }
        .dday-safe    { background: rgba(34,197,94,.1);  color: var(--green); border:1px solid rgba(34,197,94,.25); }
        .dday-warn    { background: rgba(245,158,11,.1);  color: var(--gold);  border:1px solid rgba(245,158,11,.25); }
        .dday-danger  { background: rgba(239,68,68,.1);  color: var(--red);   border:1px solid rgba(239,68,68,.25);
                        animation: ddayBlink 1.2s ease-in-out infinite; }
        @keyframes ddayBlink {
          0%,100% { box-shadow: 0 0 8px rgba(239,68,68,.3); }
          50%      { box-shadow: 0 0 20px rgba(239,68,68,.7); }
        }
        .dday-live-dot {
          width: 7px; height: 7px; border-radius: 50%;
          background: currentColor;
          animation: livePulse 1.2s ease-in-out infinite;
        }

        /* ── Card — Title ── */
        .card-title {
          font-size: 1rem; font-weight: 700;
          line-height: 1.5; color: #f1f5f9;
        }
        .card-title a { color: inherit; text-decoration: none; }
        .card-title a:hover { color: var(--purple); }

        /* ── Card — Desc ── */
        .card-desc {
          font-size: .83rem; color: var(--text-muted);
          line-height: 1.65; font-weight: 300;
        }

        /* ── Card — Meta ── */
        .card-meta {
          margin-top: auto;
          padding-top: 14px;
          border-top: 1px solid var(--border);
          display: flex; flex-direction: column; gap: 7px;
        }
        .meta-row {
          display: flex; align-items: flex-start; gap: 10px;
          font-size: .81rem; color: var(--text-muted);
        }
        .meta-icon { font-size: .9rem; flex-shrink: 0; margin-top: 1px; }
        .meta-label { font-weight: 700; color: #94a3b8; min-width: 28px; flex-shrink:0; }
        .deadline-text { color: #cbd5e1; }

        /* ── Card — Collected Date ── */
        .collected {
          font-size: .72rem; color: #334155;
          letter-spacing: .04em;
        }

        /* ── Card — CTA Button ── */
        .card-btn {
          display: block; margin-top: 6px;
          padding: 10px;
          background: linear-gradient(135deg, var(--purple), var(--blue));
          color: #fff; font-weight: 700; font-size: .85rem;
          font-family: var(--font);
          text-align: center; text-decoration: none;
          border-radius: 10px;
          letter-spacing: .06em;
          transition: opacity .2s, transform .15s;
        }
        .card-btn:hover { opacity: .85; transform: scale(1.02); }
        .card.dev .card-btn {
          background: linear-gradient(135deg, #16a34a, #059669);
        }

        /* ── Pagination ── */
        .pagination {
          display: flex; justify-content: center; align-items: center;
          gap: 6px; margin: 48px 0 24px;
          flex-wrap: wrap;
        }
        .pagination button {
          min-width: 40px; height: 40px;
          border: 1px solid var(--border);
          border-radius: 10px;
          background: var(--surface);
          backdrop-filter: blur(10px);
          color: var(--text-muted);
          font-size: .88rem; font-family: var(--font);
          cursor: pointer;
          transition: all .2s;
        }
        .pagination button:hover:not(:disabled) {
          border-color: var(--purple);
          color: var(--text);
          box-shadow: 0 0 12px rgba(168,85,247,.3);
        }
        .pagination button.active {
          background: linear-gradient(135deg, var(--purple), var(--blue));
          border-color: transparent;
          color: #fff;
          box-shadow: 0 0 18px rgba(168,85,247,.4);
          font-weight: 700;
        }
        .pagination button:disabled { opacity: .25; cursor: not-allowed; }

        /* ── Loading ── */
        .loading-spinner {
          grid-column: 1 / -1;
          text-align: center;
          padding: 80px 20px;
          color: var(--text-muted);
          font-size: .9rem;
        }
        .spinner-ring {
          width: 48px; height: 48px;
          border: 3px solid var(--border);
          border-top-color: var(--purple);
          border-radius: 50%;
          margin: 0 auto 16px;
          animation: spin .8s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }

        /* ── Empty ── */
        .empty-msg {
          grid-column: 1 / -1;
          text-align: center;
          padding: 80px 20px;
          color: var(--text-muted);
          font-size: .95rem;
          letter-spacing: .04em;
        }

        /* ── Footer ── */
        .site-footer {
          position: relative; z-index: 1;
          text-align: center;
          padding: 48px 20px;
          color: var(--text-muted);
          font-size: .8rem;
          border-top: 1px solid var(--border);
          letter-spacing: .04em;
        }
        .site-footer a { color: var(--purple); text-decoration: none; }
        .site-footer a:hover { text-decoration: underline; }

        /* ── Scrollbar ── */
        ::-webkit-scrollbar { width: 6px; }
        ::-webkit-scrollbar-track { background: var(--bg); }
        ::-webkit-scrollbar-thumb {
          background: rgba(168,85,247,.3);
          border-radius: 3px;
        }
        ::-webkit-scrollbar-thumb:hover { background: rgba(168,85,247,.6); }
        """;
    }


    static String generateJS() {
        return """
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
          const matches = [...deadlineStr.matchAll(/(\\d{4})[.\\-\\/](\\d{1,2})[.\\-\\/](\\d{1,2})/g)];
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
        """;
    }



    static String esc(String t) {
        if (t == null) return "";
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
