# Compatibility matrix for known carriers

This documents describes compatibility status of supported devices per local carriers.

## Disclaimer

This chart DOES NOT GUARANTEE actual operation. Ability to make things work can change anytime by updates from Google or your carrier.
Carrier not being listed on the table does not mean that this patch won't work for your carrier. If you made it work from unlisted carriers, please create a new discussion at Discussions -> Carrier-specific Experiences section or request an update to this table by creating new Pull Request.

| Continent     | Country (MCC)           | Carrier (MNC)     | VoLTE Compatibility | Wi-Fi Calling (or VoWiFi) Compatibility | Notes                                                                                                                                        |
| ------------- | ----------------------- | ----------------- | ------------------- | --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Asia          | Bangladeshi (470)       | Grameenphone (01) | O                   | ?                                       | [Reference](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-4940003)                                         |
| Asia          | Republic of Korea (450) | SKT (05)          | X                   | Not operational                         | OMD를 `OMD 기타LTE핸드셋_VOLTE` 혹은 `OMD Default5G` 로 등록할 경우 IMS는 활성화되나 불안정한 상태                                           |
| Asia          | Republic of Korea (450) | LG U+ (06)        | O                   | Not operational                         | OMD 등록을 해제하여야 작동                                                                                                                   |
| Asia          | Republic of Korea (450) | KT (08)           | X                   | Not operational                         |                                                                                                                                              |
| North America | Mexico (334)            | Movistar (03)     | O                   | ?                                       | [Reference](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-5014817)                                         |
| North America | Mexico (334)            | Newww (140)       | O                   | ?                                       | Need to register device to the carrier. [Reference](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-4988569) |
| Europe        | Bulgaria (220)          | Yettel (01)       | O                   | Not operational                         | [Reference](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-5012767)                                         |
| Europe        | Poland (260)            | Orange PL (03)    | O                   | O                                       | [Reference](https://github.com/kyujin-cho/pixel-volte-patch/issues/17)                                                                       |
| Europe        | Poland (260)            | Plus (01)         | O                   | O                                       | [Reference](https://github.com/kyujin-cho/pixel-volte-patch/issues/17)                                                                       |
| Europe        | Poland (260)            | Play (06)         | O                   | O                                       | Need to set APN as `ims`. [Reference](https://github.com/kyujin-cho/pixel-volte-patch/issues/17)                                             |
| Europe        | Romania (226)           | Vodafone (01)     | O                   | O                                       | [Reference](https://github.com/kyujin-cho/pixel-volte-patch/discussions/6)                                                                   |
| Europe        | Romania (226)           | Orange RO (10)    | O                   | O                                       | [Reference](https://github.com/kyujin-cho/pixel-volte-patch/discussions/6)                                                                   |
| Oceania          | New Zealand (530)       | Spark NZ (05) | O                   | O                                       | [Reference](https://github.com/kyujin-cho/pixel-volte-patch/discussions/1#discussioncomment-4940003)      