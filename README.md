# [Junit] Spring Boot에서 Testcontainer 사용

최근들어 테스트의 중요성을 느끼고 테스트를 열심히 작성하고 있습니다.

그런데 테스트를 하다보니 운영환경 DB는 MySQL인데 테스트용으로는 H2 인메모리 DB를 사용한다는 것이 찝찝했습니다.

물론 대부분의 기능은 문제 없이 테스트가 되었으나 `날짜 함수` 처럼 특정 DBMS에 종속적인 함수나
`트랜잭션 isolation`같은 부분에서는 DBMS마다 차이가 있기에 이런 부분이 문제로 다가왔을 때 멘붕에 빠졌었습니다.

이를 해결하기 위해선 운영에서 사용하는 DB에 테스트용 스키마 생성하여 사용하는 것이였습니다.

운영 서버의 DB를 같이 쓴다는거 자체가 찝찝할 뿐더러 로컬에서는 또 로컬용으로 만들어야 하니 여간 귀찮은게 아니였습니다.

그러다 최근에 `Testcontainer`라는 것을 알게 되었습니다.

컨셉은 간단합니다 Docker 컨테이너로 DB를 띄워 독립적인 환경에서 테스트를 하고 테스트가 끝나면 컨테이너를 종료하는 것입니다.

이제 Testcontainer를 적용하기 위해 테스트 하면서 겪었던 부분들을 작성해 보겠습니다.

> 여기서는 MySQL을 기준으로 할 것이지만 내용을 다 읽고 다른 DB로 바꾸는 것은 문제가 없을 것이라 생각합니다.

## 의존성 받기

우선 Testcontainer를 사용하기 위해서는 `gradle`에 의존성을 주입받아야 합니다.

```groovy
ext {
    set('testcontainersVersion', "1.17.4")
}

dependencies {
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:mysql'
}

dependencyManagement {
    imports {
        mavenBom "org.testcontainers:testcontainers-bom:${testcontainersVersion}"
    }
}
```

`MySQL` 전용 의존성도 추가로 받아 줍니다.

```groovy
testImplementation 'org.testcontainers:mysql'
```

## MySQL Container 사용

```JAVA

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}
```

```java

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
```

```java
public interface MemberRepo extends JpaRepository<Member, Long> {
}
```

우선 테스트 하기 앞서 간단하게 DB에 값을 넣을 수 있도록 간단하게 작성하였습니다.

### MySQL Container - 첫 번째 방법

```java

@SpringBootTest
@Testcontainers
class MemberServiceTest {

    private static final String USERNAME = "root";
    private static final String PASSWORD = "password";
    private static final String DATABASE_NAME = "mysql_testcontainer";

    @ClassRule
    @Container
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.0")
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    public static void overrideProps(DynamicPropertyRegistry dynamicPropertyRegistry) {

        dynamicPropertyRegistry.add("spring.datasource.url", () -> mySQLContainer.getJdbcUrl());
        dynamicPropertyRegistry.add("spring.datasource.username", () -> USERNAME);
        dynamicPropertyRegistry.add("spring.datasource.password", () -> PASSWORD);
        dynamicPropertyRegistry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

    }

    @Autowired
    private MemberService memberService;

    @Test
    void 회원_저장() throws Exception {

        Member member = memberService.save();

        Assertions.assertThat(member.getId()).isEqualTo(1);
        Assertions.assertThat(member.getName()).isEqualTo("name");
    }

}
```

`MySQLContainer`에 MySQL 이미지를 넣은 후 계정, 비밀번호, DB이름 등을 설정합니다.

> 이때, 컨테이너를 `static`으로 생성하여야 매 테스트 마다 컨테이너가 새로 시작하지 않습니다.
>
> 추후, 추상화 클래스로 올리는 등의 작업으로 다른 클래스에서 사용하여도 컨테이너는 한 번만 실행 되도록 할 수 있습니다.

이후 스프링에서 `Datasource`를 생성할 수 있도록 `@DynamicPropertySource`를 통해 동적으로 `properties`값을 설정하여 줍니다.

원래라면 아래 두 가지도 추가해주어야 합니다.

- `@BeforeAll`을 사용하여 `mySQLContainer.start()`메소드를 실행하여 컨테이너 시작
- `@AfterAll`을 사용하여 `mySQLContainer.stop()` 메소드를 실행하여 컨테이너 종료

이런 기능을 대신 해주는 것이 `@Testcontainers`와 `@Container`입니다.

- `@Testcontainers`: start(), stop()를 실행
- `@Container`: 해당 객체가 테스트 컨테이너 임을 선언

### MySQL Container - 두 번째 방법

Spring Boot는 properties 혹은 yml 파일을 읽어서 자동으로 설정하여 객체를 생성해 줍니다.

Testcontainer의 경우에도 설정만 해주면 주입이 됩니다.

```yml
spring:
  datasource:
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
    url: jdbc:tc:mysql:8.0:///test_container_test
    username: root
    password: password

  jpa:
    hibernate:
      ddl-auto: create
    show-sql: true
```

- driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver 로 사용하여야 함
- url: `jdbc:tc:<도커이미지>://[host]/<database name>`
  - host는 생략이 가능하며 생략 시 포트 번호는 랜덤하게 잡힘
  - tc를 붙여줘야 이렇게 랜덤으로 생성된 포트번호와 연결이 가능

> 이 방법은 여러 테스트가 실행되도 자동으로 컨테이너가 한 번만 실행됩니다.

> 두 번째 방법을 사용하기를 권장하며 사용하지 말라고 해도 첫 번째 방법 보다 훨씬 간단해서 사용할 것 같습니다.

> 첫 번째 방법과 두 번째 방법이 공존하면 두 번째 방법이 설정을 오버라이드 하는 것으로 아는데 저는 컨테이너가 2개가 떴습니다.
> 물론 사용하는 컨테이너는 두 번째 방법이 사용되었으나 찝찝했습니다.

## DockerComposeContainer 사용

저는 어차피 배포를 하면 `docker-compose`를 사용할 것이라 이 또한 동일하게 가져가는 것이 따로 설정을 다시 다 해주는것 보다 편리할 것 같다는 생각이 들었습니다

> 추가로 현재 `Docker`와 `docker-compose`를 공부하고 있는 것도 있고 위에서 컨테이너가 두 개가 뜨는 현상 또한 마음에 들지 않았습니다.

- 실제 사용하는 docker-compose

```yml
version: '3'

services:
  app:
    build:
      context: ./
      dockerfile: Dockerfile-server
    #    restart: always
    ports:
      - '8080:8080'
    container_name: server
    depends_on:
      - mysqldb

  mysqldb:
    image: 'mysql:8.0'
    #    restart: always
    environment:
      - MYSQL_ROOT_PASSWORD=password
      - MYSQL_DATABASE=docker_test
    volumes:
      - ./mysqldata:/var/lib/mysql
    ports:
      - '3306:3306'
    container_name: db
```

- 테스트용 docker-compose

```yml
version: '3'

services:
  mysqldb:
    image: 'mysql:8.0'
    #   restart: always
    environment:
      - MYSQL_ROOT_PASSWORD=password
      - MYSQL_DATABASE=test_container_test
    ports:
      - '3306'
```

> 위에 작성된 `docker-compose`는 공부하며 작성한 것입니다.
>
> 단지, 실제 사용될 `docker-compose`와 테스트에서 사용될 `docker-compose`의 설정이 약간은 다르다는 것을 보여주기 위함입니다.

거의 동일하나 중요한것은 테스트용 docker-compose의 경우 port부분에 외부 포트와 연결하지 않습니다.
이렇게 작성 시 외부 포트는 자동으로 사용되지 않은 포트 중 하나로 랜덤하게 설정됩니다.

이외 `volumes`, `container_name`은 불필요하기 때문에 제거 하였습니다.

해당 `docker-compose.yml`파일은 `src/test/resources`에 넣어 두었습니다.

```java

@SpringBootTest
@Testcontainers
class MemberServiceTest {

    public static final String MYSQL_DB = "mysqldb";
    public static final int MY_SQL_PORT = 3306;

    @ClassRule
    @Container
    static final DockerComposeContainer<?> dockerComposeContainer =
            new DockerComposeContainer<>(new File("src/test/resources/docker-compose.yml"))
                    .withExposedService(
                            MYSQL_DB,
                            MY_SQL_PORT,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30))
                    );

    @DynamicPropertySource
    public static void overrideProps(DynamicPropertyRegistry dynamicPropertyRegistry) {

        final String host = dockerComposeContainer.getServiceHost(MYSQL_DB, MY_SQL_PORT);
        final Integer port = dockerComposeContainer.getServicePort(MYSQL_DB, MY_SQL_PORT);
        dynamicPropertyRegistry.add("spring.datasource.url",
                () -> "jdbc:mysql://%s:%d/test_container_test".formatted(host, port));
        dynamicPropertyRegistry.add("spring.datasource.username", () -> "root");
        dynamicPropertyRegistry.add("spring.datasource.password", () -> "password");
        dynamicPropertyRegistry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

    }

    @Autowired
    private MemberService memberService;

    @Test
    void 회원_저장() throws Exception {

        Member member = memberService.save();

        Assertions.assertThat(member.getId()).isEqualTo(1);
        Assertions.assertThat(member.getName()).isEqualTo("name");
    }

}
```

### STEP. 1

```java
@ClassRule
static final DockerComposeContainer dockerComposeContainer =
        new DockerComposeContainer(new File("src/test/resources/docker-compose.yml"))
                .withExposedService(
                        MYSQL_DB,
                        MY_SQL_PORT,
                        Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30))
        );
```

방금 작성한 `docker-compose.yml`파일을 파라미터로 넘겨 줍니다.

여기서 외부로 노출할 서비스와 포트 번호를 추가로 입력해 줍니다.

`Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30))`: 컨테이너가 뜨기도 전에 테스트가 실행되지 않게 하기 위해 대기시간을 걸어줍니다.

### STEP. 2

```java
@DynamicPropertySource
public static void overrideProps(DynamicPropertyRegistry dynamicPropertyRegistry){

final String host=dockerComposeContainer.getServiceHost(MYSQL_DB,MY_SQL_PORT);
final Integer port=dockerComposeContainer.getServicePort(MYSQL_DB,MY_SQL_PORT);
        dynamicPropertyRegistry.add("spring.datasource.url",
        ()->"jdbc:mysql://%s:%d/test_container_test".formatted(host,port));
        dynamicPropertyRegistry.add("spring.datasource.username",()->"root");
        dynamicPropertyRegistry.add("spring.datasource.password",()->"password");
        dynamicPropertyRegistry.add("spring.jpa.hibernate.ddl-auto",()->"create");
        }
```

이제 `MySQLContainer`의 첫 번째 방법 처럼 동적 할당을 해야 합니다.

여기서 우리는 `host`와 `port`를 알면 `url`를 만들 수 있습니다.

- dockerComposeContainer.getServiceHost(): host 값 조회
- dockerComposeContainer.getServicePort(): port 값 조회

해당 값을 이용하여 `jdbc url`을 생성합니다.

> 이건 현재 로컬에 잠시 뜬 컨테이너에 진짜 접근하는 것이기 때문에 `tc`가 붙은게 아닌 `진짜 url 경로`를 작성해야 합니다.

이제 우리는 `docker-compose`를 이용하여 `Testcontainer`를 띄웠기 때문에 `MySQLContainer 의존성을 제거`해도 됩니다.

```groovy
testImplementation 'org.testcontainers:mysql' // 제거
```

## 마치며

이 외 `GenericContainer`를 이용하여 컨테이너를 생성하는 방법도 존재하지만 저는 `docker-compose` 방식을 자주 사용할 것 같아 따로 공부해보지는 않았습니다.

컨테이너를 띄워야 하기 때문에 생각한 것 이상으로 시간이 조금 오래걸리지만 저는 최대한 운영과 동일한 환경에서 테스트를 하는 것이 좋다고 생각합니다.

로컬에서는 `Tag`를 이용하여 테스트에 제외하고 `CI/CD`작업을 할 때만 실행 하도록 하는 등의 방법으로 조금이나마 테스트 시간을 절약할 수 있을 것 같습니다.

## 참고 사이트

- [https://www.testcontainers.org/](https://www.testcontainers.org/)
- [[인프런] 더 자바, 애플리케이션을 테스트하는 다양한 방법 - 백기선](https://www.inflearn.com/course/the-java-application-test)
- [testContainer를 사용하여 SpringBoot, JPA, MySQL환경 테스트 진행](https://honeyinfo7.tistory.com/m/305)
- [[Spring] TestContainers로 멱등성있는 MySql 테스트 환경 구축하기](https://loosie.tistory.com/m/793)
