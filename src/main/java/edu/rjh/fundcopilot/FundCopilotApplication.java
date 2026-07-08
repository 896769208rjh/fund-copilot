package edu.rjh.fundcopilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("edu.rjh.fundcopilot.**.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication
public class FundCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundCopilotApplication.class, args);
    }

}
