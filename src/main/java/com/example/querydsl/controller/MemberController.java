package com.example.querydsl.controller;

import com.example.querydsl.dto.MemberSearchCondition;
import com.example.querydsl.dto.MemberTeamDto;
import com.example.querydsl.repository.MemberJpaRepository;
import com.example.querydsl.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberController {
    private final MemberJpaRepository memberJpaRepository;
    private final MemberRepository memberRepository;

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

    @GetMapping("/v2/members")
    public Page<MemberTeamDto> searchMemberV2(MemberSearchCondition condition, Pageable pageable) {
        return memberRepository.searchPageSimple(condition, pageable);
    }

    /**
     * http://127.0.0.1:8080/v3/members?page=0&size=110
     *
     * sort : sort 정렬은 조금만 복잡해져도 동작을 안할 가능성이 있다.
     * sort 보나느 파라미터를 직접 받아서 orderby조건에 넣어서 처리 하자.
     */
    @GetMapping("/v3/members")
    public Page<MemberTeamDto> searchMemberV3(MemberSearchCondition condition, Pageable pageable) {
        return memberRepository.searchPageComplex(condition, pageable);
    }
}
