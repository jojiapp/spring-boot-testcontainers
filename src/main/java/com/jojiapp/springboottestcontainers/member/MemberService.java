package com.jojiapp.springboottestcontainers.member;


import lombok.*;
import org.springframework.stereotype.*;

import javax.transaction.*;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepo memberRepo;

    public Member save() {

        return memberRepo.save(
                new Member(null, "name")
        );
    }
}
