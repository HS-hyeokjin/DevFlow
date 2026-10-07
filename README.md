# DevFlow

AI 개발 에이전트를 직접 만들면서 **MSA + Kafka + AI Agent**를 학습하는 프로젝트입니다.

## v0.5 - Test Agent

v0.5부터 Code Agent와 테스트 실행을 분리합니다.

```text
사용자
  ↓
Task Service
  ↓
Kafka: dev-task-created
  ↓
Code Agent
  ├─ Git Clone
  ├─ Repo Map
  ├─ 관련 파일 선택
  ├─ Mock/OpenAI 분석
  ├─ 코드 수정
  └─ CODE_MODIFIED 발행
            ↓
      Kafka: code-modified
            ↓
        Test Agent
          ├─ workspace 확인
          ├─ Maven/Gradle 명령 결정
          ├─ 실제 빌드/테스트 실행
          ├─ test-command.log 저장
          ├─ test-result.md 저장
          └─ TEST_PASSED / TEST_FAILED 발행
                    ↓
              Kafka: test-result
```

## 왜 Test Agent를 분리했나?

코드 생성과 테스트는 실행 특성이 다릅니다.

- Code Agent: Git/AI/파일 수정 중심
- Test Agent: CPU/프로세스 실행/빌드 도구 중심
- 긴 테스트가 Code Agent의 다음 작업을 막지 않음
- 테스트 Worker만 여러 개로 확장 가능
- 실패 결과를 Kafka로 다시 보내 v0.6 자동 재수정 루프에 활용 가능

여기부터 Kafka의 장점이 더 분명해집니다.

## CODE_MODIFIED 이벤트

Code Agent가 수정 완료 후 다음 정보를 Kafka로 보냅니다.

```json
{
  "eventType": "CODE_MODIFIED",
  "taskId": "...",
  "repository": "HS-hyeokjin/DevFlow",
  "prompt": "Kafka Consumer 관련 코드를 개선해줘",
  "workspace": "D:/.../workspaces/<taskId>",
  "branch": "devflow/<taskId>",
  "changedFiles": ["..."],
  "createdAt": "..."
}
```

Test Agent와 Code Agent가 같은 PC에서 실행되는 현재 개발 환경에서는
이 absolute workspace 경로를 공유합니다.

## Test Agent가 실행하는 명령

우선순위:

```text
Windows
1. mvnw.cmd -B -ntp verify
2. gradlew.bat test

Linux/macOS
1. ./mvnw -B -ntp verify
2. ./gradlew test

Wrapper가 없으면
3. mvn -B -ntp verify
4. gradle test
```

현재는 Maven/Gradle 프로젝트를 지원합니다.

## 결과 파일

기존 workspace의 `.devflow` 아래에 다음 파일이 추가됩니다.

```text
workspaces/<taskId>/
└─ .devflow/
   ├─ repo-map.md
   ├─ analysis.md
   ├─ change-plan.md
   ├─ changes.diff
   ├─ test-command.log
   └─ test-result.md
```

## TEST 결과 이벤트

성공:

```text
TEST_PASSED
```

실패:

```text
TEST_FAILED
```

실패 이벤트에는 원래 사용자 prompt와 테스트 로그 tail도 포함되므로,
다음 v0.6에서 Code Agent가 실패 원인을 보고 자동으로 다시 수정할 수 있습니다.

## 실행

### 1. Kafka

```powershell
docker compose up -d
```

### 2. Task Service

```powershell
.\mvnw.cmd -pl task-service spring-boot:run
```

### 3. Code Agent

```powershell
.\mvnw.cmd -pl code-agent spring-boot:run
```

### 4. Test Agent

새 PowerShell 창:

```powershell
.\mvnw.cmd -pl test-agent spring-boot:run
```

### 5. 작업 생성

기존과 동일하게 `POST /tasks`를 호출합니다.

정상 흐름:

```text
Task Service
[TASK CREATED]

Code Agent
[TASK RECEIVED]
[GIT CLONE COMPLETED]
[PROJECT ANALYSIS COMPLETED]
[CODE MODIFICATION COMPLETED]
[CODE_MODIFIED EVENT PUBLISHED]

Test Agent
[CODE_MODIFIED RECEIVED]
[TEST STARTED]
[TEST COMPLETED]
Status: TEST_PASSED
[TEST_PASSED EVENT PUBLISHED]
```

## 현재까지

- **v0.1 ✅** Task Service → Kafka → Code Agent
- **v0.2 ✅** GitHub clone
- **v0.3 ✅** 프로젝트 분석
- **v0.3.1 ✅** Mock AI
- **v0.3.5 ✅** Repo Map + 관련 파일 선택
- **v0.4 ✅** 로컬 코드 수정 + Git diff
- **v0.5 ✅** Test Agent + TEST_PASSED/TEST_FAILED 이벤트
- **v0.6** 테스트 실패 → Code Agent 자동 재수정
- **v0.7** Review Agent + GitHub PR
- **v1.0** 사람이 승인하는 AI 개발 Agent Workflow

## Branch Workflow

현재 기능 브랜치:

```text
feature/v0.5-test-agent
```

검증 후 main으로 merge합니다.
