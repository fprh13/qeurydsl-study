package com.example.querydsl.repository;

import com.example.querydsl.domain.Member;
import com.example.querydsl.domain.Team;
import com.example.querydsl.dto.MemberSearchCondition;
import com.example.querydsl.dto.MemberTeamDto;
import com.example.querydsl.dto.QMemberTeamDto;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.querydsl.domain.QMember.member;
import static com.example.querydsl.domain.QTeam.team;
import static org.springframework.util.StringUtils.hasText;

// 조회 요청이 복잡하고 특화된 쿼리라면 조회용 repository를 따로 구분하여 만드는것이 좋은 설계 방향일 수 있다.
// 너무 모든것을 Custom repository에 담으려고는 하지 말자
@Repository
public class MemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    public MemberQueryRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    /**
     * where 다중 파라미터 사용 - dto 로 반환 받는 방법
     */
    public List<MemberTeamDto> search(MemberSearchCondition condition) {
        return queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")
                ))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .fetch();
    }


    private BooleanExpression usernameEq(String username) {
        return hasText(username) ? member.username.eq(username) : null;
    }

    private BooleanExpression teamNameEq(String teamName) {
        return hasText(teamName) ? team.name.eq(teamName) : null;
    }

    private BooleanExpression ageGoe(Integer ageGoe) { // ? <
        return ageGoe != null ? member.age.goe(ageGoe) : null;
    }

    private BooleanExpression ageLoe(Integer ageLoe) { // > ?
        return ageLoe != null ? member.age.loe(ageLoe) : null;
    }

    // null을 조심한다면 위의 메서드 조합 가능
    private BooleanExpression ageBetween(int ageGoe, int ageLoe) {
        return ageGoe(ageGoe).and(ageLoe(ageLoe));
    }

}