package fundcopilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "fund-copilot.agent.enable-llm=false")
class FundCopilotApplicationTests {

    @Test
    void contextLoads() {
    }

}
