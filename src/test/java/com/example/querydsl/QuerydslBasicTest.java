package com.example.querydsl;


import com.example.querydsl.domain.Member;
import com.example.querydsl.domain.QMember;
import com.example.querydsl.domain.Team;
import com.querydsl.core.QueryResults;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.querydsl.domain.QMember.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    // 필드 단위에서 처리 가능
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {

        // 동시성 문제는 X
        // entityManger는 멀티 쓰레드도 알아서 처리되도록 설계가 되어있음(transaction에 맞게 분배를 해줌)
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1",10,teamA);
        Member member2 = new Member("member2",20,teamA);

        Member member3 = new Member("member3",30,teamB);
        Member member4 = new Member("member4",40,teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    /**
     * JPQL 로 작성하는 법
     * 문제가 있다면 실행을 해야지 발견됨
     */
    @Test
    public void startJPGL() {
        // member1 을 찾아라.
        String qlString = "select m from Member m " +
                "where m.username = :username";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * querydsl
     * 문제가 있디면 애초에 컴파일 시점에서 발견됨
     * (쿼리 확인할 때 엘리어스 명시가 필요할 때 생성자에 넣어서 실행)
     */
    @Test
    public void startQuerydslV1() {

        // 이건 안쓰지만 첫 예제라 사용 (이미 만들어진 것을 사용할 것임)
//        QMember m = new QMember("m");

        // 미리 만들어 진것
        QMember m = member;

        // 파라미터 바인딩을 안해도 자동으로 해준다.
        // 그래서 sql 인젝션으로 안전하다.
        Member findMember = queryFactory
                .select(m)
                .from(m)
                .where(m.username.eq("member1")) // 파라미터 바인딩 처리
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * querydsl
     * 코드 줄이기 (권장 방법)
     */
    @Test
    public void startQuerydsl() {
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * 검색
     * eq : equals 같다면
     */
    @Test
    public void search() throws Exception {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * 검색 - param
     * 이것을 권장 (null이 무시가 되는데 동적 쿼리를 만들 때 아주 좋다)
     */
    @Test
    public void searchAndParam() throws Exception {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10), null
                )
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * 조회 종류
     */
    @Test
    public void resultFetch() throws Exception {
        /**
         * 전체 조회
         */
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        /**
         * 단건 조회
         */
        Member fetchOne = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member2"))
                .fetchOne();

        /**
         * 첫번 째 조회
         */
        Member fetchFirst = queryFactory
                .selectFrom(member)
//                .limit(1),fetchOne() == fetchFirst
                .fetchFirst();

        /**
         *  페이징 중요 (총 쿼리 2번 : 성능 이슈로 토탈 카운트와 컨텐츠와 다를 때가 있다.)
         *  그럴 때는 fetchResults 를 사용하지 말고 쿼리 두번을 따로 날려야한다.
         */
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();
        results.getTotal();
        List<Member> contents = results.getResults();

        /**
         * select에서 count 로 변경
         */
        long total = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /**
     * 정렬
     * 1. 회원 나이 내림차순 (desc)
     * 2. 회원 이름 올림차순 (asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() throws Exception {
        //given
        em.persist(new Member(null,100));
        em.persist(new Member("member5",100));
        em.persist(new Member("member6",100));

        //when
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        //then
        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    /**
     * 페이징 (total 이 필요 없을 때)
     */
    @Test
    public void pagingV1() throws Exception {
        //when
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();
        //then
        assertThat(result.size()).isEqualTo(2);
    }

    /**
     * 페이징 (total 이 필요 할 때)
     * 성능 상 문제가 있을 때 는 count 쿼리와 분리해서 작성하자
     */
    @Test
    public void pagingV2() throws Exception {
        //when
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();
        //then
        System.out.println(results.getResults());
        assertThat(results.getTotal()).isEqualTo(4);
        assertThat(results.getLimit()).isEqualTo(2);
        assertThat(results.getOffset()).isEqualTo(1);
        assertThat(results.getResults().size()).isEqualTo(2);
    }


}
