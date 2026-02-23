package com.smartquotes.quoteservice.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class LiquibaseConfig {

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        // We explicitly create the bean, forcing Spring to execute it
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setChangeLog("classpath:db/db.changelog-master.yaml");
        liquibase.setDataSource(dataSource);
        return liquibase;
    }
}
