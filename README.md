# DevFlow

## v1.0 — Human Approval Workflow

**개발 브랜치:** `feature/v1.0-human-approval`

AI Agent는 코드 수정·테스트·리뷰·Pull Request 생성까지 자동 수행하지만 **merge는 사람의 명시적 승인 이후에만 실행**한다.

```text
POST /tasks
   ↓
Task Service
   ↓
Code Agent → Test Agent → Review Agent
                         ↓
                  GitHub Pull Request
                         ↓
                 WAITING_APPROVAL
                         ↓
             ┌───────────┴───────────┐
             │                       │
     POST /approve             POST /reject
             │                       │
      HUMAN_APPROVED          HUMAN_REJECTED
             │                       │
             ↓                       └→ REJECTED
           Kafka
             ↓
        Review Agent
             ↓
       GitHub PR merge
             ↓
    PULL_REQUEST_MERGED
             ↓
        Task Service
             ↓
         COMPLETED
```

### Task 상태 조회

```http
GET /tasks/{taskId}
```

주요 상태:

```text
QUEUED
→ TESTING
→ FIXING         (테스트 실패 시)
→ REVIEWING
→ WAITING_APPROVAL
→ APPROVAL_SUBMITTED
→ COMPLETED

또는
REVIEW_REJECTED / REJECTED / MERGE_FAILED
```

Task 상태는 기본적으로 `./task-state`에 로컬 영속 저장한다. 학습용 단일 노드 구현이며 운영 환경에서는 DB로 교체하는 것이 적절하다.

### 사람 승인

PR이 생성되어 상태가 `WAITING_APPROVAL`인 경우에만 승인할 수 있다.

```http
POST /tasks/{taskId}/approve
Content-Type: application/json

{
  "decidedBy": "hyeokjin",
  "reason": "변경 내용 확인 완료"
}
```

거절:

```http
POST /tasks/{taskId}/reject
Content-Type: application/json

{
  "decidedBy": "hyeokjin",
  "reason": "추가 수정 필요"
}
```

승인/거절 API는 현재 로컬 학습용으로 인증 기능이 없다. 실제 운영에서는 SSO/OAuth/RBAC 등 인증·권한 검사가 반드시 필요하다.

### GitHub merge 조건

실제 PR 생성과 merge는 다음 환경변수를 명시적으로 활성화한 경우에만 가능하다.

```powershell
$env:DEVFLOW_GITHUB_PUBLISH_ENABLED="true"
$env:DEVFLOW_GITHUB_TOKEN="github_token"
```

토큰은 소스에 저장하지 않는다. PR 생성 및 merge 권한이 필요하다.

사람이 `approve` API를 호출하기 전에는 Review Agent가 자동 merge하지 않는다.

---

AI 개발 에이전트를 직접 만들면서 **MSA + Kafka + AI Agent**를 학습하는 프로젝트입니다.

## v0.7 — Review Agent + GitHub Pull Request

**개발 브랜치:** feature/v1.0-human-approval

테스트가 통과한 변경만 Review Agent가 검토한다.

```text
Task Service
  ↓ DEV_TASK_CREATED
Code Agent
  ↓ CODE_MODIFIED
Test Agent
  ├ TEST_FAILED → Code Agent 재수정
  └ TEST_PASSED
        ↓
    Review Agent
      ├ Mock/OpenAI Review
      ├ review-result.md 저장
      ├ 변경 파일만 Git commit
      ├ 작업 브랜치 push
      └ GitHub Pull Request 생성
```

### 안전 기본값

GitHub 원격 publish는 기본적으로 비활성화되어 있다.

```yaml
devflow:
  github:
    publish-enabled: false
```

실제 PR 생성을 허용할 때만 Review Agent 실행 전에 환경변수를 설정한다.

```powershell
$env:DEVFLOW_GITHUB_PUBLISH_ENABLED="true"
$env:DEVFLOW_GITHUB_TOKEN="github_token"
.\mvnw.cmd -pl review-agent spring-boot:run
```

토큰은 소스코드에 저장하지 않는다. GitHub Contents 쓰기 및 Pull Request 생성 권한이 필요하다.

### 변경 파일 manifest

Code Agent는 실제 수정한 파일을 누적 기록한다.

```text
.devflow/changed-files.txt
```

Review Agent는 이 목록의 tracked file만 commit한다. .devflow 결과 파일이나 관계없는 로컬 수정은 commit하지 않는다.

### Review 결과

```text
.devflow/review-result.md
.devflow/review-state.properties
```

Review Agent는 review-result Topic에 다음 상태를 발행한다.

```text
REVIEW_APPROVED
REVIEW_REJECTED
PULL_REQUEST_CREATED
```

Mock Review는 테스트 성공, diff 존재, 변경 파일 manifest 존재만 확인한다. 실제 코드 품질 검토는 OpenAI review provider 등 실제 LLM 사용 시 수행된다.

---

## v0.6 — Automated Test Failure Retry

**개발 브랜치:** `feature/v0.7-review-agent-pr`

- Code Agent가 `test-result` Topic의 `TEST_FAILED`를 수신
- 기존 `devflow/<taskId>` 로컬 브랜치와 workspace를 재사용
- 테스트 실패 로그 마지막 최대 4,000자를 참고해 관련 파일을 다시 선택·분석·수정
- `CODE_MODIFIED` 재발행 → Test Agent 재테스트
- 기본 재시도 최대 2회(`devflow.retry.max-attempts`), 초과 시 중지
- 동일 실패 이벤트 중복 처리 억제 및 결과 상태 저장
- 분석용으로 잘린 소스 파일을 전체 내용으로 덮어쓰지 않도록 안전 검사 추가
- `TEST_PASSED` 수신 시 상태 `COMPLETED` 기록

```text
Task Service → dev-task-created → Code Agent
                                   ↓ CODE_MODIFIED
                              code-modified → Test Agent
                                                 ↓
                                            test-result
                                                 ↓
                               Code Agent (retry worker)
                               ├ TEST_PASSED → COMPLETED
                               └ TEST_FAILED → 코드 재수정 → CODE_MODIFIED
                                                (최대 2회)
```

```text
workspaces/<taskId>/.devflow/
├─ retry-state.properties    # 시도 횟수, 마지막 실패 이벤트, 상태
└─ retry-history.md          # 각 재시도 기록
```

**Mock 모드 주의:** 재시도 단계별로 `DEVFLOW-MOCK-RETRY-1` 등의 주석을 추가하여 반복 흐름만 검증한다. 실제 테스트 실패 원인을 해결하지는 않는다. 실제 수정은 `openai` 공급자 등 실제 LLM 사용 시에만 가능하다.

**개발 환경 제약:** Code Agent와 Test Agent가 같은 파일시스템을 사용해야 한다. 분산 서버 배포 시 공유 workspace/아티팩트 스토어가 필요하다. 이 버전은 학습용이며 분산 락, 완전한 exactly-once 처리, 컨테이너 격리 테스트 실행은 아직 미구현 상태다.

---

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
- **v0.6 ✅** 테스트 실패 → Code Agent 자동 재수정 (최대 2회)
- **v0.7 ✅** Review Agent + GitHub PR
- **v1.0 ✅** 사람이 승인하는 AI 개발 Agent Workflow

## Branch Workflow

현재 기능 브랜치:

```text
feature/v0.6-auto-retry
```

검증 후 main으로 merge합니다.
