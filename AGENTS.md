# AI assistant 작업 시 메타 규칙

이 문서는 Codex / Claude / 다른 AI assistant 가 이 repo (security-log-search) 에서 작업할 때
지켜야 할 규칙을 정리합니다. 과거에 실제로 어겼던 항목들을 모았습니다.

> 이 디렉토리(`.codex/`, `.claude/`) 는 `.gitignore` 에 포함되어 GitHub 에 올라가지 않습니다.

---

## 1. Commit author / 이메일

- **회사 이메일 절대 금지** (`*@strato.co.kr` 등). 개인 또는 GitHub noreply 만.
- 권장: `wittyahn@users.noreply.github.com`
- `git config --local user.email "wittyahn@users.noreply.github.com"` 으로 repo 마다 명시.

## 2. Commit 메시지

- **AI co-author trailer 금지**:
  ```
  Co-Authored-By: Codex Opus ... <noreply@anthropic.com>   ← 절대 추가하지 말 것
  ```
- **금지 표현** (이력서 / 포트폴리오 톤):
  - "이력서 매칭", "면접에서", "면접 talking point", "면접 마이너스"
  - "차별 포인트", "핵심 차별", "ROI"
  - "한 컷", "한 페이지"
- **회사명 / 이전 직장 정보 금지**: "SKT", "iFLAND", "netspresso", "nota", "안랩",
  "Splunk", "Microsoft Sentinel", "Elastic Security" 등 — 학술 표준 / OSS 명칭 인용은 가능
  (Elastic Common Schema, OCSF, Apache Flink, OpenSearch, ClickHouse 등).
- **Conventional Commits 따르기**: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`

## 3. 코드 / 문서 안 표현

- 도메인 코드와 문서는 **사실 위주, 자연스러운 한국어**.
- 영어 직역체 금지: "Trade-off" → "장단점", "When to revisit" → "다시 검토할 시점".
- 이모지 (★, ⭐, 🔴, 🟡, 🥇 등) 과다 사용 금지.
- 자기홍보 톤 ("이걸 보여주면 어필") 금지.
- AI 번역티 표현 금지 — "보안 위생", "박제", "사고 즉시 터짐", "5년차 cold-read 30초 룰"
  같은 어색한 한국어 사용하지 말 것.

## 4. 파일 / 디렉토리

- **빈 placeholder 패키지 금지** — `package-info.java` 만 있고 실제 클래스 0개인 패키지 금지.
- **작업 메모 파일 commit 금지** — `PLAN.md`, `TODO.md`, `notes.md` 같은 것은 `.gitignore` 에.
- 빌드 산출물 (`build/`, `out/`) 은 무조건 `.gitignore`. 단 `**/out/` 처럼 광범위한 패턴은
  hexagonal 패키지 (`adapter/out/`) 도 잡아버리니 `/out/` + `*/out/` + `!**/src/**/out/`
  패턴 사용.

## 5. 작업 절차

- 코드를 새로 만들기 전에 *기존 패턴* 확인 (다른 모듈 / aggregate 가 어떻게 했는지).
- 큰 변경 전에는 *설계 검토 → 동의 → 구현* 순서.
- 변경 후 항상 `./gradlew check` 통과 확인. Testcontainers 통합 테스트는 Docker 미가용 환경
  에서 자동 skip 되어도 OK. 단위 테스트는 모두 통과해야 함.
- 한 commit 은 하나의 논리적 변경만 (5-15개 파일 정도).

## 6. 도메인 / 기술 용어 정확성

- **ECS** = Elastic Common Schema (Elastic 사에서 정의한 보안 / 관측 로그 표준).
- **OCSF** = Open Cybersecurity Schema Framework (벤더 중립 보안 로그 표준, OASIS).
- **SIEM** = Security Information and Event Management.
- **ISMS-P** = 정보보호 및 개인정보보호 관리체계 인증 (한국 KISA).
- **CEP** = Complex Event Processing — 본 repo 의 Flink job 이 사용하는 패턴.
- 기술 용어 옆에는 *짧은 한국어 풀이* 를 함께 둘 것 (독자가 즉시 이해할 수 있게).
