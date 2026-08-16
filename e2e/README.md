# Ppotto E2E Test Suite

뽀또 사진 분석 파이프라인의 엔드-투-엔드 자동화 테스트

## 📋 개요

이 디렉토리는 뽀또 앱의 핵심 기능인 **사진 업로드 → AI 분석 → 배지/스티커 생성** 파이프라인을 자동으로 테스트합니다.

- **대상:** feat/9-vertex-ai-analysis-pipeline 브랜치
- **테스트 범위:** API, GCS, Vertex AI Gemini 통합
- **예상 시간:** ~50초
- **사진 개수:** 90~100개
- **추가 검증:** 특정 테마 스티커 재생성

## 🚀 빠른 시작

### 1단계: 환경 확인

```bash
# API 서버 실행 (다른 터미널에서)
cd /Users/dustin.hwang/IdeaProjects/Gallery100-Server
./gradlew bootRun

# 데이터베이스 실행 (다른 터미널에서)
docker-compose up -d

# 헬스체크
curl http://localhost:8080/actuator/health
```

로컬 compose에 Redis가 없어서 전체 헬스체크가 `DOWN`이어도 DB 컴포넌트가 `UP`이면 이 E2E는 계속 진행합니다.

### 2단계: 테스트 실행

```bash
# 기본 실행 (90개 사진)
python3 e2e/test_photosanalysis_pipeline.py

# 100개 사진으로 테스트
python3 e2e/test_photosanalysis_pipeline.py --photos-count 100

# 180장 사진을 90개 연사 그룹(그룹당 2장)으로 분석
python3 e2e/test_photosanalysis_pipeline.py --photos-count 180 --group-size 2

# 특정 테마의 스티커 재생성까지 테스트
python3 e2e/test_photosanalysis_pipeline.py --theme-query "동물" --regenerate-theme

# 결과 확인 (최신 리포트)
open $(ls -t e2e/reports/e2e_test_report_*.html | head -1)
```

## 📂 디렉토리 구조

```
e2e/
├── test_photosanalysis_pipeline.py  # 메인 테스트 스크립트
├── E2E_TEST_DESIGN.md               # 설계 문서 (상세)
├── README.md                        # 이 파일 (빠른 시작)
└── reports/                         # 테스트 결과 (자동 생성, Git 제외)
    └── e2e_test_report_<YYYYMMDD_HHMMSS>.html  # 실행마다 새로 생성
```

## 📖 사용 방법

### 기본 실행

```bash
python3 test_photosanalysis_pipeline.py
```

### 옵션

```bash
python3 test_photosanalysis_pipeline.py \
  --api-url http://localhost:8080              # API 서버 주소
  --db-host localhost                          # PostgreSQL 호스트
  --db-port 54782                              # PostgreSQL 포트
  --photos-dir ~/Desktop/etc/wark              # 사진 디렉토리
  --photos-count 90                            # 테스트 사진 개수
  --group-size 1                               # 분석 요청 그룹당 사진 개수 (1-10)
  --max-workers 10                             # 병렬 업로드 워커 수
  --theme-query "동물"                         # 재생성할 테마/스티커 제목 검색어
  --regenerate-theme                           # 특정 테마 스티커 재생성 수행
```

기존 분석 결과를 재사용해 보고서와 재생성만 확인할 수도 있습니다.

```bash
python3 test_photosanalysis_pipeline.py \
  --report-analysis-id 01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f \
  --theme-query "동물" \
  --regenerate-theme
```

### 로그 확인

```bash
# 실시간 로그 확인
tail -f e2e_test.log

# 최종 결과 (최신 리포트)
open $(ls -t e2e/reports/e2e_test_report_*.html | head -1)

# API 로그에서 분석 결과 추출
grep "analysis pipeline result" <path-to-api-log>
```

## ✅ 성공 기준

테스트 완료 후 다음을 확인하세요:

| 항목 | 기대값 | 비고 |
|------|-------|------|
| Signed URL 발급 | ✅ | 20-100개 그룹, 그룹당 1-10장 |
| 사진 업로드 | ✅ | 100% 성공 |
| Gemini 분석 | ✅ | 4개 테마 감지 |
| 배지 생성 | ✅ 이상 | 3개 이상 (스티커 타임아웃 고려) |
| 스티커 재생성 | ✅ | `--regenerate-theme` 사용 시 image key 변경, title/summary 유지 |
| 소요시간 | < 3분 | API 동기 처리 |

### 결과 파일

`e2e/reports/e2e_test_report_<YYYYMMDD_HHMMSS>.html`에 업로드 사진, 생성 스티커(제목·mainColor 색상 스와치 포함), signed URL, 모델명, Gemini 파이프라인 시간, 테마 분류 요약, 스티커 재생성 전후 정보가 기록됩니다. 실행마다 타임스탬프가 붙은 새 파일로 저장되므로 이전 결과를 덮어쓰지 않습니다.

## 🔧 문제 해결

### API 서버 연결 안 됨

```bash
# 서버 상태 확인
curl -v http://localhost:8080/actuator/health

# 서버 시작
cd /Users/dustin.hwang/IdeaProjects/Gallery100-Server
./gradlew bootRun
```

### 사진 파일을 찾을 수 없음

```bash
# 디렉토리 확인
ls /Users/dustin.hwang/Desktop/etc/wark/ | head -10

# 사진 개수 확인
ls /Users/dustin.hwang/Desktop/etc/wark/*.{jpeg,jpg,png} 2>/dev/null | wc -l

# 다른 경로 지정
python3 test_photosanalysis_pipeline.py --photos-dir /path/to/photos
```

### 분석 타임아웃

```bash
# .env 타임아웃 확인
cat .env | grep VERTEX_AI.*TIMEOUT

# 필요시 증가 (단위: ms)
# VERTEX_AI_CLASSIFY_TIMEOUT_MS=300000
# VERTEX_AI_VERIFY_TIMEOUT_MS=60000
```

### 데이터베이스 연결 실패

```bash
# Docker 상태 확인
docker ps | grep ppotto

# 재시작
docker-compose restart ppotto-postgres

# 수동 테스트
docker exec ppotto-postgres psql -U ppotto -d ppotto -c "SELECT 1;"
```

## 📊 테스트 흐름

```
┌─ 환경 검증
│  └─ API 서버 ✓
│  └─ DB 접근 ✓
│  └─ 사진 파일 ✓
│
├─ Board 생성 (DB)
│  └─ User → Board
│
├─ Signed URL 발급
│  └─ POST /analysis
│  └─ 100개 URL 획득
│
├─ 사진 업로드 (병렬)
│  └─ PUT {uploadUrl}
│  └─ 100개 완료
│
├─ 분석 시작
│  └─ POST /analysis/{id}/start
│  └─ Gemini 분류 시작 (동기)
│
├─ 테마별 스티커 생성
│  └─ 4개 테마 감지
│  └─ 배지 생성
│  └─ sourcePhoto 배경 제거 및 업로드
│
├─ 특정 테마 스티커 재생성 (선택)
│  └─ title 또는 테마명으로 대상 스티커 선택
│  └─ POST /stickers/{id}/regenerate
│  └─ image_key 변경 및 title/summary 유지 확인
│
└─ 결과 저장
   └─ e2e/reports/e2e_test_report_<timestamp>.html
```

## 📝 주요 검증 항목

### API 계약
- ✅ POST /analysis 요청/응답 형식
- ✅ Signed URL V4 형식
- ✅ POST /start 응답 시간 (동기)

### GCS 통합
- ✅ Signed URL 인증
- ✅ 100개 파일 업로드
- ✅ 메타데이터 저장

### Vertex AI
- ✅ Gemini 2.5 Flash 호출
- ✅ 4개 테마 분류
- ✅ 배지/설명 생성
- ✅ sourcePhoto 기반 스티커 cutout 업로드
- ✅ 특정 테마 스티커 재생성 (선택)

## 📚 추가 문서

- **[E2E_TEST_DESIGN.md](./E2E_TEST_DESIGN.md)** - 상세 설계 문서
  - 테스트 목적, 범위, 시나리오
  - 검증 항목 상세 설명
  - 성공/실패 기준
  - 문제 해결 가이드

## 🔄 자동화 통합

### GitHub Actions (CI/CD)

```yaml
name: E2E Test
on: [pull_request]

jobs:
  e2e-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_PASSWORD: ppotto
          POSTGRES_DB: ppotto
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
        with:
          python-version: '3.10'
      - name: Start API Server
        run: ./gradlew bootRun &
      - name: Wait for API
        run: sleep 30 && curl http://localhost:8080/actuator/health
      - name: Run E2E Test
        run: python3 e2e/test_photosanalysis_pipeline.py
      - name: Upload Results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: e2e-results
          path: e2e/reports/*.html
```

## 💡 팁

### 빠른 테스트 (90개 사진)
```bash
python3 test_photosanalysis_pipeline.py --photos-count 90
```

### 완전한 테스트 (100개 사진)
```bash
python3 test_photosanalysis_pipeline.py --photos-count 100
```

### 재생성 포함 테스트
```bash
python3 test_photosanalysis_pipeline.py --theme-query "동물" --regenerate-theme
```

### 병렬 처리 최적화
```bash
# 네트워크 대역폭 충분한 경우
python3 test_photosanalysis_pipeline.py --max-workers 20

# 네트워크 제한적인 경우
python3 test_photosanalysis_pipeline.py --max-workers 5
```

## 📞 연락처

질문이나 문제 발생 시:
1. [E2E_TEST_DESIGN.md](./E2E_TEST_DESIGN.md)의 문제 해결 섹션 참고
2. 로그 파일 확인: `e2e_test.log`
3. API 서버 로그 확인: `bootRun.log`

---

**마지막 업데이트:** 2026-07-31  
**테스트 버전:** 1.0  
**지원 브랜치:** feat/9-vertex-ai-analysis-pipeline
