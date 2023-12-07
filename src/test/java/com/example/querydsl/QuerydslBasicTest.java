package com.example.querydsl;


import com.example.querydsl.domain.Member;
import com.example.querydsl.domain.QMember;
import com.example.querydsl.domain.Team;
import com.example.querydsl.dto.MemberDto;
import com.example.querydsl.dto.QMemberDto;
import com.example.querydsl.dto.UserDto;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;

import static com.example.querydsl.domain.QMember.*;
import static com.example.querydsl.domain.QTeam.*;
import static com.querydsl.jpa.JPAExpressions.*;
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

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
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
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

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

    /**
     * 집합
     * Tuple : querydsl 에서 제공되는 튜플
     * 여러개의 타입을 가지고 있으니 꺼내는 방식을 알아야한다.
     * 데이터 타입이 여러개 들어올 때 사용할 때 이용하지만
     * 실무에서는 거의 사용을 안한다.
     * DTO로 조회하는 방식을 많이 씀
     */
    @Test
    public void aggregation() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();
        //when
        Tuple tuple = result.get(0);

        //then
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }


    /**
     * GroupBy
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
//                .having(item.price.gt(3000)) // having 도 가능 (1000원 넘는 것 조회)
                .fetch();
        //when
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        //then
        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10 + 20) / 2

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); // (30 + 40) / 2

        System.out.println(result);
    }

    /**
     * join 기본
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() throws Exception {
        //given
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        //when

        //then
        assertThat(result)
                // username 속성에
                .extracting("username")
                // member 1,2 가 구성되어 있는지
                .containsExactly("member1", "member2");
    }

    /**
     * theta_join (cross 조인 -> 디비 성능 최적화 별로 다른 쿼리일 수 도 있다.)
     * 연관관계 없이 조인 할 수 있다
     * 회원의 이름이 팀 이름과 같은 회원 조회 (억지성 예제)
     * join 없이 from 에서 처리
     * 제약 : 외부 조인 (left, right) 불가능
     * 하지만 최근 버젼에서 하이버네이트에서 가능하게 해주었다. (on 절을 이용해야함)
     */
    @Test
    public void theta_join() throws Exception {
        //given
        em.persist(new Member("teamA")); // team이름을 member 이름에 넣음
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC")); // teamC라는 이름은 team에 없으니 조회 X


        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        //when

        //then
        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * join on 절
     * 1. 조인 대상 필터링 방법
     * 2. 연관관계 없는 엔티티 외부 조인 (많이 쓰임)
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL : select m, t from Member m left join m.team t on t.name = 'teamA'
     */
    @Test
    public void leftJoin_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();
//        결과 ->
//        tuple = [Member(id=1, username=member1, age=10), Team(id=1, name=teamA)]
//        tuple = [Member(id=2, username=member2, age=20), Team(id=1, name=teamA)]
//        tuple = [Member(id=3, username=member3, age=30), null]
//        tuple = [Member(id=4, username=member4, age=40), null]
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * rightJoin on
     */
    @Test
    public void rightJoin_on_filtering() throws Exception {

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .rightJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();
//        결과 ->
//        tuple = [Member(id=1, username=member1, age=10), Team(id=1, name=teamA)]
//        tuple = [Member(id=2, username=member2, age=20), Team(id=1, name=teamA)]
//        tuple = [null, Team(id=2, name=teamB)]

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * innerJoin on
     * innerJoin == join
     * 설명: 사실상 on은 left, right 조인에서 의미가 있기 때문에 where 을 사용하는 것을 권장
     */
    @Test
    public void innerJoin_on_filteringV1() throws Exception {


        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .innerJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();
//        결과 ->
//        tuple = [Member(id=1, username=member1, age=10), Team(id=1, name=teamA)]
//        tuple = [Member(id=2, username=member2, age=20), Team(id=1, name=teamA)]

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * innerJoin where
     * innerJoin == join
     */
    @Test
    public void innerJoin_where_filteringV1() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .join(member.team, team)
//                .on(team.name.eq("teamA"))
                .where(team.name.eq("teamA"))
                .fetch();
//        결과 ->
//        tuple = [Member(id=1, username=member1, age=10), Team(id=1, name=teamA)]
//        tuple = [Member(id=2, username=member2, age=20), Team(id=1, name=teamA)]

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부조인 사용
     * 세타조인 예제와 같은 경우
     * 회원이 이름이 팀 이름과 같은 대상 외부 조인 해라
     */
    @Test
    public void join_on_no_relation() throws Exception {
        //given
        em.persist(new Member("teamA")); // team이름을 member 이름에 넣음
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC")); // teamC라는 이름은 team에 없으니 조회 X


        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
//                .leftJoin(member.team,team).on(member.username.eq(team.name))
                // 원래는 member.team의 id로 매칭되게 되어있음
                // 현재는 팀의 이름으로 조인 대상이 됨
                .fetch();

        // iter 단축어
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 로딩이 된 엔티티 인지 확인 하기 위한
     */
    @PersistenceUnit
    EntityManagerFactory emf;

    /**
     * fetch join 없을 때
     */
    @Test
    public void fetchJoinNo() throws Exception {
        //given
        em.flush();
        em.clear();

        // 현재 Lazy로딩이 되어있어서 member만 조회함 (쿼리들도)
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        // 패치 조인을 적용을 안했으니 로딩이 되면 안됨
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isFalse();

        //then
    }

    /**
     * fetch join (한방 쿼리 성능 최적화)
     * member를 조회할 때 연관된 엔티티도 한번에 추가할 수 있음
     * 방법 : .join(member.team,team).fetchJoin()
     */
    @Test
    public void fetchJoinUse() throws Exception {
        //given
        em.flush();
        em.clear();

        // 현재 Lazy로딩이 되어있어서 member만 조회함 (쿼리들도)
        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        // 패치 조인을 적용을 안했으니 로딩이 되면 안됨
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isTrue();
    }

    /**
     * 서브 쿼리
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() throws Exception {

        // 서브 쿼리에서는 외부와 내부의 alias가 달라야한다.
        // Q객제를 새로 선언하여 alias 처럼 대체하자
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();
        // member table 에서 조회할 것이다.
        // 조건은 member의 최대 나이와 같은 나이의 member를 조회

        assertThat(result)
                .extracting("age")
                .containsExactly(40);
    }

    /**
     * 서브 쿼리
     * 나이가 평균 이상인 회원
     */
    @Test
    public void subQueryGoe() throws Exception {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        // member table 에서 조회할 것이다.
        // 조건은 member의 최대 나이와 같은 나이의 member를 조회

        assertThat(result)
                .extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 서브 쿼리 in (가장 중요한)
     * 나이가 10살 초과인 맴버 (억지성 예제라 참고용)
     */
    @Test
    public void subQueryIn() throws Exception {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();
        // member table 에서 조회할 것이다.
        // 조건은 member의 최대 나이와 같은 나이의 member를 조회

        assertThat(result)
                .extracting("age")
                .containsExactly(20, 30, 40);
    }


    /**
     * select 서브 쿼리
     * 맴버 이름을 뽑고 평균 나이도 뽑는다.
     * <p>
     * (서브 쿼리 from 절에서는 안됨)
     * JPQL의 한계 (querydsl도 마찬가지)
     * 서브쿼리를 join으로 변경한다. 불가능한 상황도 있다.
     * 애플리케이션에서 쿼리를 2번 분리해서 실행한다.
     * nativeSQL을 사용한다.
     * <p>
     * from 절안에 from 절안에 from 절안에 from 절 ?
     * 쿼리에서만 풀려고 하지는 말자
     * DB에서 데이터를 퍼올리는 용도로만 이용하자 (gruopby 이런거를 일단 잘쓰자)
     */
    @Test
    public void selectSubQuery() throws Exception {

        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();
//        결과 ->
//        tuple = [member1, 25.0]
//        tuple = [member2, 25.0]
//        tuple = [member3, 25.0]
//        tuple = [member4, 25.0]

        // member table 에서 조회할 것이다.
        // 조건은 member의 최대 나이와 같은 나이의 member를 조회
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * case
     */
    @Test
    public void basicCase() throws Exception {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * CaseBuilder
     */
    @Test
    public void complexCase() throws Exception {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0에서 20살")
                        .when(member.age.between(21, 30)).then("21살에서 30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);

        }
    }

    /**
     * 상수 더하기
     */
    @Test
    public void constant() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(member.username,
                        Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 문자 더하기
     * username_age 하는 법
     */
    @Test
    public void concat() throws Exception {

        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue())) // 이 방법은 enum을 처리 할 때 자주 쓰인다
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);

        }
    }

    /**
     * 프로젝션 : select 대상 지정
     * 프로젝션 대상이 하나
     * 대상이 하나면 타입을 명확하게 지정할 수 있음
     * <p>
     * 대상이 둘이상이라면 튜플이나 DTO로 조회
     */
    @Test
    public void simpleProjection() throws Exception {
        //given
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * 튜플로 조회
     * 튜플은 repository 에서만 사용 하는게 좋은 설계 이다.
     * 밖으로 나갈 때는 DTO로 변환해서 나가야한다.
     */
    @Test
    public void tupleProjection() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username" + username);
            System.out.println("age" + age);
        }
    }

    /**
     * JPQL에서의 DTO 조회 방법
     * 생성자 방식만 지원하고
     * 순수 JPA DTO를 조회할 때는 new 명령어를 사용해야함
     */
    @Test
    public void findDtoByJPQL() throws Exception {
        //given
//        em.createQuery("select m from Member m", MemberDto.class); 타입이 안맞아서 안됨
//        em.createQuery("select m.username, m.age from Member m", MemberDto.class); 타입이 안맞아서 안됨

        // 뉴 오퍼레이션 활용
        List<MemberDto> result = em.createQuery(
                "select new com.example.querydsl.dto.MemberDto(m.username, m.age) from Member m",
                MemberDto.class).getResultList();
        //when
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * DTO로 조회 (매우 중요)
     * 프로퍼티 접근 가능
     * 필드 직접 접근 가능
     * 생성자 사용
     */

    /**
     * 프로퍼티 접근 가능 (setter)
     * bean 은 getter setter 그 bean 을 말함
     * querydsl이 만들고 setset 해줘야하는데 기본 생성자가 없으면 안되니까 dto에 기본 생성자를 만들 수 있도록 해야함
     */
    @Test
    public void findDtoBySetter() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(
                        MemberDto.class,
                        member.username,
                        member.age
                ))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 필드 직접 접근 가능 (setter 가 없어도 가능)
     * 기본 생성자 필요
     */
    @Test
    public void findDtoByFiled() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(
                        MemberDto.class,
                        member.username,
                        member.age
                ))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 생성자 사용
     * member 필드와 memberDto의 타입이 동일해야한다.
     */
    @Test
    public void findDtoByConstructor() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(
                        MemberDto.class,
                        member.username,
                        member.age
                ))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * userDto 버젼
     */


    /**
     * 필드 직접 접근 가능 (setter 가 없어도 가능)
     * <p>
     * member 랑 다르게 userDto의 필드의 이름 부분이 username 이 아닌 name이다
     * 필드가 다르다면 어떻게 해야될까?
     * member.username.as("name")으로 필드 명을 맞춘다.
     * <p>
     * ExpressionUtils.as(member.username,"name") 로 해도되지만 지저분 해짐
     * <p>
     * 기본 생성자 필요
     */
    @Test
    public void findUserDto() throws Exception {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
                .select(Projections.fields(
                        UserDto.class,

                        member.username.as("name"),
//                        ExpressionUtils.as(member.username,"name"),

                        // 서브쿼리의 결과를 age라는 alias로 결과를 내게 해줌
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();
        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * userDto 필드명이 다른 Dto일 때
     * 생성자 사용
     * member 필드와 userDto의 타입만 동일해야한다.
     */
    @Test
    public void findUserDtoByConstructor() throws Exception {
        List<UserDto> result = queryFactory
                .select(Projections.constructor(
                        UserDto.class,
                        member.username,
                        member.age
                ))
                .from(member)
                .fetch();
        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * 프로젝션의 궁긍의 어노테이션 하지만 단점 존재
     * Dto의 생성자에 어노테이션을 달아 주자
     * 그 후 querydsl을 다시 컴파일 해줘야한다.
     * Dto 마저 Q파일로 만들어줌
     * <p>
     * new QMemberDto 생성자를 선언하기만 하면 된다
     * <p>
     * constructor 과 차이는 예를 들어서 member.id 를 추가한다고 했을 때
     * QMemberDto는 컴파일에러를 내준다
     * 하지만 constructor는 실행이 되야 오류가 나는 큰 단점
     * <p>
     * 단점 : Q를 만들어줘야한다.
     * 아케텍쳐 의존관계의 문제 -> @QeuryProjection 때문에 Dto 자체가 라이브러리적으로 querydsl 에 의존성을 가지게 된다
     * 갑작스럽게 querydsl을 빼라고 하면 영향이 클 것 이다.
     * dto는 여러 레이어에서 사용하게 되기 때문에 순수하지 않은 dto가 되는 것
     */
    @Test
    public void findDtoByQueryProjection() throws Exception {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 동적 쿼리
     *
     * 1. booleanBuilder 방식
     * 2. where 당중 파라미터 사용
     */

    /**
     * booleanBuilder
     */
    @Test
    public void dynamicQuery_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
//        BooleanBuilder builder = new BooleanBuilder(member.age.eq(ageCond)); // age는 무조건 넘어 와야한다. 이런 설정도 초기에 가능
        BooleanBuilder builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }
        // 둘다 있으면 둘다 빌더에 넣을 거고 age가 없으면 하나만 들어가고 그런 방식으로 진행이 됨
        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /**
     * where 다중 파라미터 사용
     * 실무에서 가장 좋은 방법
     * 코드가 매우 깔끔함
     * <p>
     * 장점: where 조건에 null 값은 무시된다.
     * 메서드를 다른 쿼리에서도 재활용 할 수 있다.
     * 쿼리 자체의 가독성이 높아진다.
     */
    @Test
    public void dynamicQuery_WhereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond))
//                .where(allEq(usernameCond,ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    /**
     * 한방으로 조합 가능 : 조립을 할 수 있는 것이 강점이다.
     */
    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    /**
     * 수정 삭제 배치 쿼리
     * <p>
     * 배치 연산이라고 한다.
     * <p>
     * 모든 개발자 연봉 10퍼센트 인상해. 이런 연산들 처리
     */
    @Test
//    @Commit
    public void bulkUpdate() throws Exception {
        //member1 = 10 -> DB member1
        //member2 = 20 -> DB member2
        //member3 = 30 -> DB member3
        //member4 = 40 -> DB member4


        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();
        //member1 = 10 -> DB 비회원
        //member2 = 20 -> DB 비회원
        //member3 = 30 -> DB member1
        //member4 = 40 -> DB member1


//    영속성 컨텍스트 상태
        //member1 = 10 -> cash member1
        //member2 = 20 -> cash member2
        //member3 = 30 -> cash member3
        //member4 = 40 -> cash member4

        // 디비에서 조회를 해도 버리고 영속성 컨텍스트 내용을 출력하게 된다.
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();
        for (Member member1 : result) {
            System.out.println("result = " + member1);
        }

//        결과 ->
//        result = Member(id=1, username=member1, age=10)
//        result = Member(id=2, username=member2, age=20)
//        result = Member(id=3, username=member3, age=30)
//        result = Member(id=4, username=member4, age=40)

//        해결 방법 ->
        em.flush(); // 영속성 컨테스트 내용을 디비로 보내서 싱크를 맞추고
        em.clear(); // 초기화를 해버린다.
        List<Member> result2 = queryFactory
                .selectFrom(member)
                .fetch();
        for (Member member1 : result2) {
            System.out.println("result2 = " + member1);
        }
    }

    /**
     * bulk 더하기
     */
    @Test
    public void bulkAdd() throws Exception {
        queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }

    /**
     * bulk 곱하기
     */
    @Test
    public void bulkMultiply() throws Exception {
        queryFactory
                .update(member)
                .set(member.age, member.age.multiply(2))
                .execute();
    }

    /**
     * bulk 삭제
     */
    @Test
    public void bulkDelete() throws Exception {
        queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    /**
     * sql function 호출
     */
    @Test
    public void sqlFunction() throws Exception {
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * sql function 소문자
     */
    @Test
    public void sqlFunction2() throws Exception {

        Member member5 = new Member("HELLOJAVA5", 40);
        em.persist(member5);

        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower', {0})", member.username)))
//                .where(member.username.eq(member.username.lower()))
                .where(member.username.lower().eq("hellojava5"))
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
