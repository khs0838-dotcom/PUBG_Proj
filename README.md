# PUBG Mortar AutoPhoto v0.4

## 현재 개발 변경: 저해상도 지도와 PDF 보정식

- 공식 `api-assets`의 `Low_Res` 지도 9종을 사용합니다. 게임 내부 식별자와 이미지 파일명은 별도로 매핑합니다. 예: `Desert_Main` → `Miramar_Main_Low_Res.png`. `Erangel_Main`이라는 파일명 자체가 구버전이라는 뜻은 아닙니다. 현재 패치와의 지형 일치는 별도 검증이 필요합니다.
- 고도가 있을 때 **보정 참고거리 = 수평거리 + |목표 고도 − 발사 고도| × 0.5**를 사용합니다. 첨부된 Battlemap.gg PDF의 경험식이며 실제 PUBG 탄도 모델이 아닙니다. 오르막·내리막의 보정값은 같으며, 부호 있는 고도차는 별도로 표시합니다.
- PDF 사례: 수평거리 457.9m, 고도차 ±16.1m → 465.95m → 화면 표시 466.0m. 결과가 700m를 넘으면 값을 잘라내지 않고 최대 설정거리 초과 안내를 표시합니다.
- 고도 데이터가 없으면 0으로 가정하지 않고 보정 참고거리 표시를 보류합니다. 현재 클라우드에서 준비한 9종 지도에는 고도 데이터가 없습니다.
- 저해상도 지도는 파일 용량을 줄이지만 확대된 지도나 휴대폰 촬영 사진의 정합 정확도는 실제 사진으로 검증해야 합니다. U-Net 도입과 촬영 인식 방식 변경은 이번 변경에 포함되지 않습니다.

검증 명령: `gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest`, `python tools/verify_assets.py app/src/main/assets`, `python tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk`.

## 왜 기존 APK에서 카메라가 열리지 않았나요?

v0.2 ZIP의 `app/src/main/assets/maps/`에는 `.gitkeep`만 있었습니다. 지도는 APK 빌드 이전에 `tools/prepare_offline_assets.py`를 실행해서 만들어야 했는데, **기존 `Android CI` 워크플로는 이 단계를 실행하지 않았습니다.** v0.2 앱은 지도 누락과 OpenCV 초기화 실패를 같은 오류문구로 표시했기 때문에 정확한 원인을 보여주지도 않았습니다.

## v0.3 수정사항

- Android 앱 시작 시 **기준 지도 개수 / 역사적 고도 데이터 개수 / OpenCV 초기화 결과**를 각각 표시합니다.
- 소스에 실물 지도가 없다면 Gradle `:app:assembleDebug`가 실패하도록 `verifyOfflineMaps`를 연결했습니다. 이제 **지도 없는 APK를 성공으로 내보내지 않습니다.**
- GitHub Actions `.github/workflows/android.yml`은 지도 다운로드 → 데이터 파일검사 → Gradle 빌드 → **최종 APK 압축파일 검사(OpenCV 네이티브 .so 포함)** 순서로 실행합니다.
- 공식 지도의 구버전 에란겔 파일명 `Erangel_Main`을 폴백으로 인식하지만 현재 게임 지도와 다를 수 있어 화면에 구버전임을 표시합니다.
- 앱 안에서 사용자가 지도 또는 고도 파일을 가져오는 UI는 없습니다.

## 중요한 한계

현재 저장소에는 공식 저해상도 지도 JPEG 9종과 catalog가 포함되어 있습니다. 고도 HMAP은 포함되어 있지 않습니다. GitHub Actions 빌드 시에는 **PUBG 공식 API 리포지토리에서 지도를 다시 다운로드하여 APK에 내장**합니다. OpenCV는 `org.opencv:opencv:4.11.0` Gradle 의존성을 통해 APK에 포함됩니다.

과거 고도 데이터는 `cgcostume/pubg-maps`의 PC v5.7.6 소스에서 선택적으로 시도합니다. 일부 또는 전체 맵에서 **고도 파일이 전혀 없을 수 있습니다**. 파일이 있더라도 좌표 정합, 높이 단위, 현재 맵과의 일치가 검증되지 않았습니다. 최종 APK 검사는 **파일 포함 여부**만 검증하며 고도 측정 정확도를 보증하지 않습니다. 촬영 사진에서 완전 자동으로 내 위치와 목표 핑을 식별하는 알고리즘도 아직 실사용 검증 전입니다.

## GitHub에서 새 APK 제작

1. 기존 GitHub 저장소 파일을 v0.3 ZIP 안의 `PUBG_Mortar_AutoPhoto_V3` 폴더 **내용물**로 교체합니다. GitHub 최상위에 `settings.gradle`, `app/`, `tools/`, `.github/workflows/android.yml`이 있도록 배치합니다.
2. 기존 `.github/workflows` 안에 있는 **오래된 `build-apk.yml` / `android.yml`은 삭제/교체**하여 작업이 중복 실행되지 않도록 합니다. 남은 워크플로는 `PUBG Android CI (verified offline maps)` 하나여야 합니다.
3. GitHub → Actions → `PUBG Android CI (verified offline maps)` → `Run workflow` 실행.
4. 로그에서 `Validate source assets BEFORE building`가 **지도 N개** 확인, `Validate final APK contains maps and OpenCV`가 **지도 N개, OpenCV lib/...**를 표시해야 합니다.
5. 성공 후 Artifacts → `PUBG-Mortar-Checked-APK` 설치.

`Prepare bundled official PUBG map images ...` 단계에서 GitHub API 한도/네트워크 실패 시 APK 빌드를 중단합니다. 고도맵 다운로드 오류는 경고하고 거리 계산용 APK만 제작할 수 있습니다. **고도 파일 0개여도 빌드가 성공할 수 있습니다**. 이 동작은 고도 계산이 된다고 잘못 표시하는 것보다 안전하지만, 처음 요청하신 완성형 고도 포함 앱에 아직 미달합니다.

실제로 사용할 APK의 파일 여부와 기기에서의 OpenCV 로딩 성공은 해당 Actions 실행 로그 및 앱 첫 화면의 세부 진단으로 확인해 주세요.

## v0.4 지도 정합 개선

지도 정합을 ORB에서 SIFT + FLANN + RANSAC으로 변경했습니다. 사진 특징은 한 번 추출해 지도 9종에서 공유하고, 기준 지도를 2배로 리샘플링해 확대 사진과의 스케일 차이를 줄입니다. 리샘플링은 원본에 없는 세부 정보를 복원하지는 않습니다. 일반 탐색에서 맵이 발견되지 않으면 4방향 회전·압축한 기준 지도로 비스듬한 촬영을 추가 탐색합니다. 이 경로는 일반 정합보다 느릴 수 있습니다.

중복 대응점을 제거하고, 양방향 투영 오차·내점 분포·뒤집힘/원근 변환·다른 맵과의 점수 차이를 확인해 잘못된 맵을 채택하지 않도록 합니다. 이 변경은 지도 정합 단계이며 마커 검출 모델을 변경하지 않습니다. 저해상도 지도의 정보 한계, 게임 패치 차이, 심한 반사·모아레·흔들림은 여전히 인식 실패 원인이 될 수 있습니다.

네이티브 회귀 검증은 전체 JDK 17 이상에서 `python tools/run_map_matcher_regression.py`로 실행합니다. 테스트 전용 데스크톱 OpenCV 4.9 JNI를 Maven Central에서 SHA-256 검증 후 캐시하고, Android 앱과 같은 `MapFeatureMatcher.java`를 직접 컴파일·실행합니다. APK는 기존 Android OpenCV 4.11을 계속 사용합니다. 에란겔 저해상도 지도에서 1/2/4/6배 확대, 원근 왜곡과 회전 입력을 합성하고, 투영 위치 오차와 다른 맵/단색 이미지 거부를 검사합니다. 실제 휴대폰 사진 인식률이나 기기 처리 시간의 측정 결과는 아닙니다.
