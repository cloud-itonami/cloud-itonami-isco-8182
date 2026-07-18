(ns boilercoord.governor
  "BoilerCoordGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  boiler-plant scheduling/logistics coordination proposal an advisor
  may make for a steam-engine/boiler plant under coordination. The
  governor never dispatches hardware itself, never operates boiler/
  steam equipment, and never allows a proposal to finalize a boiler-
  operation-execution decision (authorizing a pressure change/valve
  operation to proceed), finalize a plant-safety-clearance decision,
  or override a plant safety officer's judgment — this actor
  coordinates BOILER-PLANT SCHEDULING/LOGISTICS ONLY. Modeled on
  cloud-itonami-isco-8131's chemcoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. plant provenance        — the operator/plant record must be
                                 independently verified/registered
                                 before any action.
    2. no-actuation            — proposal :effect must be :propose
                                 (the governor never dispatches
                                 hardware and never operates boiler/
                                 steam equipment; it only gates what
                                 the advisor may coordinate).
    3. closed op-allowlist     — :op must be one of the four
                                 coordination ops (:log-work-record,
                                 :schedule-crew-operation,
                                 :flag-safety-concern,
                                 :coordinate-supply-order). No op that
                                 directly finalizes a boiler-
                                 operation-execution decision,
                                 finalizes a plant-safety-clearance
                                 decision, or overrides plant-safety-
                                 officer authority exists in this
                                 allowlist — these decision classes
                                 are structurally absent, not merely
                                 gated. This actor never operates
                                 boiler/steam equipment itself;
                                 pressure-vessel/valve/steam-equipment
                                 handling is entirely out of scope for
                                 this administrative-coordination
                                 actor (:coordinate-supply-order
                                 covers plant-equipment/administrative/
                                 spare-parts procurement only, never
                                 pressure vessels, valves or steam
                                 equipment themselves).
    4. plant-mismatch          — if the proposal names a plant, it
                                 must be the SAME plant verified for
                                 this request (defense-in-depth against
                                 a proposal quietly targeting a
                                 different, unverified plant).
    5. operator basis          — if the proposal references an
                                 operator, that operator must be a
                                 REGISTERED certified steam-engine/
                                 boiler operator belonging to this
                                 plant (an unregistered or foreign-
                                 plant operator reference is not a
                                 routine scheduling proposal).
    6. scope-exclusion         — a proposal that attempts to finalize
                                 a boiler-operation-execution decision
                                 (authorizing a pressure change/valve
                                 operation to proceed), to finalize a
                                 plant-safety-clearance decision, or to
                                 override a plant safety officer's
                                 judgment, is a hard, PERMANENT block —
                                 never overridable by human approval,
                                 regardless of confidence or stake, and
                                 NEVER auto-commit-eligible under any
                                 confidence level. Detected as
                                 finalization/execution ACTION PHRASES
                                 (e.g. 'authorize the pressure-valve
                                 operation to proceed', 'finalize the
                                 boiler-operation decision', 'override
                                 the plant safety officer's judgment')
                                 in free-text proposal fields, never as
                                 bare domain nouns ('boiler', 'steam',
                                 'pressure', 'valve') — bare-noun
                                 matching would false-trip on the
                                 default mock advisor's own routine
                                 rationale text, since this actor's
                                 entire domain is boiler-plant
                                 scheduling coordination. See
                                 `boilercoord.governor-test`
                                 `default-mock-advisor-proposals-never-self-trip-scope-exclusion`.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off,
  regardless of confidence):
    7. :op :flag-safety-concern always escalates (a surfaced pressure-
                                 reading concern, safety-valve-
                                 condition concern or equipment-
                                 condition issue ALWAYS requires human
                                 review — the governor never resolves a
                                 safety concern itself, and this is
                                 unconditional — no confidence-level
                                 exception, ever).
    8. :op :coordinate-supply-order with :cost above
                                 `supply-order-cost-threshold` always
                                 escalates.
    9. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [boilercoord.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold 20000)

(def ^:private allowed-ops
  #{:log-work-record :schedule-crew-operation :flag-safety-concern
    :coordinate-supply-order})

(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Scope-exclusion is matched as finalization/execution ACTION
;; PHRASES, never as bare nouns ("boiler", "steam", "pressure",
;; "valve") — this actor's entire domain is boiler-plant scheduling
;; coordination, so bare-noun matching would false-trip on the
;; default mock advisor's own routine rationale text (e.g. "proposed
;; :coordinate-supply-order for plant BP-1" naming plant equipment and
;; spare-parts signage, or a crew-schedule proposal naming a boiler
;; unit under maintenance). See governor-test's dedicated self-trip
;; guard.
(def ^:private scope-exclusion-phrases
  ["authorize the pressure-valve operation to proceed"
   "authorize the pressure valve operation to proceed"
   "authorize the boiler operation to proceed"
   "authorize the pressure change to proceed"
   "approve the pressure-valve operation to proceed"
   "approve the boiler operation to proceed"
   "approve the pressure change to proceed"
   "approve the valve operation to proceed"
   "clear the boiler for operation"
   "clear the plant for the boiler operation"
   "clear the plant for the pressure change"
   "finalize the boiler-operation decision"
   "finalize the boiler operation decision"
   "finalize the pressure-valve operation"
   "finalize the pressure valve operation"
   "finalize the boiler-operation-execution decision"
   "finalize the boiler operation execution decision"
   "finalize the plant-safety-clearance decision"
   "finalize the plant safety clearance"
   "finalize the plant-safety clearance decision"
   "execute the pressure change directly"
   "execute the valve operation directly"
   "perform the valve operation directly"
   "perform the pressure change directly"
   "initiate the pressure change directly"
   "initiate the valve operation directly"
   "trigger the valve operation directly"
   "start the boiler operation directly"
   "start the pressure change directly"
   "dispatch the crew to operate the boiler"
   "dispatch the operator to open the valve"
   "sign off the boiler-operation authorization"
   "sign the boiler-operation authorization"
   "sign the boiler operation authorization"
   "issue the boiler-operation authorization"
   "issue the plant-safety clearance"
   "issue the plant safety clearance"
   "override the plant safety officer's judgment"
   "override the plant safety officer"
   "bypass the plant safety officer"
   "bypass the safety clearance"
   "bypass the boiler-operation authorization"])

(defn- scope-excluded-text [proposal]
  (str/lower-case (str (:rationale proposal) " " (:description proposal))))

(defn scope-exclusion-violation?
  "true if any free-text field of `proposal` contains a
  finalization/execution action phrase attempting to finalize a
  boiler-operation-execution decision, finalize a plant-safety-
  clearance decision, or override plant-safety-officer authority.
  Phrased as multi-word action phrases (never bare nouns) so this
  never false-trips on legitimate boiler-plant-scheduling-
  coordination domain vocabulary."
  [proposal]
  (let [text (scope-excluded-text proposal)]
    (boolean (some #(str/includes? text %) scope-exclusion-phrases))))

(defn- hard-violations [{:keys [request proposal]} plant-record o]
  (let [{:keys [op plant-id operator-id]} proposal]
    (cond-> []
      (nil? plant-record)
      (conj {:rule :no-plant :detail "未登録 plant/boiler record"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は boiler-plant 判断を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op :detail "closed op-allowlist 外の op（boiler-operation-execution 決定の確定・plant-safety-clearance 決定の確定・plant safety officer の判断の上書きにあたる op は許可されていない）"})

      (and plant-id (not= plant-id (:plant-id request)))
      (conj {:rule :plant-mismatch :detail "proposal の plant が request で検証済みの plant と一致しない"})

      (and operator-id (nil? o))
      (conj {:rule :unknown-operator :detail "未登録 operator への提案は不可"})

      (and o (not= (:plant-id o) (:plant-id request)))
      (conj {:rule :operator-wrong-plant :detail "operator が別 plant 所属"})

      (scope-exclusion-violation? proposal)
      (conj {:rule :scope-exclusion-violation
             :detail "boiler-operation-execution 決定の確定・plant-safety-clearance 決定の確定・plant safety officer の判断の上書きにあたる提案は恒久的に禁止（human 承認でも上書き不可）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `boilercoord.store/Store`. Pure — never mutates
  the store, never dispatches a robot action, never operates boiler/
  steam equipment."
  [request _context proposal store]
  (let [plant-record (store/plant store (:plant-id request))
        o (some->> (:operator-id proposal) (store/operator store))
        hard (hard-violations {:request request :proposal proposal} plant-record o)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        cost (:cost proposal)
        over-threshold? (and (= :coordinate-supply-order (:op proposal))
                              (number? cost) (> cost supply-order-cost-threshold))
        always-risky? (or (contains? always-escalate-ops (:op proposal)) over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
