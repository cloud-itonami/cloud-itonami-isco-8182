(ns boilercoord.store
  "SSoT for the ISCO-08 8182 Steam Engine and Boiler Operators
  boiler-plant scheduling/logistics coordination actor (itonami actor
  pattern, ADR-2607121000 / CLAUDE.md Actors section; README's
  'Robotics premise' — a boiler-plant scheduling/logistics
  coordination robot proposes crew/shift scheduling, pressure-log/
  maintenance-record/progress record logging, safety-concern flags
  and administrative/spare-parts supply order coordination under this
  advisor/governor pair, which never dispatches hardware itself, never
  operates boiler/steam equipment, and never finalizes a boiler-
  operation-execution decision, a plant-safety-clearance decision, or
  overrides a plant safety officer's judgment). Modeled on
  cloud-itonami-isco-8131's chemcoord.store.

  Domain:

    plant    — a registered steam-engine/boiler plant or production
               line under coordination (:plant-id, :name, :location).
    operator — a registered certified steam-engine/boiler operator
               {:operator-id :plant-id :name :role}, belonging to
               exactly one registered plant (the plant currently
               assigned to this operator for this operation).
    record   — a committed operating record (a logged pressure-log/
               maintenance-record/progress record, scheduling
               proposal, safety-concern flag or supply-order
               coordination entry) — written ONLY via commit-record!.
               This actor coordinates boiler-plant scheduling/
               logistics ONLY — a `record` is a coordination
               artifact, never a boiler-operation-execution decision,
               a plant-safety-clearance decision, or a plant safety
               officer's-judgment override.
    ledger   — append-only audit trail, commit or hold.")

(defprotocol Store
  (plant [s plant-id])
  (operator [s operator-id])
  (records-of [s plant-id])
  (ledger [s])
  (register-plant! [s p])
  (register-operator! [s o])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (plant [_ plant-id] (get-in @a [:plants plant-id]))
  (operator [_ operator-id] (get-in @a [:operators operator-id]))
  (records-of [_ plant-id] (filter #(= plant-id (:plant-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-plant! [s p]
    (swap! a assoc-in [:plants (:plant-id p)] p) s)
  (register-operator! [s o]
    (swap! a assoc-in [:operators (:operator-id o)] o) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:plants {} :operators {} :records [] :ledger []}
                                   seed)))))
