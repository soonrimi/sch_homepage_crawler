package whatisMGC;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * 하드코딩된 게시판 환경 변수 및 설정값들을 board_config.json에서 불러와
 * 애플리케이션 전역에서 사용할 수 있게 제공하는 싱글톤 객체입니다.
 */
public class BoardConfig {
    
    private static BoardConfig instance;

    public List<String> centerNames;
    public List<String> announceNames;
    public List<String> excludedPages;
    public Map<String, String> customSelectors;
    public List<SubpageUrlOverride> subpageUrlOverrides;
    public List<PostUrlFallback> postUrlFallbacks;
    public List<CategoryRule> categoryRules;

    // --- Null 안전 보장을 위한 기본 생성자 처리 ---
    public BoardConfig() {
        centerNames = Collections.emptyList();
        announceNames = Collections.emptyList();
        excludedPages = Collections.emptyList();
        customSelectors = Collections.emptyMap();
        subpageUrlOverrides = Collections.emptyList();
        postUrlFallbacks = Collections.emptyList();
        categoryRules = Collections.emptyList();
    }

    public static class SubpageUrlOverride {
        public String keyword;
        public String url;
    }

    public static class PostUrlFallback {
        public String urlContains;
        public String department;
        public String category; // Maps to Category enum name
    }

    public static class CategoryRule {
        public String matchType;
        public List<String> titleIncludes;
        public String category; // Optional (used if matchType is TITLE_SIMPLE)
        public List<SubRule> subRules; // Optional (used if matchType is DEPARTMENT_COMPOUND)
    }

    public static class SubRule {
        public List<String> boardNameIncludes;
        public List<String> postContentIncludes;
        public String category;
    }

    /**
     * board_config.json 파일을 읽어서 BoardConfig 객체로 변환하여 리턴합니다.
     */
    public static BoardConfig getInstance() {
        if (instance == null) {
            try {
                Reader reader = new InputStreamReader(
                    BoardConfig.class.getResourceAsStream("/board_config.json"), 
                    StandardCharsets.UTF_8
                );
                Gson gson = new Gson();
                instance = gson.fromJson(reader, BoardConfig.class);
            } catch (Exception e) {
                System.err.println("경고: 설정 파일(board_config.json) 로드 실패: " + e.getMessage());
                instance = new BoardConfig(); // Fallback to empty context
            }
        }
        return instance;
    }
}
