package com.example.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class MemberDto {

    private String username;

    private int age;

    public MemberDto() {
        log.info("기본 생성자 호출 입니다.");
    }

    @QueryProjection
    public MemberDto(String username, int age) {
        log.info("생성자를 통한 방식으로 진행 되었습니다.");
        this.username = username;
        this.age = age;
    }
}
