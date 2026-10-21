# PUBG Mortar AutoPhoto v0.3 — verification patch

## 왜 기존 APK에서 카메라가 열리지 않았나요?

v0.2 ZIP의 `app/src/main/assets/maps/`에는 `.gitkeep`만 있었습니다. 지도는 APK 빌드 이전에 `tools/prepare_offline_assets.py`를 실행해서 만들어야 했는데, **기존 `Android CI` 워크플로는 이 단계를 실행하지 않았습니다.** v0.2 앱은 지도 누락과 OpenCV 초기화 실패를 같은 오류문구로 표시했기 때문에 정확한 원인을 보여주지도 않았습니다.

## v0.3 수정사항

- Android 앱 시작 시 **기준 지도 개수 / 역사적 고도 데이터 개수 / OpenCV 초기화 결과**를 각각 표시합니다.
- 소스에 실물 지도가 없다면 Gradle `:app:assembleDebug`가 실패하도록 `verifyOfflineMaps`를 연결했습니다. 이제 **지도 없는 APK를 성공으로 내보내지 않습니다.**
- GitHub Actions `.github/workflows/android.yml`은 지도 다운로드 → 데이터 파일검사 → Gradle 빌드 → **최종 APK 압축파일 검사(OpenCV 네이티브 .so 포함)** 순서로 실행합니다.
- 공식 지도의 구버전 에란겔 파일명 `Erangel_Main`을 폴백으로 인식하지만 현재 게임 지도와 다를 수 있어 화면에 구버전임을 표시합니다.
- 앱 안에서 사용자가 지도 또는 고도 파일을 가져오는 UI는 없습니다.

## 중요한 한계

이 ZIP에는 **PUBG 실제 지도 JPEG/고도 HMAP/OpenCV 네이티브 바이너리가 들어 있지 않습니다.** 지도는 GitHub Actions 빌드 도중 **PUBG 공식 API 리포지토리에서 다운로드하여 APK에 내장**하게 되어 있습니다. OpenCV는 `org.opencv:opencv:4.11.0` Gradle 의존성을 통해 APK에 포함됩니다.

과거 고도 데이터는 `cgcostume/pubg-maps`의 PC v5.7.6 소스에서 선택적으로 시도합니다. 일부 또는 전체 맵에서 **고도 파일이 전혀 없을 수 있습니다**. 파일이 있더라도 좌표 정합, 높이 단위, 현재 맵과의 일치가 검증되지 않았습니다. 최종 APK 검사는 **파일 포함 여부**만 검증하며 고도 측정 정확도를 보증하지 않습니다. 촬영 사진에서 완전 자동으로 내 위치와 목표 핑을 식별하는 알고리즘도 아직 실사용 검증 전입니다.

## GitHub에서 새 APK 제작

1. 기존 GitHub 저장소 파일을 v0.3 ZIP 안의 `PUBG_Mortar_AutoPhoto_V3` 폴더 **내용물**로 교체합니다. GitHub 최상위에 `settings.gradle`, `app/`, `tools/`, `.github/workflows/android.yml`이 있도록 배치합니다.
2. 기존 `.github/workflows` 안에 있는 **오래된 `build-apk.yml` / `android.yml`은 삭제/교체**하여 작업이 중복 실행되지 않도록 합니다. 남은 워크플로는 `PUBG Android CI (verified offline maps)` 하나여야 합니다.
3. GitHub → Actions → `PUBG Android CI (verified offline maps)` → `Run workflow` 실행.
4. 로그에서 `Validate source assets BEFORE building`가 **지도 N개** 확인, `Validate final APK contains maps and OpenCV`가 **지도 N개, OpenCV lib/...**를 표시해야 합니다.
5. 성공 후 Artifacts → `PUBG-Mortar-Checked-APK` 설치.

`Prepare bundled official PUBG map images ...` 단계에서 GitHub API 한도/네트워크 실패 시 APK 빌드를 중단합니다. 고도맵 다운로드 오류는 경고하고 거리 계산용 APK만 제작할 수 있습니다. **고도 파일 0개여도 빌드가 성공할 수 있습니다**. 이 동작은 고도 계산이 된다고 잘못 표시하는 것보다 안전하지만, 처음 요청하신 완성형 고도 포함 앱에 아직 미달합니다.

실제로 사용할 APK의 파일 여부와 기기에서의 OpenCV 로딩 성공은 해당 Actions 실행 로그 및 앱 첫 화면의 세부 진단으로 확인해 주세요.
