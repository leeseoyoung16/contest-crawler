import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ContestCrawler {

    static class Contest {
        String title, host, deadline, description, link;
        Contest(String title, String host, String deadline, String description, String link) {
            this.title = title; this.host = host; this.deadline = deadline;
            this.description = description; this.link = link;
        }
    }

    public static void main(String[] args) {
        System.out.println("공모전 수집 시작");
        List<Contest> contests = new ArrayList<>();
        Set<String> seenLinks = new HashSet<>();

        // IT/정보통신 카테고리 URL로 처음부터 필터링
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
                    String title = link.text().trim();

                    // 사이드바 링크 제거 (숫자. 로 시작)
                    if (title.matches("^\\d+\\..*")) continue;
                    // 카테고리 prefix 제거
                    title = title.replaceAll("^(학문|미술|사진|문학|네이밍|기획|아이디어|캐릭터|공연|건축|창업|기타)[^가-힣]*", "").trim();
                    if (title.length() < 5) continue;

                    String href = link.attr("href");
                    String fullLink = href.startsWith("http") ? href : "https://www.contestkorea.com/sub/" + href;

                    if (seenLinks.contains(fullLink)) continue;
                    seenLinks.add(fullLink);

                    // 상세 페이지에서 마감일, 주최, 설명 가져오기
                    String host = "", deadline = "", description = "";
                    try {
                        Document detail = Jsoup.connect(fullLink)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                                .referrer("https://www.contestkorea.com")
                                .timeout(15000)
                                .get();

                        Elements trs = detail.select("tr");
                        for (Element tr : trs) {
                            String th = tr.select("th").text().trim();
                            String td = tr.select("td").text().trim();
                            if (host.isEmpty() && (th.contains("주최") || th.contains("주관")))
                                host = td.length() > 30 ? td.substring(0, 30) + "..." : td;
                            if (deadline.isEmpty() && (th.contains("접수") || th.contains("마감") || th.contains("기간")))
                                deadline = td.length() > 50 ? td.substring(0, 50) : td;
                        }

                        Elements paras = detail.select(".view_con p, .board_view p, .cont_view p, #content p");
                        for (Element p : paras) {
                            String txt = p.text().trim();
                            if (txt.length() > 20) {
                                description = txt.length() > 100 ? txt.substring(0, 100) + "..." : txt;
                                break;
                            }
                        }
                        Thread.sleep(400);
                    } catch (Exception e) {
                        System.err.println("상세 실패: " + title);
                    }

                    contests.add(new Contest(title, host, deadline, description, fullLink));
                    System.out.println("수집: " + title);
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println(page + "페이지 실패: " + e.getMessage());
            }
        }

        System.out.println("총 " + contests.size() + "개 수집");
        generateHTML(contests);
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
            if (!c.description.isEmpty())
                sb.append("<div class='card-desc'>").append(esc(c.description)).append("</div>");
            sb.append("<div class='card-meta'>");
            if (!c.host.isEmpty())
                sb.append("<div class='meta-row'><span class='meta-label'>주최</span><span>")
                        .append(esc(c.host)).append("</span></div>");
            if (!c.deadline.isEmpty())
                sb.append("<div class='meta-row'><span class='meta-label'>마감</span>")
                        .append("<span class='deadline-badge'>").append(esc(c.deadline)).append("</span></div>");
            sb.append("</div>");
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
        return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("'","&#39;");
    }
}