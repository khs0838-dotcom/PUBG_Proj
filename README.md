# PUBG 박격포 계산기 — Android 실험판

> **현재 제공물: 안드로이드 스튜디오 프로젝트 소스 / APK 미생성·미설치검증**
>
> 이 버전은 실제 PUBG 지도·고도 자료가 내장된 완제품이 아닙니다. 이용자가 **전체 기준 지도**와 (선택적으로) **정합·보정된 고도맵**을 별도로 가져와야 합니다. 이 제한을 숨기지 않습니다.

## 구현된 기능

- 카메라 앱을 통한 PC 모니터 지도 촬영 또는 기존 사진 불러오기
- OpenCV ORB + RANSAC 특징점 정합: 사진 속 확대·회전·원근 변화가 있을 때도 전체 기준 지도 좌표에 맞추도록 **시도**
- 지도 정합 성공 시 기준 지도의 발사점 및 목표점 좌표 변환
- 자동 정합 실패 시 **전체 기준 지도 직접 선택** 또는 **사진에서 100m 격자 두 점을 이용한 수동 보정**
- 화면 핀치 확대·이동, 발사점/목표점 지정
- 수평 거리, 북쪽 기준 방위각 계산
- (a) 직접 입력한 높이차 또는 (b) 단위 보정된 16비트 높이맵을 이용한 높이차 계산
- 기하학적 직선거리와 **비공식 포물선 모델의 실험적인 조준거리** 출력
- 보정되지 않은 고도맵만 있을 경우 조준 거리 계산을 **보류**하도록 설계
- 인터넷, 게임 프로세스 메모리 접근, 게임 화면 캡처 권한 없이 기본 기능 실행

## 아직 구현하지 않았거나 검증이 필요한 기능

- **APK 파일은 이 실행 환경에서 생성하지 못함** (Android SDK와 Gradle 실행 도구 부재)
- 자동 핑 검출, 완전한 무터치 실시간 카메라 분석, 다중 맵 자동 식별 미구현
- 2026 PUBG 패치에 맞춘 높이맵, 공식 박격포 탄도/고도 보정 상수 미확보
- 특정 카메라 촬영 각도와 모든 맵 확대 단계에서의 성공 보장 불가
- 사진의 100m 격자 수동 보정은 **사진상 원근 왜곡**을 따로 보정하지 않음
- 자체 원본 지도 자료의 사용/배포 가능 조건 확인 필요
- Play Store 등록, 앱 서명/실기기 테스트 미수행

## 사용법

### 1. 기준 지도 가져오기

앱을 열고 `1. 전체 기준 지도 가져오기`를 선택합니다. 게임 내 확대 화면 **전체를 포함하고 원근이 없는** 위에서 내려다본 기준 지도 PNG/JPG를 선택하세요. 원칙적으로 사진 속 지도와 해당 기준 지도는 지형 버전이 같아야 합니다.

공식 PUBG API 개발자 이미지 저장소: https://github.com/pubg/api-assets/tree/master/Assets/Maps

**지도 크기 설정:** 에란겔은 명목상 `8 × 8 km`이므로 기본 가로 길이를 `8000 m`로 입력했지만, 사용하는 이미지의 정확한 지형 사각형 범위·테두리 때문에 실제 축척을 별도로 확인해야 합니다. 정확한 미터 산출값을 원한다면 100m 격자로 검증하세요.

### 2. 사진 촬영 → 정합

`3. 모니터 지도 촬영하기` → `4. 사진과 전체 지도 자동 정합` 선택. 성공한 경우 내점 수가 표시됩니다. 자동 확대 배율 추정은 **이미지 특징점과 호모그래피에 의해 수행되며, 일부 지도/확대 상태에서는 실패**할 수 있습니다.

실패한다면 모니터 반사를 줄이고 지도 영역을 많이 포함한 사진을 찍거나, 전체 기준 지도에서 직접 발사점/목표점을 지정하세요. 대안으로 사진에서 100m 격자 너비에 해당하는 두 점을 선택하여 사진 픽셀 축척을 수동 보정할 수 있습니다.

### 3. 발사 위치와 목표 위치 선택

지도 표시 창 아래에서 `발사 위치 선택`을 누른 다음 사진/지도에서 시작 지점을 탭합니다. 이어 `목표 위치 선택`을 누른 후 목표 지점을 탭합니다.

### 4. 고도 보정

- **직접 고도차 입력:** 목표가 25m 더 높으면 `25`, 15m 더 낮으면 `-15`를 입력합니다. 이 입력이 고도맵보다 우선합니다.
- **고도맵 사용:** 전 지형을 덮는 16bit grayscale PNG를 아래 Python 유틸리티로 `.hmap` 파일로 변환하고, 앱의 `2. 16비트 고도맵 가져오기`로 로드합니다. `고도맵 raw 값 1당 미터값`을 반드시 해당 원본 자료의 보정값으로 설정하세요. *해당 보정값을 모른다면 빈칸으로 두세요. 높이를 알고 있는 것처럼 가장해 결과를 출력하지 않습니다.*

고도맵 준비 예시:

```powershell
python -m pip install numpy Pillow
python tools/prepare_heightmap.py erangel_height_16bit.png erangel.hmap --size 1024
```

고도맵 참고: https://github.com/cgcostume/pubg-maps/releases (구버전 공개 16비트 자료). 이것은 게임 내부 가공 자료이므로 사용 권리와 최신도 확인이 필요하며, **게시된 원본 PNG와 전술 지도 PNG의 지역 범위·회전·축척이 일치하지 않으면 높이 계산은 틀립니다**.

### 5. 거리/고도차/조준거리 계산

발사점 및 목표점의 수평 지도 거리 `L`, 방위각과 사용자 입력 또는 고도맵에서 산출한 높이차 `ΔH`를 사용합니다.

실험적 조준거리 공식 (`M=700m`):

```
t = ΔH / L
D = M*M - 2*L*M*t - L*L
R = (L + t * (M - sqrt(D))) / (1 + t*t)  , D>=0
```

이 공식은 커뮤니티 포물선 근사이며 PUBG 공식 수치가 아닙니다. 같은 높이이면 `R = L`이고, 목표가 더 높으면 대체로 `R > L`입니다. 산출값을 실제 박격포 착탄 위치와 비교해 교정하기 전에는 실전용으로 단정하지 마세요. `121~700m` 표시 역시 과거 공개 정보를 기준으로 한 참고값입니다.

## 데모 입력 자료 (실제 PUBG와 무관)

`demo/` 디렉터리의 다음 파일은 생성된 합성 자료입니다.

1. `demo_reference.png` → 기준 지도 선택
2. `demo_camera_photo.jpg` → 사진 선택, 이어 자동 정합
3. `demo_height.hmap` → 고도맵 선택
4. 기준 지도 가로 길이 `8000`, 고도 단위(raw당 m) `0.0078125` 입력
5. 사진에서 발사 위치와 목표 위치를 탭하면 높이차 포함 계산을 시험할 수 있습니다.

**이 보정 상수는 오직 합성 데모를 생성할 때 사용한 값으로 PUBG 실제 지도에는 적용하면 안 됩니다.**

## Windows에서 APK 빌드

### 방법 A: Android Studio

1. https://developer.android.com/studio 에서 Android Studio 설치
2. 압축 파일을 풀고 `PUBG_Mortar_Android` 폴더 열기
3. Android SDK 35 및 JDK 17 준비, Gradle 8.9 구성
4. Gradle 동기화 (OpenCV 4.11.0, AndroidX 라이브러리를 처음 다운로드하므로 인터넷 필요)
5. `Build > Build Bundle(s) / APK(s) > Build APK(s)` 실행
6. `app/build/outputs/apk/debug/app-debug.apk` 설치

> Gradle Wrapper 바이너리(`gradle-wrapper.jar`)는 포함되어 있지 않습니다. Gradle 8.9를 별도로 설치해 IDE의 Gradle 설정에서 사용하거나 다음 GitHub Actions 자동 빌드를 이용하세요.

### 방법 B: GitHub Actions (로컬 Android SDK 설치 불필요)

1. 압축을 풀어 새 개인 GitHub 저장소에 **폴더 내부의 전체 파일**을 올립니다.
2. `.github/workflows/build-apk.yml`이 포함되어 있는지 확인합니다.
3. GitHub 저장소 `Actions` → `Build Android debug APK` → `Run workflow` 클릭
4. 빌드 성공 후 해당 실행의 `Artifacts` 아래 `PUBG-Mortar-debug-APK`를 받습니다.
5. 압축을 풀면 `app-debug.apk`가 있습니다. 스마트폰에서 설치합니다.

이 자동 빌드 구성 역시 이 대화 환경에서 원격 실행해 보지는 못했습니다.

## 기술 사양

- Java 17 / Android API 26+ / Gradle Android plugin 8.7.3
- OpenCV Android Maven 4.11.0, AndroidX Core / ExifInterface
- Camera Intent + FileProvider (카메라 앱에 촬영 위임)
- ORB(최대 5000개 키포인트), Hamming nearest-neighbor matching, Lowe ratio 0.74, RANSAC homography
- 지도 포인트 클릭 좌표를 변환하고, 지도 가로 크기로 실제 수평 거리 계산
- HMAP 헤더: ASCII `HMAP`, little-endian `uint32 width`, `uint32 height`, 뒤이어 little-endian `uint16` 래스터

## 출처와 참고

- PUBG API 공식 개발자 자료: https://github.com/pubg/api-assets
- PUBG 구버전 고도맵 추출 프로젝트: https://github.com/cgcostume/pubg-maps
- OpenCV 공식 Android Java/Gradle 가이드: https://docs.opencv.org/4.11.0/d5/df8/tutorial_dev_with_OCV_on_Android.html
- 비공식 고도 보정 포물선 자료: https://github.com/Iamnotphage/MortarAid4PUBG

이 앱은 PUBG/KRAFTON의 공식 또는 후원 애플리케이션이 아닙니다.
