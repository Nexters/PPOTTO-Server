#!/usr/bin/env python3
"""
Ppotto 사진 분석 파이프라인 E2E 테스트 스크립트

사진 업로드 → Gemini 분석 → 배지/스티커 생성 파이프라인의 엔드-투-엔드 테스트
"""

import argparse
import base64
import hashlib
import html
import hmac
import json
import logging
import os
import subprocess
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from typing import Dict, List, Tuple
from urllib.parse import quote

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)
logger = logging.getLogger(__name__)


class PhotosPipelineE2ETest:
    """뽀또 사진 분석 파이프라인 E2E 테스트"""

    def __init__(
        self,
        api_url: str = "http://localhost:8080",
        db_host: str = "localhost",
        db_port: int = 54782,
        db_name: str = "ppotto",
        db_user: str = "ppotto",
        db_password: str = "ppotto",
        photos_dir: str = None,
        photos_count: int = 90,
        max_workers: int = 10,
        theme_query: str = None,
        regenerate_theme: bool = False,
    ):
        self.api_url = api_url
        self.db_host = db_host
        self.db_port = db_port
        self.db_name = db_name
        self.db_user = db_user
        self.db_password = db_password
        self.photos_dir = photos_dir or os.path.expanduser("~/Desktop/etc/wark")
        self.photos_count = photos_count
        self.max_workers = max_workers
        self.theme_query = theme_query
        self.regenerate_theme = regenerate_theme
        self.test_results = {}
        self.access_token = None
        self.board_id = None
        self.user_id = None
        self.report_dir = "e2e/reports"
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        self.report_html_path = f"{self.report_dir}/e2e_test_report_{timestamp}.html"

    def run(self) -> bool:
        """전체 E2E 테스트 실행"""
        logger.info("=" * 70)
        logger.info("🚀 뽀또 사진 분석 파이프라인 E2E 테스트 시작")
        logger.info("=" * 70)

        start_time = time.time()

        try:
            if not self._validate_environment():
                return False

            self.board_id = self._create_board()
            if not self.board_id:
                return False

            photos = self._prepare_photos()
            if not photos:
                return False

            uploads = self._create_analysis(self.board_id, photos)
            if not uploads:
                return False

            if not self._upload_photos(photos, uploads):
                return False

            analysis_id = list(self.test_results.values())[0]
            if not self._start_analysis(analysis_id):
                return False

            if self.regenerate_theme and not self._regenerate_theme_sticker(analysis_id):
                self._mark_failure(start_time)
                return False

            elapsed = time.time() - start_time
            logger.info("\n" + "=" * 70)
            logger.info(f"✅ E2E 테스트 완료 ({elapsed:.0f}초)")
            logger.info("=" * 70)

            self.test_results['elapsed_seconds'] = elapsed
            self.test_results['status'] = 'success'
            self._write_report()

            return True

        except Exception as e:
            logger.error(f"❌ 테스트 실패: {e}", exc_info=True)
            self.test_results['status'] = 'failure'
            self.test_results['error'] = str(e)
            self._write_report()
            return False

    def _mark_failure(self, start_time: float) -> None:
        self.test_results['elapsed_seconds'] = time.time() - start_time
        self.test_results['status'] = 'failure'
        self._write_report()

    def _validate_environment(self) -> bool:
        """환경 검증"""
        logger.info("\n[검증] 환경 확인 중...")

        try:
            result = subprocess.run(
                ["curl", "-s", f"{self.api_url}/actuator/health"],
                capture_output=True, text=True, timeout=5
            )
            if result.returncode != 0:
                logger.error(f"❌ API 서버 접근 실패: {self.api_url}")
                return False

            health = json.loads(result.stdout)
            if not self._is_api_usable(health):
                logger.error("❌ API 서버 상태 이상")
                return False

            logger.info(f"✅ API 서버 정상: {self.api_url}")

            if not os.path.isdir(self.photos_dir):
                logger.error(f"❌ 사진 디렉토리 없음: {self.photos_dir}")
                return False

            image_files = [
                f for f in os.listdir(self.photos_dir)
                if f.lower().endswith(('.jpeg', '.jpg', '.png', '.heic'))
            ]
            if len(image_files) < self.photos_count:
                logger.error(f"❌ 사진 부족: {len(image_files)}개 (필요: {self.photos_count}개)")
                return False

            logger.info(f"✅ 사진 디렉토리 준비: {len(image_files)}개")

            return True

        except Exception as e:
            logger.error(f"❌ 환경 검증 실패: {e}")
            return False

    def _is_api_usable(self, health: Dict) -> bool:
        if health.get("status") == "UP":
            return True

        db_status = (
            health
            .get("components", {})
            .get("db", {})
            .get("status")
        )
        if db_status == "UP":
            logger.warning("API 헬스체크가 UP은 아니지만 DB가 UP이므로 E2E를 계속 진행합니다.")
            return True
        return False

    def _create_board(self) -> str:
        """Board 생성"""
        logger.info("\n[준비] Board 생성 중...")

        try:
            suffix = int(time.time() * 1000)
            cmd = f"""docker exec ppotto-postgres psql -U {self.db_user} -d {self.db_name} -A -t -F '|' -c "
                WITH u AS (
                    INSERT INTO users (provider, provider_user_id, email, name)
                    VALUES ('KAKAO', 'e2e-{suffix}', 'e2e-{suffix}@example.com', 'e2e사용자')
                    RETURNING id
                )
                INSERT INTO boards (user_id, name) SELECT id, 'E2E' FROM u RETURNING id, user_id
            " 2>/dev/null"""

            result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
            if result.returncode != 0 or not result.stdout.strip():
                logger.error(f"❌ Board 생성 실패")
                return None

            board_id, self.user_id = result.stdout.strip().splitlines()[0].split("|")
            self.access_token = self._issue_access_token(self.user_id)
            logger.info(f"✅ Board 생성됨")
            return board_id

        except Exception as e:
            logger.error(f"❌ Board 생성 중 오류: {e}")
            return None

    def _issue_access_token(self, user_id: str) -> str:
        """테스트 사용자용 JWT 발급"""
        now = int(time.time())
        header = {"alg": "HS256"}
        payload = {
            "iss": os.getenv("JWT_ISSUER", "ppotto"),
            "sub": user_id,
            "iat": now,
            "exp": now + int(os.getenv("JWT_ACCESS_TOKEN_EXPIRATION_SECONDS", "3600")),
            "jti": f"e2e-{now}",
            "token_use": "access"
        }
        secret = os.getenv("JWT_SECRET", "0123456789abcdef0123456789abcdef").encode()

        def encode(value: Dict) -> bytes:
            data = json.dumps(value, separators=(",", ":")).encode()
            return base64.urlsafe_b64encode(data).rstrip(b"=")

        signing_input = b".".join([encode(header), encode(payload)])
        signature = hmac.new(secret, signing_input, hashlib.sha256).digest()
        return b".".join([signing_input, base64.urlsafe_b64encode(signature).rstrip(b"=")]).decode()

    def _prepare_photos(self) -> List[Dict]:
        """사진 준비"""
        logger.info(f"\n[Step 1] 사진 준비 중 ({self.photos_count}개)...")

        try:
            all_files = [
                f for f in sorted(os.listdir(self.photos_dir))
                if f.lower().endswith(('.jpeg', '.jpg', '.png', '.heic'))
            ]

            image_files = all_files[:self.photos_count]

            photos = []
            for f in image_files:
                ext = f.split('.')[-1].lower()
                if ext in ['jpeg', 'jpg']:
                    ct = "image/jpeg"
                elif ext == 'png':
                    ct = "image/png"
                elif ext == 'heic':
                    ct = "image/heic"
                else:
                    ct = f"image/{ext}"

                photos.append({
                    "takenAt": "2026-07-31T04:01:16Z",
                    "contentType": ct,
                    "_filename": f
                })

            logger.info(f"✅ {len(photos)}개 사진 준비 완료")
            return photos

        except Exception as e:
            logger.error(f"❌ 사진 준비 중 오류: {e}")
            return None

    def _create_analysis(self, board_id: str, photos: List[Dict]) -> List[Dict]:
        """Signed URL 발급"""
        logger.info("\n[Step 2] Signed URL 발급 중...")

        try:
            payload = {
                "boardId": board_id,
                "photos": [
                    {
                        "items": [
                            {
                                "takenAt": p["takenAt"],
                                "contentType": p["contentType"],
                                "isRepresentative": True,
                            }
                        ]
                    }
                    for p in photos
                ]
            }

            result = subprocess.run(
                [
                    "curl", "-s", "-X", "POST", f"{self.api_url}/analysis",
                    "-H", "Content-Type: application/json",
                    "-H", f"Authorization: Bearer {self.access_token}",
                    "-d", json.dumps(payload)
                ],
                capture_output=True, text=True, timeout=10
            )

            response = json.loads(result.stdout)
            if not response.get("success"):
                logger.error(f"❌ Signed URL 발급 실패: {response['error']['message']}")
                return None

            analysis_id = response["data"]["analysisId"]
            uploads = response["data"]["uploads"]

            logger.info(f"✅ {len(uploads)}개 Signed URL 발급 완료")

            self.test_results['analysis_id'] = analysis_id
            self.test_results['board_id'] = self.board_id
            self.test_results['user_id'] = self.user_id
            self.test_results['photos'] = self._build_photo_report_items(analysis_id, photos, uploads)

            return uploads

        except Exception as e:
            logger.error(f"❌ Signed URL 발급 중 오류: {e}")
            return None

    def _upload_photos(self, photos: List[Dict], uploads: List[Dict]) -> bool:
        """사진 업로드"""
        logger.info("\n[Step 3] 사진 업로드 중...")

        try:
            def upload_file(idx: int, upload_url: str, content_type: str, file_path: str) -> Tuple[int, bool]:
                try:
                    result = subprocess.run(
                        [
                            "curl", "-s", "-o", "/dev/null", "-X", "PUT",
                            upload_url,
                            "-H", f"Content-Type: {content_type}",
                            "-H", "x-goog-content-length-range: 0,15728640",
                            "--data-binary", f"@{file_path}"
                        ],
                        capture_output=True, timeout=30
                    )
                    return idx, result.returncode == 0
                except Exception as e:
                    return idx, False

            uploaded = 0

            with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
                futures = [
                    executor.submit(
                        upload_file,
                        i + 1,
                        uploads[i]["uploadUrl"],
                        photos[i]["contentType"],
                        os.path.join(self.photos_dir, photos[i]["_filename"])
                    )
                    for i in range(len(uploads))
                ]

                for future in as_completed(futures):
                    idx, success = future.result()
                    if success:
                        uploaded += 1

                    if uploaded % 30 == 0 or uploaded == len(uploads):
                        logger.info(f"  [{uploaded}/{len(uploads)}]")

            logger.info(f"✅ {uploaded}/{len(uploads)}개 사진 업로드 완료")
            self.test_results['uploaded_photos'] = uploaded

            return True

        except Exception as e:
            logger.error(f"❌ 사진 업로드 중 오류: {e}")
            return False

    def _start_analysis(self, analysis_id: str) -> bool:
        """분석 시작"""
        logger.info("\n[Step 4] Gemini 분석 시작 (1~2분)...\n")

        try:
            analysis_start_time = time.time()
            result = subprocess.run(
                [
                    "curl", "-s", "-X", "POST",
                    f"{self.api_url}/analysis/{analysis_id}/start",
                    "-H", "Content-Type: application/json",
                    "-H", f"Authorization: Bearer {self.access_token}"
                ],
                capture_output=True, text=True, timeout=600
            )
            self.test_results['start_analysis_request_seconds'] = time.time() - analysis_start_time

            response = json.loads(result.stdout)
            if not response.get("success"):
                logger.error(f"❌ 분석 시작 실패: {response['error']['message']}")
                return False

            logger.info(f"✅ 분석 완료")
            logger.info(f"   업로드됨: {response['data']['uploadedCount']}")
            logger.info(f"   실패: {response['data']['failedCount']}")

            self.test_results['analysis_result'] = response['data']
            self.test_results['stickers'] = self._fetch_sticker_report_items(analysis_id)
            self.test_results['analysis_timing'] = self._fetch_analysis_timing(analysis_id)
            self.test_results['models'] = self._model_report_items()
            self.test_results['themes'] = self._fetch_theme_report_items(analysis_id)

            return True

        except Exception as e:
            logger.error(f"❌ 분석 시작 중 오류: {e}")
            return False

    def _regenerate_theme_sticker(self, analysis_id: str) -> bool:
        """특정 테마의 스티커 재생성"""
        logger.info("\n[Step 5] 특정 테마 스티커 재생성 중...")

        regeneration_result = {
            "status": "failure",
            "theme_query": self.theme_query or "",
            "candidates": self._regeneration_candidates(),
        }
        self.test_results["regeneration"] = regeneration_result

        selected = self._select_sticker_for_regeneration()
        if not selected:
            regeneration_result["error"] = "재생성할 이미지형 스티커를 찾지 못했습니다."
            logger.error(f"❌ {regeneration_result['error']}")
            return False

        before = self._fetch_sticker_by_id(selected["sticker_id"]) or selected
        regeneration_result["selected_before"] = before
        logger.info(f"선택된 스티커: {before.get('title')} ({before.get('sticker_id')})")

        try:
            request_start_time = time.time()
            result = subprocess.run(
                [
                    "curl", "-s", "-X", "POST",
                    f"{self.api_url}/stickers/{before['sticker_id']}/regenerate",
                    "-H", "Content-Type: application/json",
                    "-H", f"Authorization: Bearer {self.access_token}",
                ],
                capture_output=True,
                text=True,
                timeout=600,
            )
            regeneration_result["request_seconds"] = time.time() - request_start_time
            response = json.loads(result.stdout)
            regeneration_result["api_response"] = response.get("data")

            if not response.get("success"):
                error = response.get("error") or {}
                regeneration_result["error"] = error.get("message") or "재생성 API 호출에 실패했습니다."
                logger.error(f"❌ 스티커 재생성 실패: {regeneration_result['error']}")
                return False

            response_sticker = response["data"]["sticker"]
            if response_sticker["id"] != before["sticker_id"]:
                regeneration_result["error"] = "재생성 응답의 스티커 ID가 요청 ID와 다릅니다."
                logger.error(f"❌ {regeneration_result['error']}")
                return False
            if response_sticker.get("type") != "IMAGE" or not response_sticker.get("imageUrl"):
                regeneration_result["error"] = "재생성 응답에 이미지형 스티커 URL이 없습니다."
                logger.error(f"❌ {regeneration_result['error']}")
                return False

            after = self._fetch_sticker_by_id(before["sticker_id"])
            regeneration_result["selected_after"] = after
            if not after:
                regeneration_result["error"] = "재생성 후 DB에서 스티커를 찾지 못했습니다."
                logger.error(f"❌ {regeneration_result['error']}")
                return False
            if before.get("image_key") == after.get("image_key"):
                regeneration_result["error"] = "재생성 후 image_key가 변경되지 않았습니다."
                logger.error(f"❌ {regeneration_result['error']}")
                return False
            if before.get("title") != after.get("title"):
                regeneration_result["error"] = "재생성 후 title이 변경되었습니다."
                logger.error(f"❌ {regeneration_result['error']}")
                return False
            if before.get("summary") != after.get("summary"):
                regeneration_result["error"] = "재생성 후 summary가 변경되었습니다."
                logger.error(f"❌ {regeneration_result['error']}")
                return False

            regeneration_result["status"] = "success"
            self.test_results["stickers"] = self._fetch_sticker_report_items(analysis_id)
            self.test_results["themes"] = self._fetch_theme_report_items(analysis_id)
            logger.info("✅ 스티커 재생성 완료")
            return True

        except Exception as e:
            regeneration_result["error"] = str(e)
            logger.error(f"❌ 스티커 재생성 중 오류: {e}")
            return False

    def _build_photo_report_items(
        self,
        analysis_id: str,
        photos: List[Dict],
        uploads: List[Dict],
    ) -> List[Dict]:
        return [
            {
                "photo_id": upload["photoId"],
                "filename": photo["_filename"],
                "content_type": photo["contentType"],
                "object_key": self._photo_object_key(analysis_id, upload["photoId"], photo["contentType"]),
                "signed_url": self._sign_gcs_read_url(
                    self._photo_object_key(analysis_id, upload["photoId"], photo["contentType"]),
                ),
            }
            for photo, upload in zip(photos, uploads)
        ]

    def _fetch_sticker_report_items(self, analysis_id: str) -> List[Dict]:
        cmd = f"""docker exec ppotto-postgres psql -U {self.db_user} -d {self.db_name} -A -t -F '|' -c "
            SELECT id, type, title, summary, source_photo_id, image_key, text_content
            FROM stickers
            WHERE analysis_id = '{analysis_id}' AND deleted_at IS NULL
            ORDER BY z_index, created_at
        " 2>/dev/null"""
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        if result.returncode != 0 or not result.stdout.strip():
            return self._fetch_gcs_sticker_report_items(analysis_id)

        stickers = []
        for line in result.stdout.strip().splitlines():
            sticker_id, sticker_type, title, summary, source_photo_id, image_key, text_content = line.split("|", 6)
            stickers.append(
                {
                    "sticker_id": sticker_id,
                    "type": sticker_type,
                    "title": title,
                    "summary": summary,
                    "source_photo_id": source_photo_id or None,
                    "image_key": image_key or None,
                    "text_content": text_content or None,
                    "signed_url": self._sign_gcs_read_url(image_key) if image_key else None,
                }
            )
        return stickers

    def _fetch_gcs_sticker_report_items(self, analysis_id: str) -> List[Dict]:
        bucket = self._env_value("GCS_BUCKET", "ppotto-bucket-dev")
        prefix = f"stickers/{analysis_id}/"
        result = subprocess.run(
            ["gcloud", "storage", "ls", f"gs://{bucket}/{prefix}"],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if result.returncode != 0 or not result.stdout.strip():
            return []

        stickers = []
        for line in result.stdout.strip().splitlines():
            object_key = line.strip().removeprefix(f"gs://{bucket}/")
            filename = object_key.rsplit("/", 1)[-1]
            stickers.append(
                {
                    "sticker_id": None,
                    "type": "IMAGE",
                    "title": filename,
                    "summary": "",
                    "source_photo_id": self._source_photo_id_from_sticker_key(filename),
                    "image_key": object_key,
                    "text_content": None,
                    "signed_url": self._sign_gcs_read_url(object_key),
                }
            )
        return stickers

    def _source_photo_id_from_sticker_key(self, filename: str) -> str:
        name = filename.removesuffix(".png")
        return name.split("-", 1)[1] if "-" in name else ""

    def _fetch_sticker_by_id(self, sticker_id: str) -> Dict:
        cmd = f"""docker exec ppotto-postgres psql -U {self.db_user} -d {self.db_name} -A -t -F '|' -c "
            SELECT id, type, title, summary, source_photo_id, image_key, text_content
            FROM stickers
            WHERE id = '{sticker_id}' AND deleted_at IS NULL
        " 2>/dev/null"""
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        if result.returncode != 0 or not result.stdout.strip():
            return {}

        sticker_id, sticker_type, title, summary, source_photo_id, image_key, text_content = (
            result.stdout.strip().splitlines()[0].split("|", 6)
        )
        return {
            "sticker_id": sticker_id,
            "type": sticker_type,
            "title": title,
            "summary": summary,
            "source_photo_id": source_photo_id or None,
            "image_key": image_key or None,
            "text_content": text_content or None,
            "signed_url": self._sign_gcs_read_url(image_key) if image_key else None,
        }

    def _select_sticker_for_regeneration(self) -> Dict:
        stickers = [
            sticker
            for sticker in self.test_results.get("stickers", [])
            if sticker.get("type") == "IMAGE" and sticker.get("sticker_id")
        ]
        if not stickers:
            return {}
        if not self.theme_query:
            return stickers[0]

        query = self.theme_query.casefold()
        for sticker in stickers:
            if query in (sticker.get("title") or "").casefold():
                return sticker

        for theme in self.test_results.get("themes", []):
            if query not in (theme.get("theme") or "").casefold():
                continue
            image_key = theme.get("sticker_image_key")
            source_photo_id = theme.get("source_photo_id")
            for sticker in stickers:
                if image_key and sticker.get("image_key") == image_key:
                    return sticker
                if source_photo_id and sticker.get("source_photo_id") == source_photo_id:
                    return sticker

        return {}

    def _regeneration_candidates(self) -> List[Dict]:
        return [
            {
                "sticker_id": sticker.get("sticker_id"),
                "title": sticker.get("title"),
                "image_key": sticker.get("image_key"),
                "source_photo_id": sticker.get("source_photo_id"),
            }
            for sticker in self.test_results.get("stickers", [])
            if sticker.get("type") == "IMAGE"
        ]

    def write_report_for_analysis(self, analysis_id: str) -> None:
        self.test_results = {"analysis_id": analysis_id, "status": "report-only"}
        self._load_analysis_owner(analysis_id)
        self.test_results["photos"] = self._fetch_photo_report_items(analysis_id)
        self.test_results["stickers"] = self._fetch_sticker_report_items(analysis_id)
        self.test_results["analysis_timing"] = self._fetch_analysis_timing(analysis_id)
        self.test_results["models"] = self._model_report_items()
        self.test_results["themes"] = self._fetch_theme_report_items(analysis_id)
        self.test_results["uploaded_photos"] = len(self.test_results["photos"])
        self._write_report()

    def _load_analysis_owner(self, analysis_id: str) -> None:
        cmd = f"""docker exec ppotto-postgres psql -U {self.db_user} -d {self.db_name} -A -t -F '|' -c "
            SELECT user_id, board_id
            FROM analysis
            WHERE id = '{analysis_id}'
        " 2>/dev/null"""
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        if result.returncode != 0 or not result.stdout.strip():
            return

        self.user_id, self.board_id = result.stdout.strip().splitlines()[0].split("|")
        self.access_token = self._issue_access_token(self.user_id)
        self.test_results["user_id"] = self.user_id
        self.test_results["board_id"] = self.board_id

    def _model_report_items(self) -> List[Dict]:
        return [
            {
                "step": "Theme classification and recap",
                "model": "gemini-2.5-flash",
            },
            {
                "step": "Sticker image generation",
                "model": "gemini-2.5-flash-image",
            },
        ]

    def _fetch_theme_report_items(self, analysis_id: str) -> List[Dict]:
        db_themes = self._fetch_theme_report_items_from_db(analysis_id)
        if db_themes:
            return db_themes
        return self._build_theme_report_items_from_stickers()

    def _fetch_theme_report_items_from_db(self, analysis_id: str) -> List[Dict]:
        cmd = f"""docker exec ppotto-postgres psql -U {self.db_user} -d {self.db_name} -A -t -F '|' -c "
            SELECT s.title,
                   s.source_photo_id,
                   s.image_key,
                   COALESCE(string_agg(DISTINCT sp.photo_id::text, ','), ''),
                   COALESCE(string_agg(DISTINCT rc.content, E'\\n'), '')
            FROM stickers s
            LEFT JOIN sticker_photos sp ON sp.sticker_id = s.id
            LEFT JOIN recap_comments rc ON rc.sticker_id = s.id
            WHERE s.analysis_id = '{analysis_id}' AND s.deleted_at IS NULL
            GROUP BY s.id, s.title, s.source_photo_id, s.image_key, s.z_index
            ORDER BY s.z_index, s.created_at
        " 2>/dev/null"""
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        if result.returncode != 0 or not result.stdout.strip():
            return []

        themes = []
        for line in result.stdout.strip().splitlines():
            title, source_photo_id, image_key, photo_ids, comments = line.split("|")
            themes.append(
                {
                    "theme": title,
                    "source_photo_id": source_photo_id or None,
                    "sticker_image_key": image_key or None,
                    "categorized_photo_ids": [photo_id for photo_id in photo_ids.split(",") if photo_id],
                    "recap": comments,
                    "note": "",
                }
            )
        return themes

    def _build_theme_report_items_from_stickers(self) -> List[Dict]:
        themes = []
        for index, sticker in enumerate(self.test_results.get("stickers", []), start=1):
            themes.append(
                {
                    "theme": sticker.get("title") or f"Theme {index}",
                    "source_photo_id": sticker.get("source_photo_id"),
                    "sticker_image_key": sticker.get("image_key"),
                    "categorized_photo_ids": [],
                    "recap": "",
                    "note": "테마 상세가 DB에 저장되지 않아 생성된 스티커 기준으로 표시합니다.",
                }
            )
        return themes

    def _fetch_analysis_timing(self, analysis_id: str) -> Dict:
        cmd = f"""docker exec ppotto-postgres psql -U {self.db_user} -d {self.db_name} -A -t -F '|' -c "
            SELECT started_at, completed_at,
                   EXTRACT(EPOCH FROM (completed_at - started_at))
            FROM analysis
            WHERE id = '{analysis_id}'
        " 2>/dev/null"""
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        if result.returncode != 0 or not result.stdout.strip():
            return {}
        started_at, completed_at, elapsed_seconds = result.stdout.strip().splitlines()[0].split("|")
        return {
            "started_at": started_at or None,
            "completed_at": completed_at or None,
            "pipeline_elapsed_seconds": float(elapsed_seconds) if elapsed_seconds else None,
        }

    def _fetch_photo_report_items(self, analysis_id: str) -> List[Dict]:
        cmd = f"""docker exec ppotto-postgres psql -U {self.db_user} -d {self.db_name} -A -t -F '|' -c "
            SELECT id, content_type
            FROM photos
            WHERE analysis_id = '{analysis_id}'
            ORDER BY created_at
        " 2>/dev/null"""
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        if result.returncode != 0 or not result.stdout.strip():
            return []

        photos = []
        for index, line in enumerate(result.stdout.strip().splitlines(), start=1):
            photo_id, content_type = line.split("|")
            object_key = self._photo_object_key(analysis_id, photo_id, content_type)
            photos.append(
                {
                    "photo_id": photo_id,
                    "filename": f"photo-{index}",
                    "content_type": content_type,
                    "object_key": object_key,
                    "signed_url": self._sign_gcs_read_url(object_key),
                }
            )
        return photos

    def _photo_object_key(self, analysis_id: str, photo_id: str, content_type: str) -> str:
        extensions = {
            "image/jpeg": "jpg",
            "image/png": "png",
            "image/heic": "heic",
        }
        return f"photos/{analysis_id}/{photo_id}.{extensions[content_type]}"

    def _sign_gcs_read_url(self, object_key: str, expiration_seconds: int = 86400) -> str:
        credentials_path = self._env_value("GCS_CREDENTIALS_PATH", "./secrets/gcs-service-account.json")
        bucket = self._env_value("GCS_BUCKET", "ppotto-bucket-dev")
        with open(credentials_path) as f:
            credentials = json.load(f)

        now = datetime.now(timezone.utc)
        datestamp = now.strftime("%Y%m%d")
        timestamp = now.strftime("%Y%m%dT%H%M%SZ")
        escaped_object = quote(object_key, safe="/")
        credential_scope = f"{datestamp}/auto/storage/goog4_request"
        credential = f"{credentials['client_email']}/{credential_scope}"
        query_params = {
            "X-Goog-Algorithm": "GOOG4-RSA-SHA256",
            "X-Goog-Credential": credential,
            "X-Goog-Date": timestamp,
            "X-Goog-Expires": str(expiration_seconds),
            "X-Goog-SignedHeaders": "host",
        }
        canonical_query = "&".join(
            f"{quote(key, safe='')}={quote(value, safe='')}"
            for key, value in sorted(query_params.items())
        )
        canonical_uri = f"/{bucket}/{escaped_object}"
        canonical_request = "\n".join(
            [
                "GET",
                canonical_uri,
                canonical_query,
                "host:storage.googleapis.com\n",
                "host",
                "UNSIGNED-PAYLOAD",
            ]
        )
        string_to_sign = "\n".join(
            [
                "GOOG4-RSA-SHA256",
                timestamp,
                credential_scope,
                hashlib.sha256(canonical_request.encode()).hexdigest(),
            ]
        )
        signature = self._rsa_sha256_hex(credentials["private_key"], string_to_sign)
        return f"https://storage.googleapis.com/{bucket}/{escaped_object}?{canonical_query}&X-Goog-Signature={signature}"

    def _rsa_sha256_hex(self, private_key: str, string_to_sign: str) -> str:
        with tempfile.NamedTemporaryFile("w", delete=False) as key_file:
            key_file.write(private_key)
            key_path = key_file.name
        try:
            result = subprocess.run(
                ["openssl", "dgst", "-sha256", "-sign", key_path],
                input=string_to_sign.encode(),
                capture_output=True,
                check=True,
            )
            return result.stdout.hex()
        finally:
            os.unlink(key_path)

    def _env_value(self, key: str, default: str) -> str:
        if os.getenv(key):
            return os.getenv(key)
        try:
            with open(".env") as f:
                for line in f:
                    if line.startswith(f"{key}="):
                        return line.strip().split("=", 1)[1]
        except FileNotFoundError:
            pass
        return default

    def _write_report(self) -> None:
        if not self.test_results:
            return
        self.test_results["report_generated_at"] = datetime.now(timezone.utc).isoformat()
        os.makedirs(self.report_dir, exist_ok=True)
        with open(self.report_html_path, "w") as f:
            f.write(self._render_html_report())
        logger.info(f"HTML 보고서: {self.report_html_path}")

    def _render_html_report(self) -> str:
        photos = self.test_results.get("photos", [])
        stickers = self.test_results.get("stickers", [])
        models = self.test_results.get("models", [])
        themes = self.test_results.get("themes", [])
        regeneration = self.test_results.get("regeneration")
        timing = self.test_results.get("analysis_timing", {})
        pipeline_elapsed = timing.get("pipeline_elapsed_seconds")
        pipeline_elapsed_text = f"{pipeline_elapsed:.1f}s" if pipeline_elapsed is not None else "-"
        model_items = "\n".join(
            f"""
            <tr>
              <td>{html.escape(model['step'])}</td>
              <td><code>{html.escape(model['model'])}</code></td>
            </tr>
            """
            for model in models
        )
        theme_items = "\n".join(
            f"""
            <article class="theme">
              <h3>{html.escape(theme['theme'])}</h3>
              <dl>
                <dt>Source Photo</dt>
                <dd>{html.escape(str(theme.get('source_photo_id') or '-'))}</dd>
                <dt>Sticker Object</dt>
                <dd>{html.escape(str(theme.get('sticker_image_key') or '-'))}</dd>
                <dt>Categorized Photos</dt>
                <dd>{html.escape(str(len(theme.get('categorized_photo_ids') or [])))} photos</dd>
                <dt>Photo IDs</dt>
                <dd>{html.escape(', '.join(theme.get('categorized_photo_ids') or []) or '-')}</dd>
                <dt>Recap</dt>
                <dd>{html.escape(str(theme.get('recap') or '-'))}</dd>
              </dl>
              {f"<p>{html.escape(theme['note'])}</p>" if theme.get('note') else ""}
            </article>
            """
            for theme in themes
        )
        photo_items = "\n".join(
            f"""
            <figure>
              <a href="{html.escape(photo['signed_url'])}" target="_blank" rel="noreferrer">
                <img src="{html.escape(photo['signed_url'])}" alt="{html.escape(photo['filename'])}">
              </a>
              <figcaption>{html.escape(photo['filename'])}<br>{html.escape(photo['photo_id'])}</figcaption>
            </figure>
            """
            for photo in photos
        )
        sticker_items = "\n".join(
            f"""
            <figure>
              {self._render_sticker_media(sticker)}
              <figcaption>{html.escape(sticker['title'])}<br>{html.escape(str(sticker.get('sticker_id') or sticker.get('image_key') or ''))}</figcaption>
            </figure>
            """
            for sticker in stickers
        )
        regeneration_section = self._render_regeneration_section(regeneration)
        return f"""<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <title>Ppotto E2E Test Report</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 24px; color: #1f2937; }}
    h1, h2 {{ margin: 0 0 16px; }}
    section {{ margin-top: 32px; }}
    .summary {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }}
    .summary div {{ border: 1px solid #d1d5db; border-radius: 8px; padding: 12px; }}
    table {{ width: 100%; border-collapse: collapse; }}
    th, td {{ border: 1px solid #d1d5db; padding: 10px; text-align: left; vertical-align: top; }}
    th {{ background: #f9fafb; }}
    code {{ font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }}
    .themes {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; }}
    .theme {{ border: 1px solid #d1d5db; border-radius: 8px; padding: 14px; }}
    .theme h3 {{ margin: 0 0 12px; }}
    dl {{ margin: 0; display: grid; grid-template-columns: 120px 1fr; gap: 8px 12px; }}
    dt {{ font-weight: 700; color: #4b5563; }}
    dd {{ margin: 0; word-break: break-all; }}
    .theme p {{ margin: 12px 0 0; color: #6b7280; }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 16px; }}
    figure {{ margin: 0; border: 1px solid #d1d5db; border-radius: 8px; padding: 8px; }}
    img {{ width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 6px; background: #f3f4f6; }}
    figcaption {{ margin-top: 8px; font-size: 12px; line-height: 1.4; word-break: break-all; }}
    a {{ color: #2563eb; }}
  </style>
</head>
<body>
  <h1>Ppotto E2E Test Report</h1>
  <div class="summary">
    <div>Status<br><strong>{html.escape(str(self.test_results.get('status', 'unknown')))}</strong></div>
    <div>Analysis ID<br><strong>{html.escape(str(self.test_results.get('analysis_id', '')))}</strong></div>
    <div>Gemini Pipeline Time<br><strong>{html.escape(pipeline_elapsed_text)}</strong></div>
    <div>Uploaded Photos<br><strong>{html.escape(str(self.test_results.get('uploaded_photos', 0)))}</strong></div>
    <div>Stickers<br><strong>{len(stickers)}</strong></div>
  </div>
  <section>
    <h2>Models</h2>
    <table>
      <thead><tr><th>Step</th><th>Model</th></tr></thead>
      <tbody>{model_items}</tbody>
    </table>
  </section>
  <section>
    <h2>Theme Classification</h2>
    <div class="themes">{theme_items}</div>
  </section>
  {regeneration_section}
  <section>
    <h2>Stickers</h2>
    <div class="grid">{sticker_items}</div>
  </section>
  <section>
    <h2>Uploaded Photos</h2>
    <div class="grid">{photo_items}</div>
  </section>
</body>
</html>
"""

    def _render_sticker_media(self, sticker: Dict) -> str:
        if sticker.get("signed_url"):
            url = html.escape(sticker["signed_url"])
            return f'<a href="{url}" target="_blank" rel="noreferrer"><img src="{url}" alt="{html.escape(sticker["title"])}"></a>'
        return f"<p>{html.escape(sticker.get('text_content') or '')}</p>"

    def _render_regeneration_section(self, regeneration: Dict) -> str:
        if not regeneration:
            return ""

        before = regeneration.get("selected_before") or {}
        after = regeneration.get("selected_after") or {}
        candidates = regeneration.get("candidates") or []
        request_seconds = regeneration.get("request_seconds")
        request_seconds_text = f"{request_seconds:.1f}s" if request_seconds is not None else "-"
        candidate_items = "\n".join(
            f"""
            <tr>
              <td>{html.escape(str(candidate.get('title') or '-'))}</td>
              <td>{html.escape(str(candidate.get('sticker_id') or '-'))}</td>
              <td>{html.escape(str(candidate.get('image_key') or '-'))}</td>
            </tr>
            """
            for candidate in candidates
        )
        return f"""
  <section>
    <h2>Sticker Regeneration</h2>
    <div class="summary">
      <div>Status<br><strong>{html.escape(str(regeneration.get('status') or '-'))}</strong></div>
      <div>Theme Query<br><strong>{html.escape(str(regeneration.get('theme_query') or '-'))}</strong></div>
      <div>Request Time<br><strong>{html.escape(request_seconds_text)}</strong></div>
      <div>Error<br><strong>{html.escape(str(regeneration.get('error') or '-'))}</strong></div>
    </div>
    <table>
      <thead><tr><th>Field</th><th>Before</th><th>After</th></tr></thead>
      <tbody>
        <tr><td>Sticker ID</td><td>{html.escape(str(before.get('sticker_id') or '-'))}</td><td>{html.escape(str(after.get('sticker_id') or '-'))}</td></tr>
        <tr><td>Title</td><td>{html.escape(str(before.get('title') or '-'))}</td><td>{html.escape(str(after.get('title') or '-'))}</td></tr>
        <tr><td>Summary</td><td>{html.escape(str(before.get('summary') or '-'))}</td><td>{html.escape(str(after.get('summary') or '-'))}</td></tr>
        <tr><td>Source Photo</td><td>{html.escape(str(before.get('source_photo_id') or '-'))}</td><td>{html.escape(str(after.get('source_photo_id') or '-'))}</td></tr>
        <tr><td>Image Key</td><td>{html.escape(str(before.get('image_key') or '-'))}</td><td>{html.escape(str(after.get('image_key') or '-'))}</td></tr>
      </tbody>
    </table>
    <h3>Regeneration Candidates</h3>
    <table>
      <thead><tr><th>Title</th><th>Sticker ID</th><th>Image Key</th></tr></thead>
      <tbody>{candidate_items}</tbody>
    </table>
  </section>
"""


def main():
    parser = argparse.ArgumentParser(description="뽀또 사진 분석 파이프라인 E2E 테스트")

    parser.add_argument('--api-url', default='http://localhost:8080', help='API URL')
    parser.add_argument('--db-host', default='localhost', help='DB 호스트')
    parser.add_argument('--db-port', type=int, default=54782, help='DB 포트')
    parser.add_argument('--photos-dir', help='사진 디렉토리')
    parser.add_argument('--photos-count', type=int, default=90, help='테스트 사진 개수')
    parser.add_argument('--max-workers', type=int, default=10, help='병렬 워커 수')
    parser.add_argument('--report-analysis-id', help='기존 analysisId로 보고서만 생성')
    parser.add_argument('--theme-query', help='재생성할 테마 또는 스티커 제목 검색어')
    parser.add_argument('--regenerate-theme', action='store_true', help='분석 후 특정 테마의 스티커를 재생성')

    args = parser.parse_args()

    if args.photos_count > 100:
        logger.error("❌ 사진 개수는 100개 이하여야 합니다.")
        return 1

    test = PhotosPipelineE2ETest(
        api_url=args.api_url,
        db_host=args.db_host,
        db_port=args.db_port,
        photos_dir=args.photos_dir,
        photos_count=args.photos_count,
        max_workers=args.max_workers,
        theme_query=args.theme_query,
        regenerate_theme=args.regenerate_theme,
    )

    if args.report_analysis_id:
        test.write_report_for_analysis(args.report_analysis_id)
        if args.regenerate_theme:
            success = test._regenerate_theme_sticker(args.report_analysis_id)
            test.test_results["status"] = "success" if success else "failure"
            test._write_report()
            return 0 if success else 1
        return 0

    success = test.run()
    return 0 if success else 1


if __name__ == '__main__':
    sys.exit(main())
