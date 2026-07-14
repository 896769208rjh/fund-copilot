package fundcopilot.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStructuredOutputsTest {
    @Test
    void stageNarrativeShouldRequireEveryStructuredField() {
        AgentStructuredOutputs.StageNarrative validOutput = new AgentStructuredOutputs.StageNarrative(
                "历史表现存在波动。",
                List.of("近一年收益为正", "最大回撤需要关注"),
                "历史数据不能推导未来收益。"
        );
        AgentStructuredOutputs.StageNarrative invalidOutput = new AgentStructuredOutputs.StageNarrative(
                "历史表现存在波动。",
                List.of(),
                ""
        );

        assertThat(validOutput.isValid()).isTrue();
        assertThat(validOutput.render()).contains("关键观察", "风险边界");
        assertThat(invalidOutput.isValid()).isFalse();
    }

    @Test
    void finalAnswerShouldRejectMismatchedFundCode() {
        AgentStructuredOutputs.FinalAnswer output = new AgentStructuredOutputs.FinalAnswer(
                "000001",
                "2026-07-14",
                "已完成公开数据分析。",
                List.of("近一年收益仅代表历史表现"),
                List.of("存在净值波动风险"),
                "不构成投资建议。",
                "基金有风险，投资需谨慎。"
        );

        assertThat(output.isValid("000001")).isTrue();
        assertThat(output.isValid("110022")).isFalse();
        assertThat(output.render()).contains("基金代码：000001", "免责声明");
    }
}
