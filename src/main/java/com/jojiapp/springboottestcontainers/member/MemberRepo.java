package com.jojiapp.springboottestcontainers.member;

import org.springframework.data.jpa.repository.*;

public interface MemberRepo extends JpaRepository<Member, Long> {
}
