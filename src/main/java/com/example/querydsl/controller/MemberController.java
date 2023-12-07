package com.example.querydsl.controller;

import com.example.querydsl.dto.MemberSearchCondition;
import com.example.querydsl.dto.MemberTeamDto;
import com.example.querydsl.repository.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberController {
    private final MemberJpaRepository memberJpaRepository;

    /**
     * http://127.0.0.1:8080/v1/members?teamName=teamB
     * http://127.0.0.1:8080/v1/members?teamName=teamB&ageGoe=31
     * http://127.0.0.1:8080/v1/members?teamName=teamB&ageGoe=31&ageLoe=35
     * http://127.0.0.1:8080/v1/members?teamName=teamB&ageGoe=31&ageLoe=35&username=member31
     * 동적 쿼리 요청 예시
     */
    @GetMapping("/v1/members")
    public List<MemberTeamDto> searchMemberV1(MemberSearchCondition condition) {
        return memberJpaRepository.search(condition);
    }
}
