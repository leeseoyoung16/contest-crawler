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

    // 공모전 정보를 담는 내부 클래스
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

    public static void main(String[] args) {
        System.out.println("🔍 공모전 정보 수집 시작...");

        List<Contest> contests = new ArrayList<>();

        // 여러 페이지 스크래핑 (1~3페이지)
        for (int page = 1; page <= 3; page++) {
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
                            System.out.println("✅ " + title);
                        }
                    } catch (Exception e) {
                        // 개별 항목 파싱 실패 시 스킵
                    }
                }

                Thread.sleep(500); // 서버 부하 방지

            } catch (Exception e) {
                System.err.println("❌ " + page + "페이지 스크래핑 실패: " + e.getMessage());
            }
        }

        System.out.println("\n📊 총 " + contests.size() + "개 공모전 수집 완료");

        // HTML 파일 생성
        generateHTML(contests);

        System.out.println("🎉 index.html 생성 완료!");
    }

    static void generateHTML(List<Contest> contests) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));

        StringBuilder sb = new StringBuilder();
        sb.append("""
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>📋 오늘의 공모전 정보</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', 'Apple SD Gothic Neo', sans-serif;
            background: #f0f4f8;
            color: #2d3748;
        }
        header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px 20px;
            text-align: center;
        }
        header h1 { font-size: 2rem; margin-bottom: 8px; }
        header p  { font-size: 1rem; opacity: 0.85; }
        .badge {
            display: inline-block;
            background: rgba(255,255,255,0.25);
            padding: 4px 14px;
            border-radius: 20px;
            font-size: 0.85rem;
            margin-top: 10px;
        }
        .container { max-width: 1100px; margin: 30px auto; padding: 0 20px; }
        .stats {
            background: white;
            border-radius: 12px;
            padding: 20px 30px;
            margin-bottom: 24px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.07);
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 1.1rem;
            font-weight: 600;
            color: #4a5568;
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
            gap: 20px;
        }
        .card {
            background: white;
            border-radius: 12px;
            padding: 22px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.07);
            transition: transform 0.2s, box-shadow 0.2s;
            border-left: 4px solid #667eea;
        }
        .card:hover {
            transform: translateY(-3px);
            box-shadow: 0 8px 24px rgba(0,0,0,0.12);
        }
        .card-title {
            font-size: 1rem;
            font-weight: 700;
            color: #2d3748;
            margin-bottom: 12px;
            line-height: 1.4;
        }
        .card-title a {
            color: inherit;
            text-decoration: none;
        }
        .card-title a:hover { color: #667eea; }
        .info-row {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 0.85rem;
            color: #718096;
            margin-bottom: 6px;
        }
        .tag {
            display: inline-block;
            background: #ebf4ff;
            color: #3182ce;
            padding: 2px 10px;
            border-radius: 12px;
            font-size: 0.78rem;
            font-weight: 600;
            margin-bottom: 10px;
        }
        .deadline {
            background: #fff5f5;
            color: #e53e3e;
            padding: 4px 10px;
            border-radius: 8px;
            font-size: 0.82rem;
            font-weight: 600;
        }
        footer {
            text-align: center;
            padding: 40px 20px;
            color: #a0aec0;
            font-size: 0.85rem;
        }
        footer a { color: #667eea; text-decoration: none; }
    </style>
</head>
<body>
<header>
    <h1>📋 오늘의 공모전 정보</h1>
    <p>공모전코리아에서 자동 수집한 최신 공모전 목록</p>
    <div class="badge">🔄 매일 자동 업데이트</div>
</header>
<div class="container">
    <div class="stats">
        📅 """).append(today).append(" 기준 &nbsp;|&nbsp; 총 <span style='color:#667eea;margin:0 4px'>")
                .append(contests.size()).append("</span>개 공모전\n    </div>\n    <div class=\"grid\">\n");

        for (Contest c : contests) {
            sb.append("        <div class=\"card\">\n");
            if (!c.category.isEmpty()) {
                sb.append("            <div class=\"tag\">").append(escapeHtml(c.category)).append("</div>\n");
            }
            sb.append("            <div class=\"card-title\">")
              .append("<a href=\"").append(escapeHtml(c.link)).append("\" target=\"_blank\">")
              .append(escapeHtml(c.title)).append("</a></div>\n");
            if (!c.host.isEmpty()) {
                sb.append("            <div class=\"info-row\">🏢 ").append(escapeHtml(c.host)).append("</div>\n");
            }
            if (!c.deadline.isEmpty()) {
                sb.append("            <div class=\"info-row\">")
                  .append("<span class=\"deadline\">⏰ ").append(escapeHtml(c.deadline)).append("</span></div>\n");
            }
            sb.append("        </div>\n");
        }

        sb.append("""
    </div>
</div>
<footer>
    <p>데이터 출처: <a href="https://www.contestkorea.com" target="_blank">공모전코리아</a> &nbsp;|&nbsp;
    매일 오전 9시 자동 업데이트</p>
</footer>
</body>
</html>
""");

        try {
            // output 디렉토리 생성
            Files.createDirectories(Paths.get("output"));
            Files.writeString(Paths.get("output/index.html"), sb.toString());
        } catch (IOException e) {
            System.err.println("❌ HTML 파일 저장 실패: " + e.getMessage());
        }
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
