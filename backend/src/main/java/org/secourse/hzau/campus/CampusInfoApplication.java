package org.secourse.hzau.campus;

import org.secourse.hzau.campus.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 「此刻校园」校园信息共享与生活地图系统 —— 后端服务启动类。
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class CampusInfoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusInfoApplication.class, args);
    }
}
