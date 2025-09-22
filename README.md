# 스프링 STOMP 채팅 애플리케이션 상세 분석

## 1. 전체 아키텍처 개요

### 프로토콜 스택
```
클라이언트(브라우저) ↔ HTTP/WebSocket ↔ 스프링 부트 서버
                                    ↕
                                  STOMP
                                    ↕
                             메시지 브로커
```

### 메시지 흐름
1. **클라이언트 → 서버**: `/app/*` 경로로 메시지 전송
2. **서버 처리**: `@MessageMapping`이 붙은 메서드에서 처리
3. **서버 → 클라이언트**: `/topic/*` 경로로 브로드캐스트

---

## 2. 핵심 클래스별 상세 분석

### 2.1 ChatApplication.java - 메인 애플리케이션

```java
@SpringBootApplication
public class ChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
```

**역할**:
- 스프링 부트 애플리케이션의 진입점
- `@SpringBootApplication`으로 자동 설정 활성화

**내부 동작**:
- `@EnableAutoConfiguration`: 자동 설정 활성화
- `@ComponentScan`: 컴포넌트 스캔 활성화
- `@Configuration`: 설정 클래스로 지정

---

### 2.2 WebSocketConfig.java - WebSocket 설정

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

#### 2.2.1 어노테이션 분석

**@EnableWebSocketMessageBroker**:
- WebSocket 메시지 처리 활성화
- STOMP 프로토콜 지원 활성화
- 내부적으로 다음 빈들을 자동 등록:
    - `StompSubProtocolHandler`
    - `WebSocketMessageBrokerConfigurationSupport`
    - `SimpMessagingTemplate`

#### 2.2.2 메서드별 상세 분석

**configureMessageBroker()**:
```java
config.enableSimpleBroker("/topic");
```
- **SimpleBroker 활성화**: 메모리 기반 메시지 브로커
- **구독 경로 설정**: 클라이언트가 `/topic/*` 경로로 구독 가능
- **내부 동작**:
    - 구독자 목록을 메모리에 관리
    - 메시지를 모든 구독자에게 브로드캐스트

```java
config.setApplicationDestinationPrefixes("/app");
```
- **애플리케이션 메시지 경로**: 클라이언트가 `/app/*` 경로로 메시지 전송
- **내부 동작**:
    - `/app/*` 경로의 메시지를 `@MessageMapping` 메서드로 라우팅
    - 컨트롤러의 메서드와 매핑

**registerStompEndpoints()**:
```java
registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*")
        .withSockJS();
```
- **엔드포인트 등록**: `/ws` 경로에서 WebSocket 연결 수락
- **CORS 설정**: 모든 도메인에서 접근 허용 (개발용)
- **SockJS 활성화**: WebSocket을 지원하지 않는 브라우저용 폴백

#### 2.2.3 내부 아키텍처

```
클라이언트 WebSocket 연결
        ↓
StompSubProtocolHandler
        ↓
WebSocketMessageBrokerConfigurer
        ↓
SimpleBroker 또는 MessageMapping
```

---

### 2.3 ChatMessage.java - 메시지 모델

```java
public class ChatMessage {
    public enum MessageType { CHAT, JOIN, LEAVE }
    
    private MessageType type;
    private String content;
    private String sender;
    private LocalDateTime timestamp;
}
```

#### 2.3.1 설계 원리

**불변성 고려**:
- 기본 생성자와 setter로 유연성 제공
- `timestamp`는 생성 시점에 자동 설정

**메시지 타입별 용도**:
- `CHAT`: 일반 채팅 메시지
- `JOIN`: 사용자 입장 알림
- `LEAVE`: 사용자 퇴장 알림

#### 2.3.2 JSON 직렬화

**Jackson 자동 변환**:
```json
{
  "type": "CHAT",
  "content": "안녕하세요",
  "sender": "user1",
  "timestamp": "2023-09-22T14:30:00"
}
```

**LocalDateTime 처리**:
- `application.properties`의 `jackson` 설정으로 ISO 8601 형식 사용

---

### 2.4 ChatController.java - 메시지 처리 컨트롤러

```java
@Controller
public class ChatController {
    
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        return chatMessage;
    }
    
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage, 
                              SimpMessageHeaderAccessor headerAccessor) {
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setContent(chatMessage.getSender() + "님이 채팅방에 참여했습니다.");
        return chatMessage;
    }
}
```

#### 2.4.1 어노테이션 상세 분석

**@MessageMapping("/chat.sendMessage")**:
- **경로 매핑**: 클라이언트의 `/app/chat.sendMessage` 요청 처리
- **내부 동작**:
    1. STOMP 프레임 수신
    2. 메시지 바디를 `ChatMessage` 객체로 변환
    3. 메서드 실행
    4. 반환값을 JSON으로 직렬화

**@SendTo("/topic/public")**:
- **브로드캐스트**: 반환값을 `/topic/public` 구독자 모두에게 전송
- **내부 동작**:
    1. 메서드 실행 완료 후 반환값 획득
    2. `SimpMessagingTemplate.convertAndSend()` 호출
    3. 모든 구독자에게 메시지 전송

**@Payload**:
- **메시지 바디 바인딩**: STOMP 메시지의 payload를 객체로 변환
- **자동 검증**: Validation 어노테이션 지원

#### 2.4.2 메서드별 처리 흐름

**sendMessage() 흐름**:
```
1. 클라이언트: stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(message))
2. 스프링: StompSubProtocolHandler가 메시지 수신
3. 라우팅: MessageMappingMessageHandler가 메서드 호출
4. 실행: sendMessage() 메서드 실행
5. 브로드캐스트: @SendTo로 /topic/public 구독자들에게 전송
```

**addUser() 세션 관리**:
```java
headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
```
- **세션 저장**: WebSocket 세션에 사용자명 저장
- **연결 해제 시 활용**: 퇴장 메시지 생성에 사용

---

### 2.5 WebSocketEventListener.java - 이벤트 처리

```java
@Component
public class WebSocketEventListener {
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        logger.info("새로운 WebSocket 연결");
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        
        if (username != null) {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setType(ChatMessage.MessageType.LEAVE);
            chatMessage.setSender(username);
            chatMessage.setContent(username + "님이 채팅방을 나갔습니다.");
            
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
}
```

#### 2.5.1 이벤트 처리 메커니즘

**SessionConnectedEvent**:
- **발생 시점**: WebSocket 연결이 성공적으로 수립된 후
- **내부 동작**:
    1. WebSocket 핸드셰이크 완료
    2. STOMP CONNECT 프레임 처리 완료
    3. 이벤트 발행

**SessionDisconnectEvent**:
- **발생 시점**: WebSocket 연결이 종료될 때
- **트리거 조건**:
    - 클라이언트가 명시적으로 연결 해제
    - 네트워크 문제로 연결 끊김
    - 브라우저 탭 종료

#### 2.5.2 메시지 전송 메커니즘

**SimpMessagingTemplate**:
```java
@Autowired
private SimpMessagingTemplate messagingTemplate;
```
- **역할**: 프로그래밍 방식으로 메시지 전송
- **내부 동작**:
    1. 대상 경로의 구독자 목록 조회
    2. 메시지를 STOMP 프레임으로 변환
    3. 각 구독자의 WebSocket 세션으로 전송

---

## 3. 전체 메시지 흐름 상세 분석

### 3.1 연결 수립 과정

```
1. 클라이언트: new SockJS('http://localhost:8080/ws')
2. HTTP 요청: GET /ws/info (SockJS 메타데이터 조회)
3. WebSocket 업그레이드: HTTP → WebSocket 프로토콜 전환
4. STOMP 핸드셰이크: CONNECT 프레임 전송
5. 서버 응답: CONNECTED 프레임 반환
6. 이벤트 발행: SessionConnectedEvent 발생
```

### 3.2 구독 과정

```
1. 클라이언트: stompClient.subscribe('/topic/public', callback)
2. STOMP SUBSCRIBE 프레임 전송
3. SimpleBroker: 구독자 목록에 세션 추가
4. 서버 응답: RECEIPT 프레임 (선택적)
```

### 3.3 메시지 전송 과정

```
1. 클라이언트: stompClient.send('/app/chat.sendMessage', {}, messageBody)
2. STOMP SEND 프레임 전송
3. StompSubProtocolHandler: 프레임 파싱
4. MessageMappingMessageHandler: @MessageMapping 메서드 라우팅
5. ChatController.sendMessage(): 비즈니스 로직 실행
6. @SendTo 처리: SimpleBroker에 메시지 전달
7. SimpleBroker: 모든 구독자에게 MESSAGE 프레임 전송
8. 클라이언트: 콜백 함수 실행
```

---

## 4. 스프링 내부 컴포넌트 분석

### 4.1 핵심 컴포넌트들

**StompSubProtocolHandler**:
- STOMP 프로토콜 파싱 및 처리
- WebSocket 메시지 ↔ STOMP 프레임 변환

**AbstractBrokerMessageHandler**:
- 메시지 브로커 추상화
- SimpleBroker 또는 StompBrokerRelay 관리

**SimpAnnotationMethodMessageHandler**:
- `@MessageMapping` 어노테이션 처리
- 메서드 파라미터 바인딩 및 반환값 처리

**DefaultSimpUserRegistry**:
- 연결된 사용자 세션 관리
- 사용자별 메시지 전송 지원

### 4.2 메시지 변환 과정

```
JSON String (클라이언트)
        ↓
STOMP Message Frame
        ↓
Spring Message<T>
        ↓
Java Object (@Payload)
        ↓
메서드 실행
        ↓
반환값 (Java Object)
        ↓
JSON String
        ↓
STOMP Message Frame
        ↓
WebSocket Frame (클라이언트)
```

---

## 5. 성능 및 확장성 고려사항

### 5.1 메모리 관리

**SimpleBroker 한계**:
- 모든 구독 정보를 메모리에 저장
- 서버 재시작 시 모든 연결 정보 손실
- 단일 서버에서만 동작

**해결방안**:
```java
// Redis 기반 브로커 사용
@Configuration
public class RedisStompConfig {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableStompBrokerRelay("/topic")
              .setRelayHost("localhost")
              .setRelayPort(61613);
    }
}
```

### 5.2 세션 관리

**현재 구조의 한계**:
- 세션 정보가 각 서버 인스턴스에 분산 저장
- 로드밸런서 사용 시 sticky session 필요

**개선 방안**:
- Redis를 이용한 세션 클러스터링
- 데이터베이스 기반 사용자 상태 관리

---

## 6. 보안 고려사항

### 6.1 현재 구조의 보안 이슈

1. **인증 없음**: 누구나 접근 가능
2. **입력 검증 없음**: 악성 스크립트 삽입 가능
3. **메시지 크기 제한 없음**: DoS 공격 가능

### 6.2 보안 강화 방안

```java
// Spring Security 통합
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {
    
    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        messages
            .simpDestMatchers("/app/**").authenticated()
            .simpSubscribeDestMatchers("/topic/**").authenticated();
    }
}
```

이러한 상세한 분석을 통해 스프링 STOMP 채팅 애플리케이션의 동작 원리를 완전히 이해할 수 있습니다.