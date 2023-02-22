# 통신사 별 호환성 정리

저의 경험 및 Discussions / 타 채널을 통해 보고된 해당 패치의 통신사별 적용 여부에 대해 정리합니다.

## 면책 조항

이 차트에서의 호환성은 실제 동작을 보증하지 **않습니다**. 작동 여부는 통신사 혹은 구글의 변경 사항에 따라 얼마든지 변경될 수 있으며, 그런 경우에 원 제작자는 일체의 작동을 보증할 수 없습니다. 이 패치는 대체 수단일 뿐입니다. VoLTE, Wi-Fi Calling 및 기타 앱에서 제공하는 다른 기능의 정식 호환 여부는 통신사 혹은 구글에게 문의하세요.  
이 차트에 통신사가 등재되어 있지 않다고 하여 패치가 작동하지 않는 것은 아닙니다. 만약 목록에 등재된 통신사 외의 통신사에서 작동이 확인될 경우 Discussions -> Carrier-specific Experiences에 글을 남겨 주시거나 PR을 작성하여 이 문서를 업데이트 해 주세요.

| 대륙   | 국가 (MCC)       | 이동통신사명 (MNC) | VoLTE 작동 여부 | Wi-Fi Calling 작동 여부    | 비고                                                                                                                 |
| ------ | ---------------- | ------------------ | --------------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| 아시아 | 대한민국 (450)   | SKT (05)           | X               | 통신사에서 서비스하지 않음 | OMD를 `OMD 기타LTE핸드셋_VOLTE` 혹은 `OMD Default5G` 로 등록할 경우 IMS는 활성화되나 불안정한 상태                   |
| 아시아 | 대한민국 (450)   | LG U+ (06)         | O               | 통신사에서 서비스하지 않음 | OMD 등록을 해제하여야 작동                                                                                           |
| 아시아 | 대한민국 (450)   | KT (08)            | X               | 통신사에서 서비스하지 않음 |                                                                                                                      |
| 아시아 | 방글라데시 (470) | Grameenphone (01)  | O               | ?                          | [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-4940003)                 |
| 북중미 | 멕시코 (334)     | Movistar (03)      | O               | ?                          | [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-5014817)                 |
| 북중미 | 멕시코 (334)     | Newww (140)        | O               | ?                          | 기기 등록 필요, [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-4988569) |
| 유럽   | 불가리아 (220)   | Yettel (01)        | O               | 통신사에서 서비스하지 않음 | [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-5012767)                 |
| 유럽   | 폴란드 (260)     | Orange PL (03)     | O               | O                          | [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/issues/17)                                               |
| 유럽   | 폴란드 (260)     | Plus (01)          | O               | O                          | [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/issues/17)                                               |
| 유럽   | 폴란드 (260)     | Play (06)          | O               | O                          | APN을 ims로 설정 필요, [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/issues/17)                        |
| 유럽   | 루마니아 (226)   | Vodafone (01)      | O               | O                          | [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/discussions/6)                                           |
| 유럽   | 루마니아 (226)   | Orange RO (10)     | O               | O                          | [링크 참조](https://github.com/kyujin-cho/pixel-volte-patch/discussions/6)                                           |
