package fundcopilot.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AgentTaskRecoveryRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentTaskRecoveryRunner.class);

    private final AgentTaskExecutionService agentTaskExecutionService;

    public AgentTaskRecoveryRunner(AgentTaskExecutionService agentTaskExecutionService) {
        this.agentTaskExecutionService = agentTaskExecutionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("Scanning unfinished fund analysis tasks for recovery");
        agentTaskExecutionService.recoverUnfinishedTasks();
    }
}
