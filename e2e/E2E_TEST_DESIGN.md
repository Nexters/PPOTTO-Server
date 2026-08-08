# 뽀또 사진 분석 파이프라인 E2E 테스트 설계 문서

**작성일:** 2026-07-31  
**버전:** 1.0  
**상태:** 완료

---

## 📋 목차

1. [개요](#개요)
2. [테스트 목적](#테스트-목적)
3. [테스트 범위](#테스트-범위)
4. [시스템 구성](#시스템-구성)
5. [테스트 시나리오](#테스트-시나리오)
6. [검증 항목](#검증-항목)
7. [성공/실패 기준](#성공실패-기준)
8. [실행 방법](#실행-방법)
9. [결과 분석](#결과-분석)
10. [문제 해결](#문제-해결)

---

## 개요

### 목적
뽀또 앱의 핵심 기능인 **사진 분석 파이프라인**이 엔드-투-엔드로 정상 작동하는지 검증

### 테스트 범위
- API 요청/응답 계약 검증
- GCS(Google Cloud Storage) 통합 검증
- Vertex AI Gemini 분석 모델 통합 검증
- 배지/스티커 생성 검증
- 특정 테마 스티커 재생성 검증

### 테스트 환경
- **언어:** Python 3.8+
- **의존성:** curl, Docker, PostgreSQL (로컬)
- **대상:** feat/9-vertex-ai-analysis-pipeline 브랜치

---

## 테스트 목적

| # | 목적 | 검증 항목 |
|---|------|---------|
| 1 | API 계약 검증 | Request/Response DTO 정확성 |
| 2 | 클라우드 통합 검증 | GCS 업로드, Signed URL |
| 3 | AI 분석 검증 | Gemini 분류, 배지 생성 |
| 4 | 성능 검증 | 전체 소요 시간, 병렬 처리 |
| 5 | 스티커 재생성 검증 | 테마 선택, image_key 교체, 리캡 문구 유지 |
| 6 | 오류 처리 검증 | 타임아웃, 실패 케이스 |

---

## 테스트 범위

### 포함되는 항목 ✅
- 100개 사진 업로드 (실제 GCS)
- Gemini 2.5 Flash 분류 모델 호출
- Gemini 2.5 Flash-Image 스티커 생성 모델 호출
- POST /stickers/{stickerId}/regenerate 스티커 재생성 호출
- 배지 및 설명문 생성
- 데이터베이스 상태 관리

### 제외되는 항목 ❌
- UI/UX 테스트
- 보안 테스트 (인증/인가)
- 성능 부하 테스트 (대량 동시 요청)
- 국제화 테스트

---

## 시스템 구성

```
사용자 앱
    ↓
[API Server] (localhost:8080)
    ├─ POST /analysis (Signed URL 발급)
    ├─ POST /analysis/{id}/start (분석 시작)
    ├─ POST /stickers/{id}/regenerate (스티커 재생성)
    └─ GET /analysis/{id} (상태 조회)
    ↓
[PostgreSQL DB] (localhost:54782)
    └─ analysis, photo 테이블
    ↓
[Google Cloud Storage]
    └─ ppotto-bucket-dev/photos/
    ↓
[Vertex AI]
    └─ gemini-2.5-flash (분류)
    └─ gemini-2.5-flash-image (스티커)
```

---

## 테스트 시나리오

### Happy Path (정상 흐름)

```
Step 0: 환경 검증
  └─ API 서버 접근 가능
  └─ DB 접근 가능
  └─ 사진 파일 준비됨
  └─ Redis health가 DOWN이어도 DB가 UP이면 로컬 E2E는 계속 진행

Step 1: Board 생성
  └─ User → Board 생성 (DB)

Step 2: 사진 준비
  └─ /photos/dir에서 100개 사진 선택
  └─ 확장자별 contentType 매핑
     - .jpeg → image/jpeg
     - .png → image/png
     - .heic → image/heic

Step 3: Signed URL 발급
  POST /analysis
  {
    "boardId": "...",
    "photos": [
      {
        "items": [
          {
            "takenAt": "2026-07-31T04:01:16Z",
            "contentType": "image/jpeg",
            "isRepresentative": true
          }
        ]
      },
      ...
    ]
  }
  ↓
  200 OK
  {
    "analysisId": "...",
    "uploads": [
      {"photoId": "...", "uploadUrl": "https://storage.googleapis.com/..."},
      ...
    ]
  }

Step 4: 사진 업로드
  PUT {uploadUrl}
  Header: Content-Type: image/jpeg
  Header: x-goog-content-length-range: 0,15728640
  Body: 이진 이미지 데이터
  ↓
  200 OK (각 사진)

Step 5: 분석 시작
  POST /analysis/{analysisId}/start
  ↓
  202 Accepted (또는 200 OK)
  {
    "uploadedCount": 100,
    "failedCount": 0
  }

Step 6: Gemini 분석 (동기)
  - Gemini 2.5 Flash로 100개 사진 분류
  - 4개 테마 감지
  - 각 테마별 배지/설명 생성

Step 7: 스티커 생성 (동기)
  - Gemini 2.5 Flash-Image로 스티커 생성
  - 타임아웃: 120초/테마

Step 8: 특정 테마 스티커 재생성 (선택)
  - --regenerate-theme 옵션이 있을 때 실행
  - --theme-query 값으로 스티커 title 또는 테마명 부분 매칭
  - POST /stickers/{stickerId}/regenerate 호출
  - 같은 stickerId의 image_key 변경 확인
  - title, summary 유지 확인

Step 9: 결과 로그 출력
  INFO AnalysisPipelineEventListener: analysis pipeline result for analysisId=...
  {
    "recapId": "...",
    "themes": [
      {
        "theme": "학습 앱 사용기",
        "badge": "열공모드!",
        "text": "꾸준히 앱으로 학습하며...",
        "stickerUrl": "gs://ppotto-bucket-dev/stickers/..."
      },
      ...
    ]
  }
```

### Error Cases (오류 시나리오)

| # | 시나리오 | 기대 결과 |
|---|---------|---------|
| 1 | 사진 < 90개 | ANALYSIS-001 (400) |
| 2 | 사진 > 100개 | ANALYSIS-001 (400) |
| 3 | 유효하지 않은 contentType | COMMON-001 (400) |
| 4 | 존재하지 않는 boardId | COMMON-002 (404) |
| 5 | GCS 업로드 실패 | ANALYSIS-006 (409) |
| 6 | Gemini 타임아웃 | ANALYSIS-XXX (500) |
| 7 | DB 연결 실패 | 예외 발생 |

---

## 검증 항목

### API 계약 검증

#### POST /analysis
```
검증 항목:
  ✓ Request body 형식 (boardId, photos[].items[])
  ✓ Response status code (200)
  ✓ Response body 형식 (analysisId, uploads[])
  ✓ uploads[] 요소 수 = photos[] 요소 수
  ✓ uploadUrl 형식 (GCS V4 Signed URL)
  ✓ uploadUrl 유효기간 (15분)
```

#### POST /analysis/{analysisId}/start
```
검증 항목:
  ✓ Request path parameter (analysisId UUID)
  ✓ Response status code (202 또는 200)
  ✓ Response body 형식 (uploadedCount, failedCount)
  ✓ uploadedCount == 100 (모두 성공)
  ✓ failedCount == 0
  ✓ 응답 시간 (600초 이내, 동기 처리)
```

#### POST /stickers/{stickerId}/regenerate
```
검증 항목:
  ✓ Request path parameter (stickerId UUID)
  ✓ Response status code (200)
  ✓ Response body 형식 (sticker, summary, comments, photos)
  ✓ sticker.id == 요청 stickerId
  ✓ sticker.type == IMAGE
  ✓ sticker.imageUrl 존재
  ✓ DB image_key 변경
  ✓ DB title, summary 유지
```

### 데이터베이스 검증

```
검증 항목:
  ✓ User 생성 확인
  ✓ Board 생성 및 user_id FK 확인
  ✓ Analysis 상태 전환
    - 생성: UPLOADING
    - 분석 중: ANALYZING
    - 완료: COMPLETED
    - 실패: FAILED
  ✓ Photo 상태 전환
    - 생성: PENDING
    - 업로드됨: COMPLETED
```

### GCS 통합 검증

```
검증 항목:
  ✓ Signed URL V4 형식
  ✓ PUT 요청 성공 (HTTP 200)
  ✓ 파일 메타데이터 저장 (size > 0)
  ✓ 100개 모든 파일 업로드됨
  ✓ 파일명 형식: photos/{analysisId}/{photoId}.{ext}
```

### Vertex AI 검증

```
검증 항목:
  ✓ Gemini 2.5 Flash 호출 성공
  ✓ 4개 테마 감지됨
  ✓ 각 테마별 배지, 설명, 타겟 대상 생성
  ✓ Gemini 2.5 Flash-Image 스티커 생성
  ✓ 스티커 생성 타임아웃: 120초
  ✓ 스티커 저장 위치: gs://ppotto-bucket-dev/stickers/
  ✓ 특정 테마 스티커 재생성 시 새 이미지 생성
```

### 성능 검증

```
검증 항목:
  ✓ Signed URL 발급: < 2초
  ✓ 100개 사진 업로드: < 60초 (병렬)
  ✓ Gemini 분류: < 20초
  ✓ 스티커 생성: < 120초/테마
  ✓ 전체 소요시간: < 3분
```

---

## 성공/실패 기준

### ✅ 성공 기준

모든 다음 조건을 만족해야 합니다:

1. **API 응답**
   - POST /analysis: 200 OK, 100개 Signed URL 발급
   - POST /start: 202/200 OK, uploadedCount=100, failedCount=0

2. **파일 업로드**
   - 100/100 사진 GCS 업로드 성공 (HTTP 200)
   - GCS 메타데이터 확인 (size > 0)

3. **분석 결과**
   - 4개 테마 감지
   - 각 테마별 배지/설명 생성
   - 로그에 AnalysisPipelineResult 기록

4. **배지/스티커 생성**
   - 최소 2/4 배지 이미지 생성 성공 (타임아웃 고려)
   - 모든 배지 gs:// 경로 저장됨

5. **스티커 재생성**
   - --regenerate-theme 사용 시 대상 스티커 선택 성공
   - 재생성 후 image_key 변경
   - title, summary 유지

6. **성능**
   - 전체 소요시간 < 3분

### ❌ 실패 기준

다음 중 하나 발생 시 실패:

1. **API 오류**
   - POST /analysis: 400 이상 HTTP 에러
   - POST /start: 400 이상 HTTP 에러

2. **데이터 오류**
   - 업로드된 파일 < 100개
   - GCS 메타데이터 누락
   - DB 상태 불일치

3. **분석 오류**
   - 테마 감지 < 3개 (임계값)
   - Gemini 호출 실패
   - 배지/스티커 생성 실패

4. **스티커 재생성 오류**
   - --theme-query에 매칭되는 이미지형 스티커 없음
   - 재생성 API 실패
   - 재생성 후 image_key 미변경
   - 재생성 후 title 또는 summary 변경

5. **성능**
   - 전체 소요시간 > 5분
   - API 응답 타임아웃

---

## 실행 방법

### 사전 요구사항

```bash
# Python 3.8+ 설치 확인
python3 --version

# 스크립트 권한 확인
chmod +x e2e/test_photosanalysis_pipeline.py

# 환경 확인
- API 서버 실행 중 (localhost:8080)
- PostgreSQL Docker 실행 중 (localhost:54782)
- 사진 디렉토리 준비 (/Users/dustin.hwang/Desktop/etc/wark/)
```

### 기본 실행

```bash
# 기본값으로 실행 (90개 사진)
python3 e2e/test_photosanalysis_pipeline.py

# 로그 확인
tail -f e2e_test.log
```

### 옵션 지정 실행

```bash
# 100개 사진으로 테스트
python3 e2e/test_photosanalysis_pipeline.py --photos-count 100

# 다른 API URL 지정
python3 e2e/test_photosanalysis_pipeline.py --api-url http://api.example.com:8080

# 병렬 워커 수 조정
python3 e2e/test_photosanalysis_pipeline.py --max-workers 20

# 특정 테마 스티커 재생성까지 실행
python3 e2e/test_photosanalysis_pipeline.py --theme-query "동물" --regenerate-theme

# 기존 분석 결과로 보고서와 재생성만 실행
python3 e2e/test_photosanalysis_pipeline.py \
  --report-analysis-id 01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f \
  --theme-query "동물" \
  --regenerate-theme

# 전체 옵션
python3 e2e/test_photosanalysis_pipeline.py \
  --api-url http://localhost:8080 \
  --db-host localhost \
  --db-port 54782 \
  --photos-dir ~/Desktop/etc/wark \
  --photos-count 100 \
  --max-workers 10 \
  --theme-query "동물" \
  --regenerate-theme
```

### 결과 확인

```bash
# 테스트 로그
cat e2e_test.log

# 최종 결과 (최신 리포트)
open $(ls -t e2e/reports/e2e_test_report_*.html | head -1)

# API 서버 로그에서 결과 추출
grep "analysis pipeline result" bootRun.log
```

---

## 결과 분석

### 성공 시

**해석:**
- 모든 100개 사진 업로드 완료
- Gemini 분석 성공
- HTML 보고서에서 4개 테마, 모델명, signed URL, 전체 소요시간 확인
- 재생성 옵션 사용 시 Sticker Regeneration 섹션에서 전후 image key와 title/summary 유지 확인

### 실패 시

HTML 보고서의 상태와 오류 메시지를 확인합니다.

**해석:**
- 사진 개수 검증 실패
- API 서버에서 요청 거부

---

## 문제 해결

### Q: "API 서버 접근 실패" 에러

**원인:**
- API 서버 실행 중 아님
- localhost:8080이 막혀있음

**해결:**
```bash
# API 서버 실행 확인
curl http://localhost:8080/actuator/health

# 포트 확인
lsof -i :8080

# API 서버 시작
cd /Users/dustin.hwang/IdeaProjects/Gallery100-Server
./gradlew bootRun
```

### Q: "사진 디렉토리 없음" 에러

**원인:**
- 사진 디렉토리 경로 오류
- 사진 파일 부족

**해결:**
```bash
# 디렉토리 확인
ls -la /Users/dustin.hwang/Desktop/etc/wark/

# 사진 개수 확인
ls /Users/dustin.hwang/Desktop/etc/wark/*.{jpeg,jpg,png,heic} 2>/dev/null | wc -l

# 옵션으로 경로 지정
python3 e2e/test_photosanalysis_pipeline.py \
  --photos-dir /path/to/photos \
  --photos-count 90
```

### Q: "Gemini 타임아웃" 에러

**원인:**
- 타임아웃 설정 부족
- Gemini 서버 느림

**해결:**
```bash
# .env에서 타임아웃 확인
cat .env | grep VERTEX_AI_.*_TIMEOUT

# 타임아웃 증가 (분류: 180초, 스티커: 120초)
VERTEX_AI_CLASSIFY_TIMEOUT_MS=300000
VERTEX_AI_STICKER_TIMEOUT_MS=180000

# 서버 재시작
./gradlew bootRun
```

### Q: "GCS 업로드 실패" 에러

**원인:**
- GCS 서비스 계정 권한 부족
- 버킷 이름 오류
- 네트워크 연결 문제

**해결:**
```bash
# 서비스 계정 확인
cat ./secrets/gcs-service-account.json | jq .client_email

# .env 확인
cat .env | grep GCS_

# GCS 버킷 접근 테스트
gsutil ls gs://ppotto-bucket-dev/

# IAM 권한 확인
gcloud projects get-iam-policy ppotto-project-503613
```

### Q: "DB 연결 실패" 에러

**원인:**
- PostgreSQL Docker 미실행
- 포트 포워딩 오류

**해결:**
```bash
# Docker 상태 확인
docker ps | grep ppotto-postgres

# Docker 시작
docker-compose up -d

# DB 접근 테스트
docker exec ppotto-postgres psql -U ppotto -d ppotto -c "SELECT 1;"
```

---

## 부록

### A. 환경 변수 참고

| 변수 | 기본값 | 설명 |
|------|-------|------|
| `VERTEX_AI_PROJECT` | ppotto-project-503613 | GCP 프로젝트 ID |
| `VERTEX_AI_LOCATION` | us-central1 | Vertex AI 지역 |
| `VERTEX_AI_CLASSIFY_TIMEOUT_MS` | 180000 | 분류 타임아웃 (3분) |
| `VERTEX_AI_STICKER_TIMEOUT_MS` | 120000 | 스티커 타임아웃 (2분) |
| `GCS_BUCKET` | ppotto-bucket-dev | GCS 버킷명 |
| `GCS_TIMEOUT_MILLIS` | 5000 | GCS 작업 타임아웃 |

### B. 커밋 메시지 예시

```
feat: E2E 테스트 스크립트 및 설계 문서 추가

- 사진 분석 파이프라인 자동 테스트 스크립트 구현
- 90~100개 사진 지원, 병렬 업로드
- 성공/실패 기준 명확히 정의
- 문제 해결 가이드 작성

Test: E2E 테스트 완료 (4개 테마, 50초)
```

---

**문서 작성자:** E2E Test Team  
**최종 검토:** 2026-07-31  
**다음 검토 예정:** 2026-08-15
