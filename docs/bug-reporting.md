# 버그 신고 가이드

English version available [here](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/bug-reporting.en.md).

이 문서는 Pixel IMS의 크래시, ANR("앱이 응답하지 않음"), 기타 버그를 조사할 수 있도록 신고하는 방법을 설명합니다. 신고는 [버그 신고 양식](https://github.com/kyujin-cho/pixel-volte-patch/issues/new?template=bug_report.yml)으로 작성합니다.

> [!WARNING]
> **로그에는 IMSI, ICCID, IMEI, 전화번호가 포함될 수 있습니다.** GitHub 이슈는 공개됩니다. 파일을 첨부하기 전에 [개인정보 항목](#개인정보-게시-전에-식별자를-지우세요)을 반드시 읽어 주세요.

## 신고하기 전에

- [트러블슈팅 문서](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/troubleshooting.md)와 [README](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/README.md)의 자주 묻는 질문을 먼저 확인하세요.
- 지원 목록에 없는 기기나 통신사에 대한 요청은 이슈가 아닌 [Discussions](https://github.com/kyujin-cho/pixel-volte-patch/discussions)에 남겨 주세요.
- 같은 문제가 이미 신고되었는지 기존 이슈를 검색해 주세요.

## 포함할 정보

버그 신고 양식에서 아래 정보를 묻습니다. 미리 준비해 주세요.

- **기기 모델** (예: Pixel 8 Pro)
- **Android 버전**과 **보안 패치 수준**: 설정 > 휴대전화 정보 > Android 버전
- **Pixel IMS 버전**: 앱 홈 화면 맨 위에 표시됩니다. **Google Play**와 **GitHub Releases APK** 중 어디에서 설치했는지도 알려 주세요.
- 관련된 각 SIM의 **통신사와 국가**, 그리고 **SIM 슬롯** (SIM 1 / SIM 2, 물리 SIM 또는 eSIM)
- Pixel IMS에서 **변경한 토글** (예: VoLTE 활성화, VoNR 활성화, 전문가 모드에서 바꾼 값)
- **재현 방법**, **기대한 동작**, 그리고 **실제로 일어난 일**
- **로그**: 앱에서 내보낸 보고서 zip 또는 직접 수집한 로그 (아래 참고)

## 앱에서 보고서 내보내기 (크래시 또는 ANR)

Pixel IMS가 크래시되었거나 응답하지 않았다면, 다음 실행 시 홈 화면에 카드가 표시됩니다.

1. **보고서 보내기**를 누릅니다. (저장된 보고서는 언제든 **홈 > 진단 > 크래시 보고서**에서도 볼 수 있습니다.)
2. 선택 사항: **시스템 로그 포함**을 켜면 logcat (main, system, radio 버퍼)과 Pixel IMS에 대한 시스템의 ANR 및 크래시 기록이 추가됩니다. Shizuku가 실행 중이고 권한이 허용되어 있어야 하며, 그렇지 않으면 스위치가 비활성화됩니다. radio 로그에 가입자 식별자가 들어 있을 가능성이 가장 높기 때문에 기본값은 꺼져 있습니다.
3. **미리보기 생성**을 누릅니다. 마스킹이 적용된 뒤 zip에 들어갈 모든 파일이 화면에 표시됩니다. 마스킹은 완벽하지 않으므로 직접 읽어 확인해 주세요.
4. **저장**을 눌러 zip을 저장합니다 (예: 다운로드 폴더). **공유**를 누르면 다른 앱으로 zip을 보낼 수 있습니다.
5. **GitHub 이슈**를 누릅니다. 제목, 기기 모델, Android 버전, 보안 패치, 앱 버전, 통신사가 미리 채워진 버그 신고 양식이 열립니다. 나머지 항목을 채우고 저장한 zip을 **Logs and attachments** 칸에 끌어다 놓아 첨부합니다.

앱의 언어 설정에 따라 버튼 이름은 다르게 표시될 수 있습니다.

zip에는 다음 파일이 들어 있습니다.

- `environment.txt`: 종료 정보 (종류, 시각, 앱 버전), 기기 모델, Android 버전, 보안 패치, 빌드, 베이스밴드, 앱 버전, 설치 경로, SIM별 통신사 이름 / MCC-MNC / 국가. 구독 ID, ICCID, 전화번호는 포함되지 않습니다.
- 종료 추적 정보: `anr-trace.txt` (ANR) 또는 `java-crash.txt` (크래시). 네이티브 크래시의 `tombstone.pb`는 **네이티브 크래시 덤프 포함**을 켠 경우에만 추가됩니다.
- **시스템 로그 포함**을 켠 경우 `system/*.txt`

IMSI, ICCID, IMEI, 전화번호는 `[IMSI]`, `[ICCID]`, `[IMEI]`, `[PHONE]`, `[ID]`, `[NUMBER]`로 가려집니다. `tombstone.pb`는 바이너리 파일이라 마스킹하거나 미리 볼 수 없으므로 기본값은 꺼져 있습니다.

보고서는 **크래시 보고서** 화면에서 하나씩 또는 한꺼번에 삭제할 수 있습니다.

## adb로 로그 직접 수집하기

크래시나 ANR이 아닌 문제 (예: VoLTE 등록이 되지 않음), 앱 내 내보내기가 없는 이전 버전, 또는 앱을 열 수 없는 경우에 사용합니다.

1. **개발자 옵션**을 켜고 (설정 > 휴대전화 정보 > 빌드 번호를 일곱 번 누름) **USB 디버깅** (또는 **무선 디버깅**)을 켭니다. 컴퓨터에 [platform-tools](https://developer.android.com/tools/releases/platform-tools)를 설치하고 `adb devices`에 휴대전화가 표시되는지 확인합니다.
2. 선택 사항: 재현한 내용만 남도록 기존 로그를 지웁니다: `adb logcat -c`
3. 문제를 재현합니다.
4. 재현 직후 로그를 저장합니다.

   ```sh
   adb logcat -d -b main,system,radio > log.txt
   ```

   전체 시스템 상태가 필요하면 대신 `adb bugreport bugreport.zip`을 사용합니다 (용량이 크고 식별자가 많이 들어 있습니다).

확인할 위치:

- **Java 크래시**: `log.txt`에서 `AndroidRuntime` 태그의 `FATAL EXCEPTION` 줄과 그 뒤의 스택 트레이스
- **ANR**: `log.txt`에서 `ActivityManager`의 `ANR in dev.bluehouse.enablevolte` 줄. bugreport에서는 `bugreport-*.txt` 파일의 **VM TRACES AT LAST ANR** 항목과 `FS/data/anr/` 아래에 스레드 덤프가 있습니다.
- **네이티브 크래시**: `log.txt`에서 `DEBUG` 또는 `libc` 태그의 `*** *** ***`, `Fatal signal` 줄. bugreport에서는 `FS/data/tombstones/` 아래에 tombstone이 있습니다.
- 시스템은 최근의 크래시와 ANR 기록도 보관합니다: `adb shell dumpsys dropbox --print data_app_crash`, `adb shell dumpsys dropbox --print data_app_anr` (다른 앱의 기록도 포함되므로 Pixel IMS 항목만 남겨 주세요.)

파일을 이슈에 첨부하거나, 관련된 부분만 **Logs and attachments** 칸에 붙여 넣어 주세요.

## IMS 상태 확인하기

VoLTE, VoWiFi, VoNR 문제를 신고할 때는 IMS 상태를 함께 알려 주세요.

1. 전화 앱에서 `*#*#4636#*#*`을 입력합니다.
2. **휴대전화 정보** (Phone information)를 열고 SIM을 선택합니다.
3. 오른쪽 위 메뉴에서 **IMS 서비스 상태** (IMS Service Status)를 선택합니다. IMS 등록, Voice over LTE, Voice over WiFi, Video Telephony 항목의 상태를 적어 주세요.

Pixel IMS 홈 화면에서도 SIM별 IMS 등록 상태를 확인할 수 있습니다.

## 개인정보: 게시 전에 식별자를 지우세요

로그, bugreport, 스크린샷에는 다음 정보가 포함될 수 있습니다.

- **IMSI** (가입자 식별 번호, 보통 통신사의 MCC/MNC로 시작하는 15자리 숫자)
- **ICCID** (SIM 카드 일련번호, 보통 `89`로 시작)
- **IMEI** (기기 식별 번호, 15자리)
- **전화번호** (`sip:+8210...@...` 같은 SIP 주소 안에 들어 있는 경우 포함)

이슈에 올린 내용은 모두 공개되며 기록에 남습니다. 첨부하기 전에 다음을 지켜 주세요.

- 앱에서 내보낸 보고서는 이 식별자들을 최대한 가리지만 완벽하지 않습니다. 미리보기를 꼭 확인하세요.
- `adb`로 수집한 로그는 **마스킹되지 않습니다**. 본인의 값 (설정 > 휴대전화 정보에서 IMEI와 전화번호를, SIM 상태에서 추가 정보를 확인할 수 있습니다)을 파일에서 검색해 `[REDACTED]`로 바꾸고, 긴 숫자열도 검색해 확인하세요.
- `adb bugreport` 전체를 공개적으로 올리지 마세요. 관련된 부분만 첨부해 주세요.
- 식별자를 올린 것을 발견하면 이슈나 댓글을 수정해 지우고 첨부 파일을 삭제하세요.
