package org.gcrel.tfbackevent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TfBackEventApplication {

    public static void main(String[] args) {
        SpringApplication.run(TfBackEventApplication.class, args);
    }

}
