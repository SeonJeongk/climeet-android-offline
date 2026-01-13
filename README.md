# Climeet Android Offline

클밋은 클라이밍 암장의 루트를 조회하고 운동을 기록하는 Android 앱입니다. 이 앱을 오프라인 우선 구조로 개선하고 있습니다.

> 2024년 [팀 프로젝트](https://github.com/TheClimeet/climeet-android)의 기획과 화면을 바탕으로, 개인 작업으로 오프라인 캐시를 도입한 저장소입니다.

## 루트 조회 캐시

암장 프로필, 캘린더 기록, 실시간 기록, 쇼츠 섹터 선택의 네 화면이 같은 루트 API를 각자 호출해서, 이미 조회한 루트도 재진입할 때마다 서버 응답을 기다렸고 변경이 없는 데이터도 매번 다시 받았습니다. 루트 조회를 Room 기반 Repository 하나로 통합하고, 재진입하면 캐시된 카탈로그를 먼저 표시하도록 바꿨습니다.

| 요청 수 · TTL 안 | 요청 수 · TTL 지남 | 표시 시간 P50 |
| :---: | :---: | :---: |
| **3회 → 0회** | **3회 → 1회** | **622ms → 510ms** |
| 서버 요청 없이 표시 | 304 응답, 본문 0B | −18.0% |

수치는 암장 프로필 루트 탭 재진입 기준이며, 캐시 적용 전후 빌드에서 조건별로 50회씩 측정했습니다. 측정 기준과 회차별 결과는 [측정 결과 문서](docs/offline-first/route-cache/README.md)에 있습니다.

## 캐시 설계

화면은 Room에 캐시된 카탈로그만 읽고, 서버 통신은 Repository가 맡습니다. 캐시 유효 시간(TTL)은 서버 응답의 `Cache-Control: max-age`를 따르며, 값이 없으면 15분, 최대 24시간입니다. TTL이 지나도 재검증 요청 하나로 끝나기 때문에, 기본값을 짧게 두어 루트 수정이 빨리 반영되도록 했습니다. 상한은 서버가 긴 `max-age`를 보내도 하루에 한 번은 재검증하도록 둔 값입니다.

| 상황 | 동작 |
| :--- | :--- |
| TTL 안에 재진입 | 요청 없이 캐시된 카탈로그 표시 |
| TTL이 지난 뒤 재진입 | 캐시된 카탈로그를 먼저 보여 주고 ETag로 재검증, 변경이 없으면 304(본문 없음) |
| 서버 데이터가 바뀜 | 모든 층과 페이지를 받은 뒤 한 transaction으로 교체 |
| 오프라인·갱신 실패 | 캐시된 카탈로그 유지, 없을 때만 오류 표시 |
| 여러 화면이 같은 카탈로그 요청 | 진행 중인 갱신 하나로 합침 |
| 캐시 20MB 초과 | 오래 쓰지 않은 카탈로그부터 삭제, 보고 있는 카탈로그는 제외 |

카탈로그는 암장과 날짜 단위로 캐시합니다. 요청한 날짜가 캐시에 없으면 서버에서 받고, 오프라인이면 다른 날짜로 대신하지 않고 재시도 버튼과 함께 오류를 표시합니다. 홈의 내 암장 목록도 계정별로 캐시해서 오프라인에서도 홈에서 루트 탭까지 들어갈 수 있습니다.

<details>
<summary>저장 구조</summary>

루트 카탈로그는 metadata, 섹터, 난이도, 루트의 네 테이블에 나눠 저장합니다. metadata에는 ETag, 받은 시각, TTL과 테이블별 행 수를 기록합니다. 읽을 때 행 수가 metadata와 다르면 캐시가 없는 것으로 보고 다음 갱신에서 전체를 다시 받습니다.

</details>

## 테스트

| 확인 내용 | 테스트 |
| :--- | :--- |
| TTL 계산 | [`RouteCachePolicyTest`](app/src/test/java/com/climus/climeet/data/repository/route/RouteCachePolicyTest.kt) |
| 재검증, 오프라인, 갱신 실패 시 캐시 유지 | [`RouteCatalogRepositoryTest`](app/src/test/java/com/climus/climeet/data/repository/route/RouteCatalogRepositoryTest.kt) |
| 층·섹터·난이도 필터 | [`RouteFilterTest`](app/src/test/java/com/climus/climeet/presentation/ui/main/global/RouteFilterTest.kt) |
| 데이터 교체 시 선택 해제, 재시도 | [`GymProfileRouteViewModelTest`](app/src/test/java/com/climus/climeet/presentation/ui/main/global/gymprofile/route/GymProfileRouteViewModelTest.kt) |
| 카탈로그 교체와 무결성 검사 (계측 테스트) | [`RouteCacheDaoTest`](app/src/androidTest/java/com/climus/climeet/data/local/cache/RouteCacheDaoTest.kt) |
| 계정별 내 암장 저장 | [`HomeGymRepositoryTest`](app/src/test/java/com/climus/climeet/data/repository/homegym/HomeGymRepositoryTest.kt) |

## 코드 살펴보기

1. Repository와 캐시 정책: [`RouteCatalogRepositoryImpl`](app/src/main/java/com/climus/climeet/data/repository/route/RouteCatalogRepositoryImpl.kt), [`RouteCachePolicy`](app/src/main/java/com/climus/climeet/data/repository/route/RouteCachePolicy.kt)
2. Room 저장: [`RouteCatalogStore`](app/src/main/java/com/climus/climeet/data/repository/route/RouteCatalogStore.kt), [`RouteCacheDao`](app/src/main/java/com/climus/climeet/data/local/cache/RouteCacheDao.kt)
3. 화면 변환과 필터: [`RouteCatalogUi`](app/src/main/java/com/climus/climeet/presentation/ui/main/global/RouteCatalogUi.kt), [`RouteFilter`](app/src/main/java/com/climus/climeet/presentation/ui/main/global/RouteFilter.kt)
4. 측정용 benchmark receiver: [`RouteCacheControlReceiver`](app/src/benchmark/java/com/climus/climeet/benchmark/RouteCacheControlReceiver.kt)
