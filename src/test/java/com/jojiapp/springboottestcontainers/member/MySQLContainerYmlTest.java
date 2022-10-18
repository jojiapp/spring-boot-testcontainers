package com.jojiapp.springboottestcontainers.member;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class MySQLContainerYmlTest {

    @Autowired
    private MemberService memberService;

    @Test
    void 회원_저장() throws Exception {

        Member member = memberService.save();

        Assertions.assertThat(member.getId()).isEqualTo(1);
        Assertions.assertThat(member.getName()).isEqualTo("name");
    }

}
