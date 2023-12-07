package com.example.querydsl.repository;

import com.example.querydsl.domain.Member;
import com.example.querydsl.dto.MemberSearchCondition;
import com.example.querydsl.dto.MemberTeamDto;
import com.example.querydsl.dto.QMemberTeamDto;
import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

import static com.example.querydsl.domain.QMember.member;
import static com.example.querydsl.domain.QTeam.team;
import static org.springframework.util.StringUtils.hasText;

// 이름은 꼭 기존 인터페이스 이름 뒤에 Impl 을 붙혀야됨 규칙임.
public class MemberRepositoryImpl implements MemberRepositoryCustom{

    private final JPAQueryFactory queryFactory;

    public MemberRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }


    /**
     * where 다중 파라미터 사용 - dto 로 반환 받는 방법
     */
    @Override
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

    @Override
    public Page<MemberTeamDto> searchPageSimple(MemberSearchCondition condition, Pageable pageable) {
        QueryResults<MemberTeamDto> result = queryFactory
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
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetchResults();// 컨텐츠용 쿼리도 날리고 토탈 카운트 쿼리도 날린다.
        // 토탈카운트 쿼리에서는 orderBy 같은 조건은 지우니까 걱정 안해도됨

        List<MemberTeamDto> content = result.getResults();
        long total = result.getTotal();
        return new PageImpl<>(content, pageable, total);

    }

    /**
     * 콘텐츠랑 토탈을 따로 쿼리를 구분해서 날린다.
     *
     * 장점: 최적화가 된다.
     * 이유 : .fetchResults()의 토탈 카운트에서 join이 붙어서 날라간다.
     * 그렇기 때문에 조인이 굳이 없이 토탈 카운트 쿼리가 간단하게 이행되는거라면 따로 분리해서 작성하자.
     * 데이터가 몇천만건이라면 성능이 최적화가 잘될 것이다.
     *
     *  //==refactoring -> command + option + m==//
     */
    @Override
    public Page<MemberTeamDto> searchPageComplexV1(MemberSearchCondition condition, Pageable pageable) {
        List<MemberTeamDto> content = queryFactory
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
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();// fetch라 그냥 content만 날린다.

        long total = queryFactory
                .select(member)
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .fetchCount();

        return new PageImpl<>(content, pageable, total);
    }


    /**
     * count쿼리 최적화 ->
     * 만약에 페이지 시작이면서 컨텐츠 사이즈가 페이지 사이즈보다 작을 때
     * 마지막 페이지 일 때 (offset + 컨텐츠 사이즈를 더해서 전체 사이즈 구함)
     * 마지막에 fetchCount 를 하지말고 인자로 넘겨주자
     * PageableExecutionUtils.getPage에서 위의 조건에 맞춰서 함수를 호출 할지 말지 결정함
     */
    @Override
    public Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable) {
            List<MemberTeamDto> content = queryFactory
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
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .fetch();// fetch라 그냥 content만 날린다.

        JPAQuery<Member> countQuery = queryFactory
                .select(member)
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                );

//        return PageableExecutionUtils.getPage(content, pageable, () -> countQuery.fetchCount());
        //==메서드 레퍼런스==/
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchCount);
    }
}
