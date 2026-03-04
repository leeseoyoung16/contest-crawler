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
        public String collectedDate; // 수집일 기록

        public Contest() {}
        Contest(String title, String host, String deadline, String description, String link) {
            this.title = title;
            this.host = host;
            this.deadline = deadline;
            this.description = description;
            this.link = link;
            this.collectedDate = LocalDate.now().toString(); // 수집일 자동 기록
        }
    }

    static final String JSON_PATH = "output/contests.json";
    static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        System.out.println("공모전 수집 시작");

        // ✅ 1. 기존 JSON 불러오기
        List<Contest> existing = loadExisting();
        System.out.println("기존 데이터: " + existing.size() + "개");

        // ✅ 2. 오늘 크롤링
        List<Contest> crawled = crawl();
        System.out.println("오늘 수집: " + crawled.size() + "개");

        // ✅ 3. 기존 + 신규 merge (link 기준 중복 제거)
        Map<String, Contest> merged = new LinkedHashMap<>();
        for (Contest c : existing) merged.put(c.link, c);
        for (Contest c : crawled)  merged.put(c.link, c); // 신규가 기존 덮어씀

        // ✅ 4. 마감일 지난 항목 제거
        LocalDate today = LocalDate.now();
        List<Contest> filtered = new ArrayList<>();
        int expiredCount = 0;

        for (Contest c : merged.values()) {
            LocalDate deadline = parseDeadline(c.deadline);
            if (deadline == null || !deadline.isBefore(today)) {
                // 마감일 파싱 불가 → 일단 유지
                // 마감일이 오늘 이후 → 유지
                filtered.add(c);
            } else {
                System.out.println("만료 제거: " + c.title + " (" + c.deadline + ")");
                expiredCount++;
            }
        }
        System.out.println("만료 제거: " + expiredCount + "개 / 최종: " + filtered.size() + "개");

        // ✅ 5. JSON 저장 (다음 실행 때 불러올 DB 역할)
        saveJSON(filtered);

        // ✅ 6. HTML 생성
        generateHTML(filtered);
    }

    // ─────────────────────────────────────────
    // 기존 JSON 불러오기
    // ─────────────────────────────────────────
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

    // ─────────────────────────────────────────
    // JSON 저장
    // ─────────────────────────────────────────
    static void saveJSON(List<Contest> contests) {
        try {
            Files.createDirectories(Paths.get("output"));
            mapper.writeValue(Paths.get(JSON_PATH).toFile(), contests);
            System.out.println("contests.json 저장 완료");
        } catch (IOException e) {
            System.err.println("JSON 저장 실패: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // 마감일 파싱 — 여러 형식 지원
    // ─────────────────────────────────────────
    static LocalDate parseDeadline(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // 지원 형식 목록
        String[] patterns = {
                "yyyy.MM.dd", "yyyy-MM-dd", "yyyy/MM/dd",
                "yyyy년 MM월 dd일", "yyyy년MM월dd일",
                "MM.dd", "MM-dd"
        };

        // 숫자만 추출해서 시도
        String cleaned = raw.replaceAll("[^0-9.\\-/년월일]", "").trim();

        for (String pattern : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
                // MM.dd 처럼 연도 없는 경우 올해로 보정
                if (!pattern.contains("yyyy")) {
                    return LocalDate.parse(
                            LocalDate.now().getYear() + "." + cleaned,
                            DateTimeFormatter.ofPattern("yyyy." + pattern)
                    );
                }
                return LocalDate.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {}
        }

        // "~yyyy.MM.dd" 형식에서 마지막 날짜 추출
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
        return last; // 마지막으로 찾은 날짜 (범위면 종료일)
    }

    // ─────────────────────────────────────────
    // 크롤링
    // ─────────────────────────────────────────
    static List<Contest> crawl() {
        List<Contest> contests = new ArrayList<>();
        Set<String> seenLinks  = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();

        for (int page = 1; page <= 5; page++) {
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

                    // 카테고리 접두어 제거
                    String title = rawTitle.replaceAll(
                            "^(학문|과학|미술|사진|문학|네이밍|기획|아이디어|캐릭터|공연|건축|창업|기타|문예)" +
                                    "([•·/\\s]*(IT|SW|과학|미술|디자인|웹툰|음악|체육|기타))*[^가-힣0-9a-zA-Z]*", ""
                    ).trim();

                    String cleanTitle = title.replaceAll("^\\d+\\.\\s*", "").trim();
                    if (cleanTitle.length() < 5) continue;

                    String href = link.attr("href");
                    String fullLink = href.startsWith("http") ? href
                            : "https://www.contestkorea.com/sub/" + href;

                    if (seenLinks.contains(fullLink))   continue;
                    if (seenTitles.contains(cleanTitle)) continue;
                    seenTitles.add(cleanTitle);
                    seenLinks.add(fullLink);

                    // 키워드 필터
                    String[] devKeywords = {
                            "개발", "SW", "소프트웨어", "앱", "해커톤", "hackathon",
                            "프로그래밍", "코딩", "인공지능", "AI", "빅데이터",
                            "클라우드", "웹", "모바일", "게임", "보안", "사이버",
                            "디지털", "ICT", "정보통신", "알고리즘", "데이터사이언스",
                            "아이디어톤", "AX", "DX", "tech", "테크",
                            "IT경시", "IT 경시", "전산", "시스템", "플랫폼",
                            "자동화", "로봇"
                    };

                    String titleLower = cleanTitle.toLowerCase();
                    boolean isDevRelated = false;
                    for (String kw : devKeywords) {
                        if (titleLower.contains(kw.toLowerCase())) {
                            isDevRelated = true;
                            break;
                        }
                    }
                    if (!isDevRelated) {
                        System.out.println("스킵(비개발): " + cleanTitle);
                        continue;
                    }

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

                    contests.add(new Contest(cleanTitle, host, deadline, description, fullLink));
                    System.out.println("수집: " + cleanTitle);
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println(page + "페이지 실패: " + e.getMessage());
            }
        }
        return contests;
    }

    // ─────────────────────────────────────────
    // HTML 생성
    // ─────────────────────────────────────────
    static void generateHTML(List<Contest> contests) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='ko'><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width,initial-scale=1.0'>");
        sb.append("<title>개발자 공모전</title><style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}");
        sb.append("body{font-family:'Malgun Gothic',sans-serif;background:#f0f4f8;color:#2d3748}");
        sb.append("header{background:linear-gradient(135deg,#667eea,#764ba2);color:white;padding:48px 20px;text-align:center}");
        sb.append("header h1{font-size:2.2rem;margin-bottom:10px}");
        sb.append("header p{opacity:.85;margin-bottom:12px}");
        sb.append(".badge{display:inline-block;background:rgba(255,255,255,0.2);padding:5px 16px;border-radius:20px;font-size:.85rem}");
        sb.append(".container{max-width:1200px;margin:32px auto;padding:0 20px}");
        sb.append(".stats{background:white;border-radius:14px;padding:18px 24px;margin-bottom:28px;");
        sb.append("box-shadow:0 2px 10px rgba(0,0,0,.07);font-size:1rem;color:#4a5568}");
        sb.append(".count{color:#667eea;font-weight:700;font-size:1.3rem}");
        sb.append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:22px}");
        sb.append(".card{background:white;border-radius:14px;padding:24px;box-shadow:0 2px 10px rgba(0,0,0,.07);");
        sb.append("border-top:4px solid #667eea;display:flex;flex-direction:column;gap:10px;transition:transform .2s,box-shadow .2s}");
        sb.append(".card:hover{transform:translateY(-4px);box-shadow:0 10px 28px rgba(0,0,0,.12)}");
        sb.append(".card-title{font-size:1.05rem;font-weight:700;line-height:1.45;color:#1a202c}");
        sb.append(".card-title a{color:inherit;text-decoration:none}");
        sb.append(".card-title a:hover{color:#667eea}");
        sb.append(".card-desc{font-size:.88rem;color:#718096;line-height:1.6}");
        sb.append(".card-meta{display:flex;flex-direction:column;gap:6px;margin-top:auto;padding-top:10px;border-top:1px solid #e2e8f0}");
        sb.append(".meta-row{display:flex;align-items:flex-start;gap:8px;font-size:.83rem;color:#718096}");
        sb.append(".meta-label{font-weight:700;color:#4a5568;min-width:36px;flex-shrink:0}");
        sb.append(".deadline-badge{background:#fff5f5;color:#e53e3e;padding:2px 8px;border-radius:6px;font-weight:600}");
        sb.append(".collected{font-size:.75rem;color:#a0aec0;margin-top:4px}");  // 수집일 표시
        sb.append(".btn{display:block;margin-top:10px;padding:9px;background:#667eea;color:white;");
        sb.append("border-radius:8px;font-size:.88rem;text-decoration:none;font-weight:600;text-align:center}");
        sb.append(".btn:hover{background:#5a67d8}");
        sb.append("footer{text-align:center;padding:48px 20px;color:#a0aec0;font-size:.85rem}");
        sb.append("footer a{color:#667eea;text-decoration:none}");
        sb.append("</style></head><body>");
        sb.append("<header><h1>개발자 공모전 정보</h1>");
        sb.append("<p>IT/SW/AI 관련 공모전을 매일 자동 수집합니다</p>");
        sb.append("<div class='badge'>매일 오전 9시 자동 업데이트</div></header>");
        sb.append("<div class='container'>");
        sb.append("<div class='stats'>📅 ").append(today)
                .append(" 기준 &nbsp;|&nbsp; 총 <span class='count'>").append(contests.size()).append("</span>개 공모전</div>");
        sb.append("<div class='grid'>");

        for (Contest c : contests) {
            sb.append("<div class='card'>");
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
            // ✅ 수집일 표시
            if (c.collectedDate != null)
                sb.append("<div class='collected'>🗓 수집일: ").append(esc(c.collectedDate)).append("</div>");
            sb.append("<a class='btn' href='").append(esc(c.link)).append("' target='_blank'>자세히 보기</a>");
            sb.append("</div>");
        }

        sb.append("</div></div>");
        sb.append("<footer><p>데이터 출처: <a href='https://www.contestkorea.com' target='_blank'>공모전코리아</a>");
        sb.append(" | 매일 오전 9시 자동 업데이트</p></footer></body></html>");

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
        return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("'","&#39;");
    }
}
