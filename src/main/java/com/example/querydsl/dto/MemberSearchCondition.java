package com.example.querydsl.dto;

import lombok.Data;

@Data
public class MemberSearchCondition {
    // 회원명, 팀명, 나이
    private String username;
    private String teamName;
    private Integer ageGoe; // 값이 null 일 수 도 있어서 int 말고 Integer 로 처리
    private Integer ageLoe;
}
