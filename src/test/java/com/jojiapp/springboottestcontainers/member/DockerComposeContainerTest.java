package com.jojiapp.springboottestcontainers.member;

import org.assertj.core.api.Assertions;
import org.junit.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;

import java.io.*;
import java.time.*;

@SpringBootTest
@Testcontainers
public class DockerComposeContainerTest {

    public static final String MYSQL_DB = "mysqldb";
    public static final int MY_SQL_PORT = 3306;

    @ClassRule
    @Container
    static final DockerComposeContainer<?> dockerComposeContainer =
            new DockerComposeContainer<>(new File("src/test/resources/docker-compose.yml"))
                    .withExposedService(
                            MYSQL_DB,
                            MY_SQL_PORT,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30))
                    );

    @DynamicPropertySource
    public static void overrideProps(DynamicPropertyRegistry dynamicPropertyRegistry) {

        final String host = dockerComposeContainer.getServiceHost(MYSQL_DB, MY_SQL_PORT);
        final Integer port = dockerComposeContainer.getServicePort(MYSQL_DB, MY_SQL_PORT);
        dynamicPropertyRegistry.add("spring.datasource.url",
                () -> "jdbc:mysql://%s:%d/test_container_test".formatted(host, port));
        dynamicPropertyRegistry.add("spring.datasource.username", () -> "root");
        dynamicPropertyRegistry.add("spring.datasource.password", () -> "password");
        dynamicPropertyRegistry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

    }

    @Autowired
    private MemberService memberService;

    @Test
    void 회원_저장() throws Exception {

        Member member = memberService.save();

        Assertions.assertThat(member.getId()).isEqualTo(1);
        Assertions.assertThat(member.getName()).isEqualTo("name");
    }

}
