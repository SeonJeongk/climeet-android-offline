# 루트 조회 캐시 측정 결과

암장 프로필 루트 탭에 재진입하는 흐름을 캐시 적용 전후 빌드에서 조건별로 50회씩 측정했습니다.

| 조건 | 요청 수 | 표시 시간 P50 | 표시 시간 P95 |
| :--- | :---: | :---: | :---: |
| TTL 안 | 3 → **0** | 622 → **510ms** (−18.0%) | 721 → **614ms** |
| TTL 지남 | 3 → **1** (304, 본문 0B) | — | — |

적용 전 요청 3회는 버전 조회 1회와 루트 페이지 조회 2회입니다. 페이지가 늘면 요청도 `1 + 페이지 수`회로 늘어납니다.

## 측정 기준

- 기기는 Galaxy S20 5G(Android 13) 한 대입니다. 서버는 측정용 합성 데이터를 고정해서 반환합니다.
- 적용 전은 `develop`의 [`f7c17f1e`](https://github.com/SeonJeongk/climeet-android-offline/commit/f7c17f1e5c249ac8d439b6f5825b612a84acba86), 적용 후는 [`e216eb93`](https://github.com/SeonJeongk/climeet-android-offline/commit/e216eb9345b8d13f9fcfbf9dde5b6db394ee299a)과 같은 코드로 만든 benchmark 빌드입니다.
- 조건마다 warm-up 5회 뒤에 50회를 측정했습니다. 백분위는 nearest-rank로 계산했습니다.
- 표시 시간은 루트 탭 진입을 요청한 시점부터 루트 목록이 기대한 상태로 처음 보이고 다음 조작이 가능해진 시점까지입니다.
- 날짜·버전·필터·전체 목록이 기대와 다르면 빠르게 표시돼도 실패로 처리하고 시간 집계에서 뺐습니다.
- TTL이 지난 조건은 응답에 3초 고정 지연을 넣었기 때문에 요청 수만 확인하고 표시 시간은 쓰지 않습니다.

## 한계

- 측정은 제가 담당했던 암장 프로필 루트 탭에서 진행했습니다.
- 기기 한 대와 합성 데이터에서 얻은 값입니다. 실제 사용자 환경의 대기 시간으로 일반화하지 않습니다.

## 공개 범위

| 파일 | 내용 |
| --- | --- |
| [`results/route-cache-runs.csv`](results/route-cache-runs.csv) | 적용 전후 빌드의 재진입 두 조건, 회차별 200행 |
| [`results/route-cache-summary.json`](results/route-cache-summary.json) | 조건별 P50·P95, 요청 수, 정확성 통과율 |

JSON의 조건별 집계(`groups`)는 공개 CSV에서 다시 만들 수 있습니다.

```bash
python3 tools/measurement/aggregate_evidence.py \
  docs/offline-first/route-cache/results/route-cache-runs.csv \
  --output summary.json
```
