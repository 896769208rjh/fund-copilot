package fundcopilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("fundcopilot.**.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication
@EnableScheduling
public class FundCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundCopilotApplication.class, args);
    }

}
