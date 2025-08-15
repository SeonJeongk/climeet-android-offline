# climeet-android

## 로컬 Kotlin 품질 검사

최초 한 번 Git hook을 설치합니다.

```bash
./scripts/install-git-hooks.sh
```

commit 전 hook은 전체 ktlint 검사만 실행합니다. 개발 완료와 review 전에는 detekt, unit test와 debug build를 함께 실행합니다.

```bash
./gradlew ktlintCheck
./gradlew :app:detekt :app:detektDebug
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
