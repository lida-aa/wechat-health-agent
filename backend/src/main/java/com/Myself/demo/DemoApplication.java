package com.Myself.demo;

import com.Myself.demo.bot.WeChatBotService;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Slf4j
@EnableScheduling
@SpringBootApplication
@MapperScan("com.Myself.demo.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Component
    static class AppRunner implements CommandLineRunner {

        private final WeChatBotService weChatBotService;

        AppRunner(WeChatBotService weChatBotService) {
            this.weChatBotService = weChatBotService;
        }

        @Override
        public void run(String... args) {
            log.info("应用启动完成，启动微信健康管理 Agent");
            weChatBotService.start();
            weChatBotService.startBot("bot2");
        }
    }
}
