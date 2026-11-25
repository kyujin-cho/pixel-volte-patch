# 버그 제보 가이드

English version available [here](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/bug-reporting.en.md).

Issues는 새로운 기능 요청 또는 앱을 사용하기 어려울 정도의 심각한 버그를 논의하는 곳입니다. 지원 대상이 아닌 기기나 통신사에서 기능이 켜지지 않는 문제는 사전 안내 없이 Discussions로 이동될 수 있습니다. 제보하기 전에 [트러블슈팅 문서](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/troubleshooting.md)를 먼저 확인해 주세요.

## 반드시 포함해 주세요

- 기기 모델 (예: Pixel 8 Pro)
- Android 버전 및 보안 패치 수준 (설정 -> 휴대전화 정보)
- Pixel IMS 앱 버전
- 통신사와 국가
- SIM 슬롯 (SIM 1 / SIM 2 / eSIM)
- 변경한 토글 (VoLTE, VoWiFi, VoNR, 화상 통화 등)
- 재현 절차, 기대 동작, 실제 동작

## 로그 수집

### 수동 수집 (앱 내 내보내기 기능을 사용할 수 없는 경우)

문제를 재현한 직후에 PC에서 아래 명령을 실행합니다.

```bash
adb bugreport bugreport.zip
```

또는 로그캣 버퍼를 직접 덤프합니다.

```bash
adb logcat -d -b main,system,radio > log.txt
```

`adb bugreport`는 ZIP 파일을 생성하며 ANR 추적(`FS/data/anr/`), 네이티브 크래시 기록(`FS/data/tombstones/`), 로그캣 덤프 등을 포함합니다. 로그캣 덤프에서 앱 크래시 스택 트레이스는 `main` 버퍼(`AndroidRuntime`, `FATAL EXCEPTION`), ANR 정보는 `system` 버퍼(`ActivityManager`, `ANR in ...`), 통신 및 IMS 메시지는 `radio` 버퍼에서 찾을 수 있습니다.

### IMS 등록 상태 확인

다이얼러에서 `*#*#4636#*#*` -> `Phone information` -> `IMS Service Status` 로 이동해 등록 상태를 확인하고, 가능하면 화면을 캡처해 함께 첨부해 주세요.

### 앱 내 리포트 내보내기

리포트 내보내기 및 공유 기능이 제공되는 버전에서는 앱 홈 화면에서 리포트를 내보내 ZIP 파일을 만든 뒤 이슈에 첨부해 주세요.

## 첨부 방법

수집한 `bugreport` ZIP 또는 `log.txt`를 이슈에 직접 첨부하거나, [Gist](https://gist.github.com/)에 올린 뒤 링크를 남겨 주세요. 텍스트로 붙여넣는 경우 코드 블록으로 감싸 주세요.

## 개인정보 보호 경고

로그에는 IMSI, ICCID, IMEI, 전화번호 등 가입자 식별 정보가 포함될 수 있습니다. 공개된 이슈에 올리기 전에 이러한 값을 반드시 가리거나 삭제해 주세요. (예: `01012345678` -> `010XXXXXXXX`)

## 이슈 작성

[새 이슈](https://github.com/kyujin-cho/pixel-volte-patch/issues/new/choose)에서 버그 리포트 양식을 선택하고 위 정보를 채워 주세요.
