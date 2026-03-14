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
        public boolean isDev; // ✅ 개발 관련 여부 플래그 추가

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

    // ✅ 개발 관련 키워드 — 클래스 레벨로 분리
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
            if (deadline == null || !deadline.isBefore(today)) {
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

    // ✅ 개발 키워드 포함 여부 판별 유틸
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
                            "^(학문|과학|미술|사진|문학|네이밍|기획|아이디어|캐릭터|공연|건축|창업|기타|문예)" +
                                    "([•·/\\s]*(IT|SW|과학|미술|디자인|웹툰|음악|체육|기타))*[^가-힣0-9a-zA-Z]*", ""
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

                    // ✅ 키워드 필터 제거 → isDev 플래그만 판별
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

                    // ✅ devFlag 전달
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
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));
        long devCount = contests.stream().filter(c -> c.isDev).count(); // ✅ 개발 공모전 수

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='ko'><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width,initial-scale=1.0'>");
        sb.append("<title>공모전 대시보드</title><style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}");
        sb.append("body{font-family:'Malgun Gothic',sans-serif;background:#f0f4f8;color:#2d3748}");
        sb.append("header{background:linear-gradient(135deg,#667eea,#764ba2);color:white;padding:48px 20px;text-align:center}");
        sb.append("header h1{font-size:2.2rem;margin-bottom:10px}");
        sb.append("header p{opacity:.85;margin-bottom:12px}");
        sb.append(".badge{display:inline-block;background:rgba(255,255,255,0.2);padding:5px 16px;border-radius:20px;font-size:.85rem}");
        sb.append(".container{max-width:1200px;margin:32px auto;padding:0 20px}");
        sb.append(".stats{background:white;border-radius:14px;padding:18px 24px;margin-bottom:20px;");
        sb.append("box-shadow:0 2px 10px rgba(0,0,0,.07);font-size:1rem;color:#4a5568}");
        sb.append(".count{color:#667eea;font-weight:700;font-size:1.3rem}");

        // ✅ 필터 버튼 스타일
        sb.append(".filter-bar{display:flex;gap:10px;margin-bottom:20px;flex-wrap:wrap;align-items:center}");
        sb.append(".filter-btn{padding:8px 20px;border-radius:20px;border:2px solid #667eea;");
        sb.append("background:white;color:#667eea;font-size:.9rem;font-weight:600;cursor:pointer;transition:all .2s}");
        sb.append(".filter-btn:hover{background:#667eea;color:white}");
        sb.append(".filter-btn.active{background:#667eea;color:white}");
        sb.append(".filter-label{font-size:.85rem;color:#718096;margin-left:4px}");

        sb.append(".page-info{text-align:center;color:#888;font-size:.85rem;margin-bottom:12px}");
        sb.append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:22px}");
        sb.append(".card{background:white;border-radius:14px;padding:24px;box-shadow:0 2px 10px rgba(0,0,0,.07);");
        sb.append("border-top:4px solid #667eea;display:flex;flex-direction:column;gap:10px;transition:transform .2s,box-shadow .2s}");
        sb.append(".card:hover{transform:translateY(-4px);box-shadow:0 10px 28px rgba(0,0,0,.12)}");

        // ✅ 개발 카드 강조 테두리 색상
        sb.append(".card.dev{border-top-color:#48bb78}");

        sb.append(".card-title{font-size:1.05rem;font-weight:700;line-height:1.45;color:#1a202c}");
        sb.append(".card-title a{color:inherit;text-decoration:none}");
        sb.append(".card-title a:hover{color:#667eea}");

        // ✅ 개발 뱃지
        sb.append(".dev-badge{display:inline-block;background:#f0fff4;color:#276749;border:1px solid #9ae6b4;");
        sb.append("padding:2px 9px;border-radius:20px;font-size:.75rem;font-weight:700;margin-bottom:4px}");

        sb.append(".card-desc{font-size:.88rem;color:#718096;line-height:1.6}");
        sb.append(".card-meta{display:flex;flex-direction:column;gap:6px;margin-top:auto;padding-top:10px;border-top:1px solid #e2e8f0}");
        sb.append(".meta-row{display:flex;align-items:flex-start;gap:8px;font-size:.83rem;color:#718096}");
        sb.append(".meta-label{font-weight:700;color:#4a5568;min-width:36px;flex-shrink:0}");
        sb.append(".deadline-badge{background:#fff5f5;color:#e53e3e;padding:2px 8px;border-radius:6px;font-weight:600}");
        sb.append(".collected{font-size:.75rem;color:#a0aec0;margin-top:4px}");
        sb.append(".btn{display:block;margin-top:10px;padding:9px;background:#667eea;color:white;");
        sb.append("border-radius:8px;font-size:.88rem;text-decoration:none;font-weight:600;text-align:center}");
        sb.append(".btn:hover{background:#5a67d8}");
        sb.append(".card.dev .btn{background:#48bb78}.card.dev .btn:hover{background:#38a169}");
        sb.append(".pagination{display:flex;justify-content:center;align-items:center;gap:6px;margin:36px 0 24px;flex-wrap:wrap}");
        sb.append(".pagination button{min-width:38px;height:38px;border:1px solid #ddd;border-radius:8px;");
        sb.append("background:white;color:#333;font-size:.9rem;cursor:pointer;transition:all .2s}");
        sb.append(".pagination button:hover:not(:disabled){background:#667eea;color:white;border-color:#667eea}");
        sb.append(".pagination button.active{background:#667eea;color:white;border-color:#667eea;font-weight:700}");
        sb.append(".pagination button:disabled{opacity:.35;cursor:not-allowed}");
        sb.append(".empty{text-align:center;padding:60px 20px;color:#a0aec0;font-size:1rem}");
        sb.append("footer{text-align:center;padding:48px 20px;color:#a0aec0;font-size:.85rem}");
        sb.append("footer a{color:#667eea;text-decoration:none}");
        sb.append("</style></head><body>");

        // ── 헤더
        sb.append("<header><h1>🏆 공모전 대시보드</h1>");
        sb.append("<p>IT/SW/AI 포함 전체 공모전을 매일 자동 수집합니다</p>");
        sb.append("<div class='badge'>매일 오전 10시 자동 업데이트</div></header>");

        sb.append("<div class='container'>");
        sb.append("<div class='stats'>📅 ").append(today)
                .append(" 기준 &nbsp;|&nbsp; 전체 <span class='count'>").append(contests.size())
                .append("</span>개")
                .append(" &nbsp;|&nbsp; 개발 관련 <span class='count'>").append(devCount)
                .append("</span>개</div>");

        // ✅ 필터 버튼 — data-filter 속성으로 JS가 구분
        sb.append("<div class='filter-bar'>");
        sb.append("<button class='filter-btn active' data-filter='all'>📋 전체</button>");
        sb.append("<button class='filter-btn' data-filter='dev'>💻 개발자</button>");
        sb.append("</div>");

        sb.append("<p class='page-info' id='pageInfo'></p>");

        // ── 카드 그리드
        sb.append("<div class='grid' id='cardGrid'>");
        for (Contest c : contests) {
            // ✅ isDev 이면 class='card dev', data-dev='1'
            String cardClass = c.isDev ? "card dev" : "card";
            String dataAttr  = c.isDev ? " data-dev='1'" : " data-dev='0'";
            sb.append("<div class='").append(cardClass).append("'").append(dataAttr).append(">");

            // ✅ 개발 뱃지 (해당 카드만)
            if (c.isDev) sb.append("<span class='dev-badge'>💻 개발</span>");

            sb.append("<div class='card-title'><a href='").append(esc(c.link))
                    .append("' target='_blank'>").append(esc(c.title)).append("</a></div>");
            if (c.description != null && !c.description.isEmpty())
                sb.append("<div class='card-desc'>").append(esc(c.description)).append("</div>");
            sb.append("<div class='card-meta'>");
            if (c.host != null && !c.host.isEmpty())
                sb.append("<div class='meta-row'><span class='meta-label'>주최</span><span>")
                        .append(esc(c.host)).append("</span></div>");
            if (c.deadline != null && !c.deadline.isEmpty())
                sb.append("<div class='meta-row'><span class='meta-label'>마감</span>")
                        .append("<span class='deadline-badge'>").append(esc(c.deadline)).append("</span></div>");
            sb.append("</div>");
            if (c.collectedDate != null)
                sb.append("<div class='collected'>🗓 수집일: ").append(esc(c.collectedDate)).append("</div>");
            sb.append("<a class='btn' href='").append(esc(c.link)).append("' target='_blank'>자세히 보기</a>");
            sb.append("</div>");
        }
        sb.append("</div>"); // .grid

        sb.append("<div class='pagination' id='pagination'></div>");
        sb.append("</div>"); // .container

        sb.append("<footer><p>데이터 출처: <a href='https://www.contestkorea.com' target='_blank'>공모전코리아</a>");
        sb.append(" | 매일 오전 10시 자동 업데이트</p></footer>");

        // ✅ JavaScript — 필터 + 페이지네이션 통합
        sb.append("<script>");
        sb.append("const PER_PAGE = 12;");
        sb.append("let currentPage = 1;");
        sb.append("let currentFilter = 'all';"); // ✅ 현재 필터 상태

        // 필터 버튼 클릭 이벤트
        sb.append("document.querySelectorAll('.filter-btn').forEach(btn => {");
        sb.append("  btn.addEventListener('click', () => {");
        sb.append("    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));");
        sb.append("    btn.classList.add('active');");
        sb.append("    currentFilter = btn.dataset.filter;");
        sb.append("    currentPage = 1;"); // 필터 바꾸면 1페이지로 리셋
        sb.append("    render();");
        sb.append("  });");
        sb.append("});");

        // 현재 필터에 맞는 카드 배열 반환
        sb.append("function getFiltered() {");
        sb.append("  const all = Array.from(document.querySelectorAll('.card'));");
        sb.append("  if (currentFilter === 'dev') return all.filter(c => c.dataset.dev === '1');");
        sb.append("  return all;");
        sb.append("}");

        // 렌더
        sb.append("function render() {");
        sb.append("  const all = Array.from(document.querySelectorAll('.card'));");
        sb.append("  const filtered = getFiltered();");
        sb.append("  const total = filtered.length;");
        sb.append("  const totalPages = Math.max(1, Math.ceil(total / PER_PAGE));");
        sb.append("  if (currentPage > totalPages) currentPage = totalPages;");

        // 먼저 전부 숨기고
        sb.append("  all.forEach(c => c.style.display = 'none');");
        // 필터된 것만 현재 페이지 분 보이기
        sb.append("  const start = (currentPage - 1) * PER_PAGE;");
        sb.append("  filtered.slice(start, start + PER_PAGE).forEach(c => c.style.display = 'flex');");

        // 빈 결과 처리
        sb.append("  const grid = document.getElementById('cardGrid');");
        sb.append("  const emptyEl = document.getElementById('emptyMsg');");
        sb.append("  if (total === 0) {");
        sb.append("    if (!emptyEl) { const d=document.createElement('p');d.id='emptyMsg';");
        sb.append("    d.className='empty';d.textContent='해당 카테고리의 공모전이 없습니다.';");
        sb.append("    grid.parentNode.insertBefore(d, grid.nextSibling); }");
        sb.append("  } else { if (emptyEl) emptyEl.remove(); }");

        sb.append("  document.getElementById('pageInfo').textContent =");
        sb.append("    (currentFilter==='dev'?'💻 개발 공모전 ':'📋 전체 공모전 ')");
        sb.append("    + total + '개 · ' + currentPage + ' / ' + totalPages + ' 페이지';");
        sb.append("  renderPagination(total, totalPages);");
        sb.append("  window.scrollTo({ top: 0, behavior: 'smooth' });");
        sb.append("}");

        // 페이지네이션 렌더
        sb.append("function renderPagination(total, totalPages) {");
        sb.append("  const pg = document.getElementById('pagination');");
        sb.append("  if (totalPages <= 1) { pg.innerHTML = ''; return; }");
        sb.append("  let html = '';");
        sb.append("  html += `<button onclick='goPage(${currentPage-1})' ${currentPage===1?'disabled':''}>◀</button>`;");
        sb.append("  let s = Math.max(1, currentPage-2), e = Math.min(totalPages, s+4);");
        sb.append("  if (e-s < 4) s = Math.max(1, e-4);");
        sb.append("  if (s > 1) html += '<button onclick=\"goPage(1)\">1</button>' + (s>2?'<button disabled>…</button>':'');");
        sb.append("  for (let i=s; i<=e; i++) html += `<button onclick='goPage(${i})' class='${i===currentPage?\"active\":\"\"}'>${i}</button>`;");
        sb.append("  if (e < totalPages) html += (e<totalPages-1?'<button disabled>…</button>':'') + `<button onclick='goPage(${totalPages})'>${totalPages}</button>`;");
        sb.append("  html += `<button onclick='goPage(${currentPage+1})' ${currentPage===totalPages?'disabled':''}>▶</button>`;");
        sb.append("  pg.innerHTML = html;");
        sb.append("}");

        sb.append("function goPage(n) {");
        sb.append("  const totalPages = Math.max(1, Math.ceil(getFiltered().length / PER_PAGE));");
        sb.append("  if (n < 1 || n > totalPages) return;");
        sb.append("  currentPage = n; render();");
        sb.append("}");

        sb.append("render();"); // 초기 실행
        sb.append("</script>");

        sb.append("</body></html>");

        try {
            Files.createDirectories(Paths.get("output"));
            Files.write(Paths.get("output/index.html"), sb.toString().getBytes("UTF-8"));
            System.out.println("index.html 생성 완료");
        } catch (IOException e) {
            System.err.println("저장 실패: " + e.getMessage());
        }
    }

    static String esc(String t) {
        if (t == null) return "";
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
