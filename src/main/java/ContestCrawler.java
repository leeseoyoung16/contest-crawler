import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ContestCrawler {

    static class Contest {
        String title;
        String category;
        String host;
        String deadline;
        String link;

        Contest(String title, String category, String host, String deadline, String link) {
            this.title = title;
            this.category = category;
            this.host = host;
            this.deadline = deadline;
            this.link = link;
        }
    }

    static final String[] DEV_KEYWORDS = {
            "개발", "IT", "SW", "소프트웨어", "앱", "App", "해커톤", "Hackathon",
            "프로그래밍", "코딩", "인공지능", "AI", "빅데이터", "클라우드",
            "웹", "모바일", "게임", "정보", "데이터", "보안", "사이버", "디지털"
    };

    public static void main(String[] args) {
        System.out.println("공모전 정보 수집 시작...");

        List<Contest> contests = new ArrayList<>();

        for (int page = 1; page <= 5; page++) {
            try {
                String url = "https://www.contestkorea.com/sub/list.php"
                        + "?display=1&int_gbn=1&Txt_bkk=0&Txt_stt=1&page=" + page;

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get();

                Elements items = doc.select("ul.list_style_4 > li");

                for (Element item : items) {
                    try {
                        String title    = item.select("a.txt").text().trim();
                        String category = item.select("span.code").text().trim();
                        String host     = item.select("span.inte").text().trim();
                        String deadline = item.select("span.day").text().trim();
                        String href     = item.select("a.txt").attr("href");
                        String link     = "https://www.contestkorea.com/sub/" + href;

                        if (!title.isEmpty()) {
                            contests.add(new Contest(title, category, host, deadline, link));
                            System.out.println("수집: " + title);
                        }
                    } catch (Exception e) {
                        // skip
                    }
                }

                Thread.sleep(500);

            } catch (Exception e) {
                System.err.println(page + "페이지 실패: " + e.getMessage());
            }
        }

        System.out.println("총 " + contests.size() + "개 개발자 공모전 수집 완료");
        generateHTML(contests);
        System.out.println("index.html 생성 완료!");
    }

    static boolean isDevContest(String title, String category) {
        for (String keyword : DEV_KEYWORDS) {
            if (title.contains(keyword) || category.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    static void generateHTML(List<Contest> contests) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"ko\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>개발자 공모전 정보</title>\n");
        sb.append("  <style>\n");
        sb.append("    * { margin:0; padding:0; box-sizing:border-box; }\n");
        sb.append("    body { font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; background:#f0f4f8; color:#2d3748; }\n");
        sb.append("    header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color:white; padding:40px 20px; text-align:center; }\n");
        sb.append("    header h1 { font-size:2rem; margin-bottom:8px; }\n");
        sb.append("    header p { font-size:1rem; opacity:0.85; }\n");
        sb.append("    .badge { display:inline-block; background:rgba(255,255,255,0.25); padding:4px 14px; border-radius:20px; font-size:0.85rem; margin-top:10px; }\n");
        sb.append("    .container { max-width:1100px; margin:30px auto; padding:0 20px; }\n");
        sb.append("    .stats { background:white; border-radius:12px; padding:20px 30px; margin-bottom:24px; box-shadow:0 2px 8px rgba(0,0,0,0.07); font-size:1.1rem; font-weight:600; color:#4a5568; }\n");
        sb.append("    .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(320px,1fr)); gap:20px; }\n");
        sb.append("    .card { background:white; border-radius:12px; padding:22px; box-shadow:0 2px 8px rgba(0,0,0,0.07); border-left:4px solid #667eea; transition:transform 0.2s; }\n");
        sb.append("    .card:hover { transform:translateY(-3px); box-shadow:0 8px 24px rgba(0,0,0,0.12); }\n");
        sb.append("    .card-title { font-size:1rem; font-weight:700; color:#2d3748; margin-bottom:12px; line-height:1.4; }\n");
        sb.append("    .card-title a { color:inherit; text-decoration:none; }\n");
        sb.append("    .card-title a:hover { color:#667eea; }\n");
        sb.append("    .info-row { font-size:0.85rem; color:#718096; margin-bottom:6px; }\n");
        sb.append("    .tag { display:inline-block; background:#ebf4ff; color:#3182ce; padding:2px 10px; border-radius:12px; font-size:0.78rem; font-weight:600; margin-bottom:10px; }\n");
        sb.append("    .deadline { background:#fff5f5; color:#e53e3e; padding:4px 10px; border-radius:8px; font-size:0.82rem; font-weight:600; }\n");
        sb.append("    .empty { text-align:center; padding:60px; color:#a0aec0; font-size:1.1rem; }\n");
        sb.append("    footer { text-align:center; padding:40px 20px; color:#a0aec0; font-size:0.85rem; }\n");
        sb.append("    footer a { color:#667eea; text-decoration:none; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<header>\n");
        sb.append("  <h1>개발자 공모전 정보</h1>\n");
        sb.append("  <p>IT/SW/AI 관련 공모전을 매일 자동 수집합니다</p>\n");
        sb.append("  <div class=\"badge\">매일 자동 업데이트</div>\n");
        sb.append("</header>\n");
        sb.append("<div class=\"container\">\n");
        sb.append("  <div class=\"stats\">\n");
        sb.append("    ").append(today).append(" 기준 &nbsp;|&nbsp; 총 <span style=\"color:#667eea\">")
                .append(contests.size()).append("</span>개 개발자 공모전\n");
        sb.append("  </div>\n");

        if (contests.isEmpty()) {
            sb.append("  <div class=\"empty\">오늘은 수집된 개발자 공모전이 없습니다.</div>\n");
        } else {
            sb.append("  <div class=\"grid\">\n");
            for (Contest c : contests) {
                sb.append("    <div class=\"card\">\n");
                if (!c.category.isEmpty()) {
                    sb.append("      <div class=\"tag\">").append(escapeHtml(c.category)).append("</div>\n");
                }
                sb.append("      <div class=\"card-title\">")
                        .append("<a href=\"").append(escapeHtml(c.link)).append("\" target=\"_blank\">")
                        .append(escapeHtml(c.title)).append("</a></div>\n");
                if (!c.host.isEmpty()) {
                    sb.append("      <div class=\"info-row\">주최: ").append(escapeHtml(c.host)).append("</div>\n");
                }
                if (!c.deadline.isEmpty()) {
                    sb.append("      <div class=\"info-row\"><span class=\"deadline\">마감: ")
                            .append(escapeHtml(c.deadline)).append("</span></div>\n");
                }
                sb.append("    </div>\n");
            }
            sb.append("  </div>\n");
        }

        sb.append("</div>\n");
        sb.append("<footer>\n");
        sb.append("  <p>데이터 출처: <a href=\"https://www.contestkorea.com\" target=\"_blank\">공모전코리아</a> &nbsp;|&nbsp; 매일 오전 9시 자동 업데이트</p>\n");
        sb.append("</footer>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        try {
            Files.createDirectories(Paths.get("output"));
            Files.write(Paths.get("output/index.html"), sb.toString().getBytes("UTF-8"));
        } catch (IOException e) {
            System.err.println("HTML 저장 실패: " + e.getMessage());
        }
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}