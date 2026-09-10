(ns school.store
  "SSoT for the school actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every prior `cloud-
  itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/school/store_contract_test.clj), which is the whole point: the
  actor, the Curriculum Safeguarding Governor and the audit ledger
  never know which SSoT they run on.

  Like `hospital.store`'s dual treatment/discharge history,
  `casework.store`'s dual eligibility/referral history and every
  other dual-actuation sibling before it, this actor has TWO actuation
  events (finalizing a promotion, finalizing a safeguarding record)
  acting on the SAME entity (a student), each with its OWN history
  collection, sequence counter and dedicated double-actuation-guard
  boolean (`:promoted?`/`:safeguarding-recorded?`, never a `:status`
  value) -- the same discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which student was
  screened for a cleared staff background check, which promotion was
  finalized, which safeguarding record was finalized, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a parent trusting a school needs,
  and the evidence an operator needs if a promotion or safeguarding
  record is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [school.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (student [s id])
  (all-students [s])
  (background-check-of [s student-id] "committed background-check screening verdict for a student, or nil")
  (assessment-of [s student-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (promotion-history [s] "the append-only promotion-finalization history (school.registry drafts)")
  (safeguarding-history [s] "the append-only safeguarding-record-finalization history (school.registry drafts)")
  (next-promotion-sequence [s jurisdiction] "next promotion-finalization-number sequence for a jurisdiction")
  (next-safeguarding-sequence [s jurisdiction] "next safeguarding-record-number sequence for a jurisdiction")
  (student-already-promoted? [s student-id] "has this student's promotion already been finalized?")
  (student-already-recorded? [s student-id] "has this student's safeguarding record already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-students [s students] "replace/seed the student directory (map id->student)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained student set covering both actuation
  lifecycles (finalizing a promotion, finalizing a safeguarding
  record) so the actor + tests run offline."
  []
  {:students
   {"student-1" {:id "student-1" :student-name "Sakura Tanaka"
                 :current-class-size 25 :maximum-class-size 30 :background-check-cleared? true
                 :promoted? false :safeguarding-recorded? false
                 :jurisdiction "JPN" :status :intake}
    "student-2" {:id "student-2" :student-name "Atlantis Doe"
                 :current-class-size 25 :maximum-class-size 30 :background-check-cleared? true
                 :promoted? false :safeguarding-recorded? false
                 :jurisdiction "ATL" :status :intake}
    "student-3" {:id "student-3" :student-name "鈴木一郎"
                 :current-class-size 32 :maximum-class-size 30 :background-check-cleared? true
                 :promoted? false :safeguarding-recorded? false
                 :jurisdiction "JPN" :status :intake}
    "student-4" {:id "student-4" :student-name "田中花子"
                 :current-class-size 25 :maximum-class-size 30 :background-check-cleared? false
                 :promoted? false :safeguarding-recorded? false
                 :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-promotion!
  "Backend-agnostic `:student/mark-promoted` -- looks up the student
  via the protocol and drafts the promotion-finalization record, and
  returns {:result .. :student-patch ..} for the caller to persist."
  [s student-id]
  (let [st (student s student-id)
        seq-n (next-promotion-sequence s (:jurisdiction st))
        result (registry/register-promotion-finalization student-id (:jurisdiction st) seq-n)]
    {:result result
     :student-patch {:promoted? true
                     :promotion-number (get result "promotion_number")}}))

(defn- finalize-safeguarding-record!
  "Backend-agnostic `:student/mark-recorded` -- looks up the student
  via the protocol and drafts the safeguarding-record-finalization
  record, and returns {:result .. :student-patch ..} for the caller to
  persist."
  [s student-id]
  (let [st (student s student-id)
        seq-n (next-safeguarding-sequence s (:jurisdiction st))
        result (registry/register-safeguarding-record-finalization student-id (:jurisdiction st) seq-n)]
    {:result result
     :student-patch {:safeguarding-recorded? true
                     :safeguarding-number (get result "safeguarding_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (student [_ id] (get-in @a [:students id]))
  (all-students [_] (sort-by :id (vals (:students @a))))
  (background-check-of [_ id] (get-in @a [:background-checks id]))
  (assessment-of [_ student-id] (get-in @a [:assessments student-id]))
  (ledger [_] (:ledger @a))
  (promotion-history [_] (:promotions @a))
  (safeguarding-history [_] (:safeguarding-records @a))
  (next-promotion-sequence [_ jurisdiction] (get-in @a [:promotion-sequences jurisdiction] 0))
  (next-safeguarding-sequence [_ jurisdiction] (get-in @a [:safeguarding-sequences jurisdiction] 0))
  (student-already-promoted? [_ student-id] (boolean (get-in @a [:students student-id :promoted?])))
  (student-already-recorded? [_ student-id] (boolean (get-in @a [:students student-id :safeguarding-recorded?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :student/upsert
      (swap! a update-in [:students (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :background-check/set
      (swap! a assoc-in [:background-checks (first path)] payload)

      :student/mark-promoted
      (let [student-id (first path)
            {:keys [result student-patch]} (finalize-promotion! s student-id)
            jurisdiction (:jurisdiction (student s student-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:promotion-sequences jurisdiction] (fnil inc 0))
                       (update-in [:students student-id] merge student-patch)
                       (update :promotions registry/append result))))
        result)

      :student/mark-recorded
      (let [student-id (first path)
            {:keys [result student-patch]} (finalize-safeguarding-record! s student-id)
            jurisdiction (:jurisdiction (student s student-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:safeguarding-sequences jurisdiction] (fnil inc 0))
                       (update-in [:students student-id] merge student-patch)
                       (update :safeguarding-records registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-students [s students] (when (seq students) (swap! a assoc :students students)) s))

(defn seed-db
  "A MemStore seeded with the demo student set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :background-checks {} :ledger [] :promotion-sequences {}
                           :promotions [] :safeguarding-sequences {} :safeguarding-records []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/background-check payloads, ledger
  facts, promotion/safeguarding records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:student/id                       {:db/unique :db.unique/identity}
   :assessment/student-id            {:db/unique :db.unique/identity}
   :background-check/student-id      {:db/unique :db.unique/identity}
   :ledger/seq                       {:db/unique :db.unique/identity}
   :promotion/seq                    {:db/unique :db.unique/identity}
   :safeguarding/seq                 {:db/unique :db.unique/identity}
   :promotion-sequence/jurisdiction  {:db/unique :db.unique/identity}
   :safeguarding-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- student->tx [{:keys [id student-name current-class-size maximum-class-size background-check-cleared?
                            promoted? safeguarding-recorded?
                            jurisdiction status promotion-number safeguarding-number]}]
  (cond-> {:student/id id}
    student-name                        (assoc :student/student-name student-name)
    current-class-size                  (assoc :student/current-class-size current-class-size)
    maximum-class-size                  (assoc :student/maximum-class-size maximum-class-size)
    (some? background-check-cleared?)   (assoc :student/background-check-cleared? background-check-cleared?)
    (some? promoted?)                   (assoc :student/promoted? promoted?)
    (some? safeguarding-recorded?)       (assoc :student/safeguarding-recorded? safeguarding-recorded?)
    jurisdiction                        (assoc :student/jurisdiction jurisdiction)
    status                              (assoc :student/status status)
    promotion-number                    (assoc :student/promotion-number promotion-number)
    safeguarding-number                 (assoc :student/safeguarding-number safeguarding-number)))

(def ^:private student-pull
  [:student/id :student/student-name :student/current-class-size :student/maximum-class-size
   :student/background-check-cleared? :student/promoted? :student/safeguarding-recorded?
   :student/jurisdiction :student/status :student/promotion-number :student/safeguarding-number])

(defn- pull->student [m]
  (when (:student/id m)
    {:id (:student/id m) :student-name (:student/student-name m)
     :current-class-size (:student/current-class-size m)
     :maximum-class-size (:student/maximum-class-size m)
     :background-check-cleared? (boolean (:student/background-check-cleared? m))
     :promoted? (boolean (:student/promoted? m))
     :safeguarding-recorded? (boolean (:student/safeguarding-recorded? m))
     :jurisdiction (:student/jurisdiction m) :status (:student/status m)
     :promotion-number (:student/promotion-number m) :safeguarding-number (:student/safeguarding-number m)}))

(defrecord DatomicStore [conn]
  Store
  (student [_ id]
    (pull->student (d/pull (d/db conn) student-pull [:student/id id])))
  (all-students [_]
    (->> (d/q '[:find [?id ...] :where [?e :student/id ?id]] (d/db conn))
         (map #(pull->student (d/pull (d/db conn) student-pull [:student/id %])))
         (sort-by :id)))
  (background-check-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?sid
                :where [?k :background-check/student-id ?sid] [?k :background-check/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ student-id]
    (dec* (d/q '[:find ?p . :in $ ?sid
                :where [?a :assessment/student-id ?sid] [?a :assessment/payload ?p]]
              (d/db conn) student-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (promotion-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :promotion/seq ?s] [?e :promotion/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (safeguarding-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :safeguarding/seq ?s] [?e :safeguarding/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-promotion-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :promotion-sequence/jurisdiction ?j] [?e :promotion-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-safeguarding-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :safeguarding-sequence/jurisdiction ?j] [?e :safeguarding-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (student-already-promoted? [s student-id]
    (boolean (:promoted? (student s student-id))))
  (student-already-recorded? [s student-id]
    (boolean (:safeguarding-recorded? (student s student-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :student/upsert
      (d/transact! conn [(student->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/student-id (first path) :assessment/payload (enc payload)}])

      :background-check/set
      (d/transact! conn [{:background-check/student-id (first path) :background-check/payload (enc payload)}])

      :student/mark-promoted
      (let [student-id (first path)
            {:keys [result student-patch]} (finalize-promotion! s student-id)
            jurisdiction (:jurisdiction (student s student-id))
            next-n (inc (next-promotion-sequence s jurisdiction))]
        (d/transact! conn
                     [(student->tx (assoc student-patch :id student-id))
                      {:promotion-sequence/jurisdiction jurisdiction :promotion-sequence/next next-n}
                      {:promotion/seq (count (promotion-history s)) :promotion/record (enc (get result "record"))}])
        result)

      :student/mark-recorded
      (let [student-id (first path)
            {:keys [result student-patch]} (finalize-safeguarding-record! s student-id)
            jurisdiction (:jurisdiction (student s student-id))
            next-n (inc (next-safeguarding-sequence s jurisdiction))]
        (d/transact! conn
                     [(student->tx (assoc student-patch :id student-id))
                      {:safeguarding-sequence/jurisdiction jurisdiction :safeguarding-sequence/next next-n}
                      {:safeguarding/seq (count (safeguarding-history s)) :safeguarding/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-students [s students]
    (when (seq students) (d/transact! conn (mapv student->tx (vals students)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:students ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [students]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-students s students))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo student set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
