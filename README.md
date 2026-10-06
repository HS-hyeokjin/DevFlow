# DevFlow

AI 개발 에이전트를 직접 만들면서 **MSA + Kafka + AI Agent**를 학습하는 프로젝트입니다.

## v0.1

첫 버전의 목표는 아주 단순합니다.

```text
POST /tasks
    |
    v
Task Service :8080
    |
    | DEV_TASK_CREATED
    v
Apache Kafka :9092
    |
    v
Code Agent :8081
    |
    +--> 작업 수신 로그 출력
```

사용자가 개발 작업을 등록하면 Task Service가 Kafka의 `dev-task-created` 토픽에 이벤트를 발행하고,
Code Agent가 Consumer Group으로 이벤트를 받아 출력합니다.

## Tech Stack

- Java 21
- Spring Boot 4.1.1
- Spring for Apache Kafka
- Apache Kafka 4.3.1 (KRaft)
- Maven
- Docker Compose
- GitHub Actions

## Project Structure

```text
DevFlow
├─ task-service
│  └─ 작업 생성 API + Kafka Producer
├─ code-agent
│  └─ Kafka Consumer
├─ docker-compose.yml
├─ requests.http
└─ pom.xml
```

## Run

Maven을 PC에 별도로 설치할 필요가 없습니다. 프로젝트에 포함된 Maven Wrapper가 Maven 3.9.11을 자동으로 내려받아 사용합니다.

버전 확인:

```powershell
.\mvnw.cmd -version
```


### 1. Kafka

```bash
docker compose up -d
docker compose ps
```

### 2. Task Service

```bash
.\mvnw.cmd -pl task-service spring-boot:run
```

### 3. Code Agent

새 터미널에서:

```bash
.\mvnw.cmd -pl code-agent spring-boot:run
```

### 4. Create Task

PowerShell:

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/tasks" `
  -ContentType "application/json" `
  -Body '{"repository":"HS-hyeokjin/DevFlow","prompt":"프로젝트 분석해줘"}'
```

curl:

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"repository":"HS-hyeokjin/DevFlow","prompt":"프로젝트 분석해줘"}'
```

응답:

```json
{
  "taskId": "d65c57b5-d651-4b61-8225-5297297f89ef",
  "status": "QUEUED"
}
```

Code Agent:

```text
============================================================
[TASK RECEIVED]
Task ID    : d65c57b5-d651-4b61-8225-5297297f89ef
Repository : HS-hyeokjin/DevFlow
Prompt     : 프로젝트 분석해줘
Created At : 2026-10-06T04:00:00Z
============================================================
```

## Kafka Event

Topic: `dev-task-created`

```json
{
  "eventType": "DEV_TASK_CREATED",
  "taskId": "d65c57b5-d651-4b61-8225-5297297f89ef",
  "repository": "HS-hyeokjin/DevFlow",
  "prompt": "프로젝트 분석해줘",
  "createdAt": "2026-10-06T04:00:00Z"
}
```

## Roadmap

- **v0.1** Task Service → Kafka → Code Agent
- **v0.2** Code Agent가 GitHub 저장소 clone
- **v0.3** LLM으로 프로젝트 구조/코드 분석
- **v0.4** 코드 자동 수정 + 작업 브랜치 생성
- **v0.5** Test Agent 추가
- **v0.6** 테스트 실패 → Code Agent 자동 재수정
- **v0.7** Review Agent + GitHub PR
- **v0.8** 전체 서비스 Docker Compose
- **v0.9** Retry / DLT / Transactional Outbox / Idempotency
- **v1.0** 사람이 승인하는 실제 AI 개발 Agent Workflow

## v0.1에서 공부할 것

1. 왜 서비스를 Task Service와 Code Agent로 분리하는가
2. Kafka Producer와 Consumer는 어떻게 통신하는가
3. Topic과 Consumer Group은 무엇인가
4. HTTP 요청과 비동기 이벤트 처리는 어떤 차이가 있는가
5. Code Agent를 여러 개 띄우면 Consumer Group이 어떻게 작업을 분배하는가
