package com.jojiapp.springboottestcontainers.member;

import org.assertj.core.api.Assertions;
import org.junit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;


@SpringBootTest
@Testcontainers
public class MySQLContainerTest {

    private static final String USERNAME = "root";
    private static final String PASSWORD = "password";
    private static final String DATABASE_NAME = "mysql_testcontainer";

    @ClassRule
    @Container
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.0")
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    public static void overrideProps(DynamicPropertyRegistry dynamicPropertyRegistry) {

        dynamicPropertyRegistry.add("spring.datasource.url", () -> mySQLContainer.getJdbcUrl());
        dynamicPropertyRegistry.add("spring.datasource.username", () -> USERNAME);
        dynamicPropertyRegistry.add("spring.datasource.password", () -> PASSWORD);
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
