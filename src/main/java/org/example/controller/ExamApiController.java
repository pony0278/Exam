package org.example.controller;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.example.model.Question;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exam")
public class ExamApiController {

    // 控制並發數量，但使用較小的數值
    private final Semaphore semaphore = new Semaphore(3); // 降到3個並發

    // 使用較小的快取配置
    private final Cache<String, String> cache = CacheBuilder.newBuilder()
            .maximumSize(50)  // 降到50個
            .expireAfterWrite(3, TimeUnit.MINUTES)  // 降到3分鐘
            .build();

//    @PostMapping("/generate")
//    public ResponseEntity<String> generateExam(@RequestBody List<Question> questions) {
//        try {
//            // 基本驗證
//            if (questions == null || questions.isEmpty()) {
//                return ResponseEntity.badRequest().body("No questions provided");
//            }
//
//            // 限制題目數量
//            if (questions.size() > 30) { // 限制更小
//                return ResponseEntity.badRequest().body("Too many questions. Maximum is 30");
//            }
//
//            // 嘗試獲取信號量
//            if (!semaphore.tryAcquire(2, TimeUnit.SECONDS)) { // 等待時間縮短
//                return ResponseEntity.status(429)
//                        .header("Retry-After", "3")
//                        .body("系統忙碌中，請稍後再試");
//            }
//
//            try {
//                // 檢查快取
//                String cacheKey = generateCacheKey(questions);
//                String cachedHtml = cache.getIfPresent(cacheKey);
//                if (cachedHtml != null) {
//                    return createResponse(cachedHtml);
//                }
//
//                // 生成新試卷
//                String examHtml = generateExamHtml(questions);
//                cache.put(cacheKey, examHtml);
//                return createResponse(examHtml);
//
//            } finally {
//                semaphore.release(); // 確保釋放信號量
//            }
//
//        } catch (Exception e) {
//            // 確保記錄錯誤
//            System.err.println("Error generating exam: " + e.getMessage());
//            e.printStackTrace();
//            return ResponseEntity.status(500)
//                    .body("Error generating exam: " + e.getMessage());
//        }
//    }
    @PostMapping("/generate")
    public ResponseEntity<String> generateExam(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            // 並發控制
            if (!semaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                return ResponseEntity.status(429)
                        .header("Retry-After", "3")
                        .body("系統忙碌中，請稍後再試");
            }

            try {
                // 讀取並解析 TXT
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                List<Question> questions = parseTxtToQuestions(content);

                if (questions.isEmpty()) {
                    return ResponseEntity.badRequest().body("No valid questions found");
                }

                // 檢查緩存
                String cacheKey = generateCacheKey(questions);
                String cachedHtml = cache.getIfPresent(cacheKey);
                if (cachedHtml != null) {
                    return createResponse(cachedHtml);
                }

                // 新試卷
                String examHtml = generateExamHtml(questions);
                cache.put(cacheKey, examHtml);
                return createResponse(examHtml);

            } finally {
                semaphore.release();
            }

        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error reading file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing request: " + e.getMessage());
        }
    }
    private String parseAnswer(String line) {
        if (line.length() < 3) {
            throw new IllegalArgumentException("Invalid line format for answer");
        }
        return line.substring(1, 2);
    }

    private Integer parseQuestionNumber(String line) {
        int dotIndex = line.indexOf(".");
        if (dotIndex == -1) {
            throw new IllegalArgumentException("Question number not found");
        }
        try {
            return Integer.parseInt(line.substring(3, dotIndex).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid question number format");
        }
    }
    private Map<String, String> parseOptions(String line) {
        Map<String, String> options = new LinkedHashMap<>();
        try {
            // 先找到題目結束的位置（第一個真正的選項開始）
            int questionEndIndex = line.indexOf(" (A)", line.indexOf(".")); // 從題號之後開始找
            if (questionEndIndex == -1) {
                throw new IllegalArgumentException("Cannot find options start");
            }

            // 找到出處開始的位置
            int referenceIndex = line.indexOf("(出處");
            if (referenceIndex == -1) {
                throw new IllegalArgumentException("Cannot find reference start");
            }

            // 只解析選項部分
            String optionsText = line.substring(questionEndIndex, referenceIndex);
            String pattern = "\\(([A-Z])\\)([^(]*)";
            Pattern regexPattern = Pattern.compile(pattern);
            Matcher matcher = regexPattern.matcher(optionsText);

            while (matcher.find()) {
                String optionLetter = matcher.group(1);
                String optionText = matcher.group(2).trim();
                if (optionText.length() > 0) {
                    options.put(optionLetter, optionText);
                }
            }

            if (options.isEmpty()) {
                throw new IllegalArgumentException("No valid options found");
            }

            return options;
        } catch (Exception e) {
            System.err.println("Error parsing options in line: " + line);
            throw new IllegalArgumentException("Error parsing options: " + e.getMessage());
        }
    }

    private String parseReference(String line) {
        int referenceStart = line.indexOf("(出處：");
        if (referenceStart == -1) {
            throw new IllegalArgumentException("Reference not found");
        }
        return line.substring(referenceStart + 4, line.length() - 1).trim();
    }
    private List<Question> parseTxtToQuestions(String content) {
        List<Question> questions = new ArrayList<>();
        // 處理換行符號
        content = content.replaceAll("\\r\\n|\\r|\\n", "\n");
        String[] lines = content.split("\n");

        for (String line : lines) {
            try {
                line = line.trim(); // 移除前後空白
                if (line.isEmpty()) continue;

                // 簡化格式檢查
                if (!line.startsWith("(") || !line.endsWith("條)")) {
                    System.err.println("Skipping invalid line format: " + line);
                    continue;
                }

                Question question = new Question();
                question.setCorrectAnswer(parseAnswer(line));
                question.setQuestionNumber(parseQuestionNumber(line));
                question.setQuestionText(parseQuestionText(line));
                question.setOptions(parseOptions(line));
                question.setReference(parseReference(line));

                questions.add(question);

            } catch (Exception e) {
                System.err.println("Error parsing line: " + line);
                System.err.println("Error: " + e.getMessage());
            }
        }
        return questions;
    }
    private String parseQuestionText(String line) {
        try {
            // 找到第一個點的位置
            int dotIndex = line.indexOf(".");
            if (dotIndex == -1) {
                throw new IllegalArgumentException("No dot found in line");
            }

            // 從點號之後開始找第一個選項
            String remainingLine = line.substring(dotIndex + 1);
            int firstOptionIndex = remainingLine.indexOf(" (A)");
            if (firstOptionIndex == -1) {
                throw new IllegalArgumentException("No option (A) found after dot");
            }

            // 題目文字
            String questionText = remainingLine.substring(0, firstOptionIndex).trim();
            System.out.println("Extracted question text: " + questionText);

            return questionText;
        } catch (Exception e) {
            System.err.println("Error in line: " + line);
            throw new IllegalArgumentException("Error parsing question text: " + e.getMessage());
        }
    }

    private String generateCacheKey(List<Question> questions) {
        // 簡化快取
        return questions.stream()
                .map(q -> q.getQuestionNumber().toString())
                .collect(Collectors.joining("-"));
    }

    private ResponseEntity<String> createResponse(String html) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=exam.html")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }


    private String generateExamHtml(List<Question> questions) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<title>考試</title>");
        html.append("<style>");
        html.append("body{padding:20px;font-family:Arial;} ");
        html.append(".question{margin-bottom:20px;border-bottom:1px solid #eee;padding-bottom:10px} ");
        html.append(".result{margin-top:20px;font-weight:bold;} ");
        html.append(".correct{color:green} .wrong{color:red}");
        html.append("</style>");

        // JavaScript
        html.append("<script>");
        html.append("const answers = {");
        for (Question q : questions) {
            html.append(String.format("q%d:'%s',", q.getQuestionNumber(), q.getCorrectAnswer()));
        }
        html.append("};");

        // 檢查答案的函數
        html.append("function checkAnswers() {");
        html.append("  let score = 0;");
        html.append("  let total = Object.keys(answers).length;");
        html.append("  for (let q in answers) {");
        html.append("    let selected = document.querySelector(`input[name='${q}']:checked`);");
        html.append("    if (!selected) continue;");
        html.append("    if (selected.value === answers[q]) score++;");
        html.append("    selected.parentElement.classList.add(");
        html.append("      selected.value === answers[q] ? 'correct' : 'wrong'");
        html.append("    );");
        html.append("  }");
        html.append("  document.getElementById('result').innerHTML = ");
        html.append("    `得分：${score}/${total} (${Math.round(score/total*100)}%)`;");
        html.append("}");
        html.append("</script>");
        html.append("</head><body>");

        // 生成題目
        for (Question q : questions) {
            html.append(String.format("<div class='question'><p>%d. %s</p>",
                    q.getQuestionNumber(), q.getQuestionText()));

            for (Map.Entry<String, String> option : q.getOptions().entrySet()) {
                html.append(String.format("<div><input type='radio' name='q%d' value='%s'> %s. %s</div>",
                        q.getQuestionNumber(), option.getKey(), option.getKey(), option.getValue()));
            }
            html.append("</div>");
        }

        html.append("<button onclick='checkAnswers()'>交答案</button>");
        html.append("<div id='result' class='result'></div>");
        html.append("</body></html>");
        return html.toString();
    }
}