import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ContestCrawler {

    static class Contest {
        String title, host, deadline, description, link;
        Contest(String title, String host, String deadline, String description, String link) {
            this.title = title;
            this.host = host;
            this.deadline = deadline;
            this.description = description;
            this.link = link;
        }
    }

    static final String[] DEV_KEYWORDS = {
            "개발", "IT", "SW", "소프트웨어", "앱", "해커톤",
            "프로그래밍", "코딩", "인공지능", "AI", "빅데이터",
            "클라우드", "웹", "모바일", "게임", "데이터", "보안", "디지털", "정보"
    };

    public static void main(String[] args) {
        System.out.println("공모전 수집 시작");
        List<Contest> contests = new ArrayList<>();

        for (int page = 1; page <= 5; page++) {
            try {
                String url = "https://www.contestkorea.com/sub/list.php"
                        + "?display=1&int_gbn=1&Txt_bkk=0&Txt_stt=1&page=" + page;
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                        .referrer("https://www.contestkorea.com")
                        .timeout(15000)
                        .get();

                Elements links = doc.select("a[href*=str_no]");
                for (Element link : links) {
                    String title = link.text().trim();
                    String href = link.attr("href");
                    String fullLink = href.startsWith("http") ? href : "https://www.contestkorea.com/sub/" + href;

                    if (title.length() > 5 && isDevContest(title)) {
                        // 상세 페이지에서 마감일, 주최, 설명 가져오기
                        String host = "";
                        String deadline = "";
                        String description = "";
                        try {
                            Document detail = Jsoup.connect(fullLink)
                                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                                    .referrer("https://www.contestkorea.com")
                                    .timeout(10000)
                                    .get();

                            // 주최
                            Elements rows = detail.select("table tr, dl dt, .view_info li");
                            for (Element row : rows) {
                                String text = row.text();
                                if (text.contains("주최") || text.contains("주관")) {
                                    host = row.text().replaceAll("주최.*?:", "").replaceAll("주관.*?:", "").trim();
                                    if (host.length() > 30) host = host.substring(0, 30) + "...";
                                    break;
                                }
                            }

                            // 마감일
                            for (Element row : rows) {
                                String text = row.text();
                                if (text.contains("마감") || text.contains("접수") || text.contains("기간")) {
                                    deadline = row.text().replaceAll(".*마감.*?:", "").replaceAll(".*접수.*?:", "").trim();
                                    if (deadline.length() > 40) deadline = deadline.substring(0, 40);
                                    break;
                                }
                            }

                            // 간단 설명 (본문 첫 문장)
                            Elements content = detail.select(".view_txt p, .contest_view p, .view_content p");
                            if (!content.isEmpty()) {
                                description = content.first().text().trim();
                                if (description.length() > 80) description = description.substring(0, 80) + "...";
                            }

                            Thread.sleep(300);
                        } catch (Exception e) {
                            System.err.println("상세 페이지 실패: " + title);
                        }

                        contests.add(new Contest(title, host, deadline, description, fullLink));
                        System.out.println("수집: " + title);
                    }
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println(page + "페이지 실패: " + e.getMessage());
            }
        }

        System.out.println("총 " + contests.size() + "개 수집");
        generateHTML(contests);
    }

    static boolean isDevContest(String title) {
        for (String kw : DEV_KEYWORDS) {
            if (title.contains(kw)) return true;
        }
        return false;
    }

    static void generateHTML(List<Contest> contests) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='ko'><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width,initial-scale=1.0'>");
        sb.append("<title>개발자 공모전</title><style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}");
        sb.append("body{font-family:'Malgun Gothic',sans-serif;background:#f0f4f8;color:#2d3748}");
        sb.append("header{background:linear-gradient(135deg,#667eea,#764ba2);color:white;padding:48px 20px;text-align:center}");
        sb.append("header h1{font-size:2.2rem;margin-bottom:10px;letter-spacing:-0.5px}");
        sb.append("header p{opacity:.85;margin-bottom:12px}");
        sb.append(".badge{display:inline-block;background:rgba(255,255,255,0.2);padding:5px 16px;border-radius:20px;font-size:.85rem}");
        sb.append(".container{max-width:1200px;margin:32px auto;padding:0 20px}");
        sb.append(".stats{background:white;border-radius:14px;padding:18px 24px;margin-bottom:28px;");
        sb.append("box-shadow:0 2px 10px rgba(0,0,0,.07);font-size:1rem;color:#4a5568;display:flex;align-items:center;gap:8px}");
        sb.append(".count{color:#667eea;font-weight:700;font-size:1.3rem}");
        sb.append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:22px}");
        sb.append(".card{background:white;border-radius:14px;padding:24px;box-shadow:0 2px 10px rgba(0,0,0,.07);");
        sb.append("border-top:4px solid #667eea;transition:transform .2s,box-shadow .2s;display:flex;flex-direction:column;gap:12px}");
        sb.append(".card:hover{transform:translateY(-4px);box-shadow:0 10px 28px rgba(0,0,0,.12)}");
        sb.append(".card-title{font-size:1.05rem;font-weight:700;line-height:1.45;color:#1a202c}");
        sb.append(".card-title a{color:inherit;text-decoration:none}");
        sb.append(".card-title a:hover{color:#667eea}");
        sb.append(".card-desc{font-size:.88rem;color:#718096;line-height:1.6}");
        sb.append(".card-meta{display:flex;flex-direction:column;gap:6px;margin-top:auto}");
        sb.append(".meta-row{display:flex;align-items:center;gap:6px;font-size:.83rem;color:#718096}");
        sb.append(".meta-label{font-weight:600;color:#4a5568;min-width:40px}");
        sb.append(".deadline-badge{display:inline-block;background:#fff5f5;color:#e53e3e;");
        sb.append("padding:3px 10px;border-radius:8px;font-size:.82rem;font-weight:600}");
        sb.append(".btn{display:inline-block;margin-top:8px;padding:8px 16px;background:#667eea;");
        sb.append("color:white;border-radius:8px;font-size:.85rem;text-decoration:none;font-weight:600;text-align:center}");
        sb.append(".btn:hover{background:#5a67d8}");
        sb.append("footer{text-align:center;padding:48px 20px;color:#a0aec0;font-size:.85rem}");
        sb.append("footer a{color:#667eea;text-decoration:none}");
        sb.append("</style></head><body>");

        sb.append("<header>");
        sb.append("<h1>개발자 공모전 정보</h1>");
        sb.append("<p>IT/SW/AI 관련 공모전을 매일 자동 수집합니다</p>");
        sb.append("<div class='badge'>매일 오전 9시 자동 업데이트</div>");
        sb.append("</header>");

        sb.append("<div class='container'>");
        sb.append("<div class='stats'>");
        sb.append("📅 ").append(today).append(" 기준 &nbsp;|&nbsp; 총 <span class='count'>")
                .append(contests.size()).append("</span>개 공모전</div>");

        sb.append("<div class='grid'>");
        for (Contest c : contests) {
            sb.append("<div class='card'>");
            sb.append("<div class='card-title'><a href='").append(esc(c.link))
                    .append("' target='_blank'>").append(esc(c.title)).append("</a></div>");

            if (!c.description.isEmpty()) {
                sb.append("<div class='card-desc'>").append(esc(c.description)).append("</div>");
            }

            sb.append("<div class='card-meta'>");
            if (!c.host.isEmpty()) {
                sb.append("<div class='meta-row'><span class='meta-label'>주최</span>")
                        .append(esc(c.host)).append("</div>");
            }
            if (!c.deadline.isEmpty()) {
                sb.append("<div class='meta-row'><span class='meta-label'>마감</span>")
                        .append("<span class='deadline-badge'>").append(esc(c.deadline)).append("</span></div>");
            }
            sb.append("</div>");
            sb.append("<a class='btn' href='").append(esc(c.link)).append("' target='_blank'>자세히 보기</a>");
            sb.append("</div>");
        }
        sb.append("</div></div>");

        sb.append("<footer><p>데이터 출처: <a href='https://www.contestkorea.com' target='_blank'>공모전코리아</a>");
        sb.append(" &nbsp;|&nbsp; 매일 오전 9시 자동 업데이트</p></footer>");
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
        return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("'","&#39;");
    }
}