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
        String title, host, deadline, link;
        Contest(String title, String host, String deadline, String link) {
            this.title = title;
            this.host = host;
            this.deadline = deadline;
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

                // 실제 HTML 구조 디버깅용 출력
                if (page == 1) {
                    System.out.println("HTML 길이: " + doc.html().length());
                    Elements allLinks = doc.select("a[href*=str_no]");
                    System.out.println("공모전 링크 수: " + allLinks.size());
                    for (int i = 0; i < Math.min(3, allLinks.size()); i++) {
                        System.out.println("샘플링크: " + allLinks.get(i).text() + " | " + allLinks.get(i).attr("href"));
                    }
                }

                // 방법1: str_no 파라미터가 있는 링크로 공모전 목록 수집
                Elements links = doc.select("a[href*=str_no]");
                for (Element link : links) {
                    String title = link.text().trim();
                    String href = link.attr("href");
                    String fullLink = href.startsWith("http") ? href : "https://www.contestkorea.com/sub/" + href;

                    if (title.length() > 5) {
                        Element parent = link.parent();
                        String deadline = "";
                        String host = "";
                        if (parent != null) {
                            Elements spans = parent.select("span");
                            for (Element span : spans) {
                                String txt = span.text();
                                if (txt.contains("~") || txt.matches(".*\\d{2}\\.\\d{2}.*")) {
                                    deadline = txt;
                                }
                            }
                        }
                        if (isDevContest(title)) {
                            contests.add(new Contest(title, host, deadline, fullLink));
                            System.out.println("수집: " + title);
                        }
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
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='ko'><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width,initial-scale=1.0'>");
        sb.append("<title>개발자 공모전</title><style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}");
        sb.append("body{font-family:sans-serif;background:#f0f4f8;color:#2d3748}");
        sb.append("header{background:linear-gradient(135deg,#667eea,#764ba2);color:white;padding:40px;text-align:center}");
        sb.append("header h1{font-size:2rem;margin-bottom:8px}");
        sb.append(".container{max-width:1100px;margin:30px auto;padding:0 20px}");
        sb.append(".stats{background:white;border-radius:12px;padding:20px;margin-bottom:24px;box-shadow:0 2px 8px rgba(0,0,0,.07);font-weight:600}");
        sb.append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:20px}");
        sb.append(".card{background:white;border-radius:12px;padding:20px;box-shadow:0 2px 8px rgba(0,0,0,.07);border-left:4px solid #667eea;transition:transform .2s}");
        sb.append(".card:hover{transform:translateY(-3px)}");
        sb.append(".title{font-weight:700;margin-bottom:10px;line-height:1.4}");
        sb.append(".title a{color:#2d3748;text-decoration:none}");
        sb.append(".title a:hover{color:#667eea}");
        sb.append(".info{font-size:.85rem;color:#718096;margin-bottom:5px}");
        sb.append(".deadline{background:#fff5f5;color:#e53e3e;padding:3px 8px;border-radius:6px;font-weight:600}");
        sb.append("footer{text-align:center;padding:40px;color:#a0aec0;font-size:.85rem}");
        sb.append("footer a{color:#667eea;text-decoration:none}");
        sb.append("</style></head><body>");
        sb.append("<header><h1>개발자 공모전 정보</h1>");
        sb.append("<p>IT/SW/AI 관련 공모전 매일 자동 수집</p></header>");
        sb.append("<div class='container'>");
        sb.append("<div class='stats'>").append(today).append(" 기준 | 총 ")
                .append(contests.size()).append("개 개발자 공모전</div>");
        sb.append("<div class='grid'>");
        for (Contest c : contests) {
            sb.append("<div class='card'>");
            sb.append("<div class='title'><a href='").append(esc(c.link))
                    .append("' target='_blank'>").append(esc(c.title)).append("</a></div>");
            if (!c.deadline.isEmpty())
                sb.append("<div class='info'><span class='deadline'>마감: ")
                        .append(esc(c.deadline)).append("</span></div>");
            sb.append("</div>");
        }
        sb.append("</div></div>");
        sb.append("<footer><p>출처: <a href='https://www.contestkorea.com' target='_blank'>공모전코리아</a> | 매일 오전 9시 자동 업데이트</p></footer>");
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