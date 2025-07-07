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

ktlint는 baseline 없이 전체 코드를 검사합니다. 생성자 주입은 `class Example @Inject constructor(` 형태를 유지합니다.
짧은 대입식과 체인의 첫 호출은 같은 줄에 유지합니다. annotation, multiline-expression-wrapping, chain-method-continuation 규칙은 끄고 120자 제한과 기본 들여쓰기는 유지합니다.
기존 detekt 위반은 baseline에 동결되어 있습니다. 일반 기능 개발에서는 baseline 생성 task와 전체 `ktlintFormat`을 실행하지 않습니다.
