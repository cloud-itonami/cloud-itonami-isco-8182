# physai-isco-8182 — 蒸気機関・ボイラーオペレーター（ISCO 8182）のボイラー室を巡回するロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-8182`、ISCO 8182 蒸気機関・ボイラーオペレーター）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: ボイラープラントの段取り・物流調整ロボットが、圧力記録・保守記録・進捗の記録、班の勤務案、安全上の懸念の提起、予備品の補給調整を行う（ボイラーや蒸気設備は操作しない）。
その物理的な仕事（巡回で通る保温された蒸気配管の表面温度と、圧力を記録する給水ライン）を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:steam-line-lagging-surface` | thermal | 180 °C の蒸気配管（管壁 180 °C 固定）のロックウール保温の表面を、4 時間後に巡回で読む | 保温表面温度 | 50 °C（estimate） |
| `:feedwater-line` | pipe-flow | 約 105 °C の給水を脱気器から 50 mm・40 m、高低差 10 m のラインでボイラードラムへ送る | 圧力損失（高低差込み） | 250 kPa（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:physai-test`（`test-physai/boilercoord/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する。repo 自身の `test/` の .cljk も同じ runner で走り、計 28 test / 71 assertion）。

## 測って分かったこと・限界（成長の第一候補）

1. **保温表面**: 表面温度は保温厚で決まる（15 mm で 60.8 °C、20 mm で 53.5 °C、25 mm で 48.6 °C、50 mm で 37.8 °C、100 mm で 31.7 °C）。
   限界 50 °C 以下に収まる保温厚は **23.4 mm 以上**。薄い保温は短時間で定常に達し（15 mm で 118 s で 50 °C 超え）、50 mm 以上は 4 時間ではまだ温度上昇中。
   平板モデルなので、配管の曲率（外表面積の増加）の効果は入っていない。
2. **給水ライン**: 圧力損失は高低差 10 m 分（約 94 kPa）が大半で、流量で緩やかに増える（1 L/s で 95.8 kPa、3 L/s で 111.7 kPa、5 L/s で 142.8 kPa）。
   掃引範囲は全て限界内で、限界 250 kPa を超えるのは **8.98 L/s**。5 L/s の軸動力は 1190 W。
3. **estimate のままの値**: 保温表面温度の上限 50 °C（保温設計の規格・社内基準で置き換える）、給水ポンプの余裕 250 kPa（ポンプ性能曲線とドラム圧で置き換える）、
   ロックウールの熱伝導率 0.045 W/mK・密度・比熱、表面の熱伝達係数 10 W/m²K、配管の粗さ、ポンプ効率 0.60。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-8182 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:physai-test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-8182 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
