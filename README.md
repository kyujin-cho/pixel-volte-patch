# Pixel IMS: Tensor Pixel VoLTE 활성화

English version available [here](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/README.en.md).

## 트러블슛팅

[이곳](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/troubleshooting.md)을 참조하세요.

## 개요

이 문서에서는 Android 내부 API 중 `telephony.ICarrierConfigLoader.overrideConfig()` API를 이용하여 루팅 혹은 부트로더 변조 없이 VoLTE (IMS) 기능을 활성화 하는 법에 대해 설명합니다.

## 지원 통신사

### 1차 지원

즉시 테스트가 가능하여 가/부 여부를 바로 확인할 수 있는 통신사입니다.

- LG U+ (대한민국)

### 2차 지원

테스트가 불가능하지만 커뮤니티에 의해 가능함이 확인된 통신사입니다. 목록은 [링크](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/compatibility-chart.md) 를 참고하세요.

## 적용 방법

### 준비물

- Google Tensor Chipset이 적용되었으며 Android 11 이상이 설치된 Pixel 단말기
  - Google Pixel 6
  - Google Pixel 6a
  - Google Pixel 6 Pro
  - Google Pixel 7
  - Google Pixel 7a
  - Google Pixel 7 Pro
  - Google Pixel 8
  - Google Pixel 8 Pro
  - Google Pixel Fold
- [Android Platform Tools](https://developer.android.com/studio/command-line/adb) 이 설치된 Windows, macOS 혹은 Linux 컴퓨터
- 데이터 통신이 가능한 USB-A to USB-C 혹은 USB-C to USB-C 케이블

### Shizuku 설치

[Shizuku](https://shizuku.rikka.app/) 는 ADB 혹은 루트 권한으로 동작하는 서비스를 통하여 일반적인 경로로는 접근할 수 없는 시스템 API를 호출할 수 있도록 하는 서비스입니다. 이 방법을 사용하기 위해서는 시스템 API의 호출이 필요합니다.

1. VoLTE 패치를 적용할 Pixel 단말기의 Google Play Store 를 실행한 후 [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) 어플리케이션을 설치합니다.
   ![image-1](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035249.png)
2. 설치한 Shizuku 어플리케이션을 실행합니다.
   ![image-2](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035312.png)
3. Pixel 단말기와 컴퓨터 간 ADB 통신이 가능한 상태로 준비 후 Pixel 단말기와 컴퓨터를 연결합니다. ADB 통신이 가능한 상태로 준비하는 방법에 대해서는 [Shizuku 문서 (영문)](https://shizuku.rikka.app/guide/setup/#start-by-connecting-to-a-computer) 을 참고하세요.
4. 다음 명령어를 입력하여 Shizuku 서비스를 실행합니다.  
   `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
   ![image-3](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot%202023-02-06%20at%203.54.00%20AM.png)
5. Shizuku 어플리케이션의 화면에 다음과 같은 문구가 표시되는 것을 확인합니다.
   ```
   Shizuku is running
   Version <임의의 버전 번호>, adb
   ```
   ![image-4](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035351.png)
6. 이제 케이블을 연결한 채로 다음 단계로 이동합니다.

### Pixel IMS 어플리케이션 설치

1. 현재 다음 두 가지 방법으로 Pixel IMS 앱을 설치할 수 있습니다.
   - [Github Releases](https://github.com/kyujin-cho/pixel-volte-patch/releases/download/1.2.8/dev.bluehouse.enablevolte.apk) 에서 APK 다운로드 후 설치
   - [Play Store](https://play.google.com/store/apps/details?id=dev.bluehouse.enablevolte) 에서 다운로드
2. 설치한 어플리케이션을 실행합니다.
3. 다음과 같이 Shizuku 권한을 묻는 팝업 창이 뜰 경우 "모든 경우에 허용" 을 선택합니다.
   ![image-5](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230208-235239.png)
4. VoLTE를 활성화 할 SIM의 페이지로 이동합니다. "Enable VoLTE" 토글을 활성화합니다.
   ![image-6](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230208-234343.png)
6. VoLTE가 작동하는 것을 확인할 때 까지 5분 간격으로 2-3회 Pixel 기기를 다시 시작합니다.

### APK 직접 빌드
[패치된 android.jar](https://github.com/Reginer/aosp-android-jar/raw/main/android-33/android.jar) 파일을 다운로드 후에 `$ANDROID_PATH/sdk/platforms/android-33` 경로 아래에 붙여넣습니다. 이후에 앱을 빌드 및 시작합니다.

## 자주 묻는 질문

### 추가적인 질문, 건의 사항, 버그 제보 등이 있습니다.

이 패치에 대해 문의할 사항이 있으시면 다음 기능을 활용해 주세요. 목적을 구분하지 않은 게시글 작성의 경우 삭제될 수 있습니다.

- 버그 제보, 기능 추가 요청: [Issues](https://github.com/kyujin-cho/pixel-volte-patch)
- 그 외의 모든 것: [Discussions](https://github.com/kyujin-cho/pixel-volte-patch/discussions)

### U+ 이외의 다른 통신사를 사용하는 경우에도 VoLTE 패치가 가능한가요?

아니오. 지원 대상은 LG U+ 및 U+ 통신망을 사용하는 MVNO (알뜰폰) 으로 한정됩니다.

### VoLTE가 적용되었는지 확인 가능한 방법이 있나요?

어플리케이션의 Home 페이지에서 `IMS Status` 항목이 `Registered` 이면 VoLTE가 성공적으로 활성화 된 것입니다.
![image-7](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230208-234340.png)

더욱 상세한 정보가 필요할 경우, Pixel 단말기에 내장 제공되는 통신 정보 확인용 내부 어플리케이션을 이용할 수 있습니다.

1. Pixel 단말기의 기본 전화 어플리케이션을 실행합니다.
   ![image-8](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035705.png)
2. 키패드에서 `*#*#4636#*#*` 키를 차례대로 입력합니다.
   ![image-9](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035701.png)
3. "Phone information" 항목을 터치합니다.
   ![image-10](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035650.png)
4. 우측 상단의 삼점 메뉴를 터치 후 "IMS Service Status" 항목을 터치합니다.
   ![image-11](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-030524.png)
5. 다음과 같은 문구가 표시된다면 VoLTE가 활성화 된 것입니다.  
   `IMS Registration: Registered`
   ![image-12](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035645.png)

### 해당 패치는 재부팅 시 마다 다시 실행하여야 하나요?

아니오.

### 해당 패치는 시스템 업데이트 시 마다 다시 실행하여야 하나요?

네.

### 해당 패치의 작동 원리가 어떻게 되나요?

Android에서 VoLTE (IMS) 가 활성화 되기 위해서는 `ImsManager.isVolteEnabledByPlatform(Context)` 메서드가 true를 반환해야 합니다. 해당 메서드의 구현을 살펴보면 다음과 같습니다 (ref: [googlesource.com](https://android.googlesource.com/platform/frameworks/opt/net/ims/+/002b204/src/java/com/android/ims/ImsManager.java)).

1. `persist.dbg.volte_avail_ovr` System Property가 true인지 확인 (기존의 setprop을 이용한 VoLTE 패치 방식)
   - 그럴 경우 true 반환
   - 아닐 경우 계속
2. 기기 자체에서 VoLTE 기능을 지원하는지 확인
   - 아닐 경우 false 반환
   - 그럴 경우 계속
3. 통신사에서 VoLTE 기능을 지원하는지 확인
   - 아닐 경우 false 반환
   - 그럴 경우 계속
4. 통신사에서 IMS 활성화를 위해 GBA capable SIM을 요구하는지 확인
   - 아닐 경우 true 반환
   - 그럴 경우 계속
5. EF IST에 GBA bit이 활성화 되어 있는지 확인
   - 그럴 경우 true 반환
   - 아닐 경우 false 반환

대한민국에서 Tensor Chip을 탑재한 Pixel로 LG U+를 사용하려는 경우, 기기에서는 VoLTE를 지원하지만 통신사에서 자체 설정을 프로비전하지 않아 3번 "통신사에서 VoLTE 기능을 지원하는지 확인" 이 false로 처리되어 기기에서 IMS가 비활성화됩니다. LG U+의 경우에는 Pixel에 내장된 VoLTE 기능을 사용할 수 있지만 통신사의 추가적인 설정이 없어 VoLTE가 비활성화 되는 것이므로, 이 어플리케이션은 위에서 언급한 Shizuku와 `CarrierConfigLoader`의 설정 강제 활성화 API를 조합하여 해당 설정을 강제로 true로 변경하여 시스템에서 VoLTE 활성화를 시도할 수 있도록 처리합니다.
