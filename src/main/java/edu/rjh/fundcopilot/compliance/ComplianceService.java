package edu.rjh.fundcopilot.compliance;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ComplianceService {
    public static final String STANDARD_DISCLAIMER = "以上内容仅基于公开数据和历史表现做信息分析，不构成任何投资建议、收益承诺或买卖依据。基金有风险，投资需谨慎。";

    private static final List<String> ADVICE_KEYWORDS = List.of(
            "能买吗", "可以买", "该买", "买入", "卖出", "加仓", "减仓", "调仓", "推荐", "适合买",
            "稳赚", "保本", "收益多少", "未来收益", "will it rise", "should i buy", "buy or sell"
    );

    public ComplianceResult check(String question) {
        if (question == null || question.isBlank()) {
            return new ComplianceResult(false, "问题为空，按普通分析处理。", STANDARD_DISCLAIMER);
        }

        String normalized = question.toLowerCase(Locale.ROOT).replace(" ", "");
        boolean blocked = ADVICE_KEYWORDS.stream().anyMatch(normalized::contains);
        if (blocked) {
            return new ComplianceResult(true, "检测到买卖建议或收益承诺倾向，回答将改写为事实分析和风险提示。", STANDARD_DISCLAIMER);
        }

        return new ComplianceResult(false, "未触发投资建议拦截。", STANDARD_DISCLAIMER);
    }

    public record ComplianceResult(boolean restricted, String message, String disclaimer) {
    }
}
