(ns school.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300):
  this repo had a hand-written product LP (`docs/index.html`) but NO
  operator console and no generator at all.

  This namespace drives the REAL actor stack --
  `school.operation` (langgraph-clj StateGraph, `interrupt-before
  #{:request-approval}`) -> `school.governor` (Curriculum Safeguarding
  Governor) -> `school.store` (MemStore SSoT + append-only audit
  ledger) -- and renders the resulting store/ledger/run transcript.

  EVERY id, number, name, rule, jurisdiction citation, record number
  and approver on the page is read back out of that run. Nothing is
  hand-typed. The student ids used by the scenario (`student-1` ..
  `student-4`) were read out of `school.store/demo-data` itself, not
  copied from `school.sim` (a sibling school-domain repo in this fleet,
  `cloud-itonami-isic-851`, has a sim driver whose ids do NOT match its
  own seed data -- so the seed is the authority here, and this repo's
  own sim was then confirmed to agree with it).

  Determinism: no timestamps, no randomness, every map iterated in a
  sorted order, so two consecutive runs are byte-identical. Verify by
  rendering twice into a scratch directory and diffing.

  Build-time invariant: `-main` THROWS if the scenario produced zero
  `:governor-hold` facts, or zero HARD (violation-bearing) holds. A
  console that cannot show the governor refusing is not evidence that
  the governor exists.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [school.facts :as facts]
            [school.governor :as governor]
            [school.phase :as phase]
            [school.store :as store]
            [school.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- the run -----------------------------

(def ^:private educator
  "Phase-3 supervised-auto operator -- a licensed educator."
  {:actor-id "op-1" :actor-role :licensed-educator :phase 3})

(def ^:private educator-phase-0
  (assoc educator :phase 0))

(def ^:private educator-phase-2
  (assoc educator :phase 2))

(defn- exec!
  "One operation proposal through the actor. Records the full run
  result so the console can render the real transcript."
  [actor log tid request context]
  (let [r (g/run* actor {:request request :context context} {:thread-id tid})]
    (swap! log conj {:thread tid :stage :propose :request request
                     :context context :result r})
    r))

(defn- resume!
  "A human operator resumes an interrupted thread with an approval
  decision. `:approved` -> commit, anything else -> hold."
  [actor log tid decision by]
  (let [r (g/run* actor {:approval {:status decision :by by}}
                  {:thread-id tid :resume? true})]
    (swap! log conj {:thread tid :stage :resume :decision decision :by by
                     :result r})
    r))

(defn run-demo!
  "Drives a freshly seeded store through a scenario that reaches every
  disposition this actor can produce, and every HARD rule its governor
  implements.

  student-1 (JPN, 25/30 class size, staff background check cleared)
  clears a full lifecycle: intake auto-commits at phase 3 (no
  student-record risk yet), then a jurisdiction assessment, a staff
  background-check screening, a PROMOTION finalization and a
  SAFEGUARDING-RECORD finalization each escalate to the human educator
  and are approved. The two actuations are never auto-eligible at any
  phase -- `school.phase` omits them from every `:auto` set and
  `school.governor`'s high-stakes gate escalates them independently.

  Then the holds, none of which ever reach a human:

    - student-2 is seeded in jurisdiction `ATL`, which has NO entry in
      `school.facts/catalog`. Its assessment HARD-holds on
      `:no-spec-basis` (the advisor must not invent a jurisdiction's
      school-licensing requirements), and a promotion attempt on the
      same student then HARD-holds on `:evidence-incomplete` -- the
      required-evidence checklist was never satisfied because the
      assessment was refused.
    - student-3 (32 students in a classroom whose own recorded maximum
      is 30) HARD-holds a promotion on `:class-size-exceeds-maximum`,
      recomputed independently from the student's own fields.
    - student-4 (staff background check NOT cleared) HARD-holds its own
      screening on `:background-check-not-cleared`. Its safeguarding
      finalization is then REJECTED by the human educator -- the
      governor had no `:not-cleared` verdict on file to hard-hold on,
      precisely because the screening that would have written one was
      itself held, so the human is the layer that says no.
    - student-1 promoted/recorded a SECOND time HARD-holds on
      `:already-promoted` / `:already-recorded`, off dedicated boolean
      facts rather than a `:status` value.

  Finally two rollout-phase holds that are NOT governor violations:
  a clean intake at phase 0 (read-only) and a governor-clean promotion
  at phase 2 (actuation not yet enabled) both hold on `:phase-disabled`.

  Returns {:db store :log [run entries]}."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        log (atom [])]

    ;; --- student-1: the full clean lifecycle -------------------------
    (exec! actor log "t1-intake"
           {:op :student/intake :subject "student-1"
            :patch {:id "student-1" :student-name "Sakura Tanaka"}}
           educator)

    (exec! actor log "t1-assess" {:op :jurisdiction/assess :subject "student-1"} educator)
    (resume! actor log "t1-assess" :approved "op-1")

    (exec! actor log "t1-screen" {:op :background-check/screen :subject "student-1"} educator)
    (resume! actor log "t1-screen" :approved "op-1")

    (exec! actor log "t1-promote" {:op :promotion/finalize :subject "student-1"} educator)
    (resume! actor log "t1-promote" :approved "op-1")

    (exec! actor log "t1-safeguard" {:op :safeguarding/finalize :subject "student-1"} educator)
    (resume! actor log "t1-safeguard" :approved "op-1")

    ;; --- student-2: unregistered jurisdiction ------------------------
    (exec! actor log "t2-assess" {:op :jurisdiction/assess :subject "student-2"} educator)
    (exec! actor log "t2-promote" {:op :promotion/finalize :subject "student-2"} educator)

    ;; --- student-3: destination class over its own maximum -----------
    (exec! actor log "t3-assess" {:op :jurisdiction/assess :subject "student-3"} educator)
    (resume! actor log "t3-assess" :approved "op-1")
    (exec! actor log "t3-promote" {:op :promotion/finalize :subject "student-3"} educator)

    ;; --- student-4: uncleared staff background check -----------------
    (exec! actor log "t4-assess" {:op :jurisdiction/assess :subject "student-4"} educator)
    (resume! actor log "t4-assess" :approved "op-1")
    (exec! actor log "t4-screen" {:op :background-check/screen :subject "student-4"} educator)
    (exec! actor log "t4-safeguard" {:op :safeguarding/finalize :subject "student-4"} educator)
    (resume! actor log "t4-safeguard" :rejected "op-1")

    ;; --- double actuation --------------------------------------------
    (exec! actor log "t1-promote-again" {:op :promotion/finalize :subject "student-1"} educator)
    (exec! actor log "t1-safeguard-again" {:op :safeguarding/finalize :subject "student-1"} educator)

    ;; --- rollout-phase gate (not governor violations) ----------------
    (exec! actor log "p0-intake"
           {:op :student/intake :subject "student-2"
            :patch {:id "student-2" :student-name "Atlantis Doe"}}
           educator-phase-0)
    (exec! actor log "p2-promote" {:op :promotion/finalize :subject "student-4"} educator-phase-2)

    {:db db :log @log}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- code [v] (str "<code>" (esc v) "</code>"))
(defn- muted [v] (str "<span class=\"muted\">" (esc v) "</span>"))
(defn- ok [v] (str "<span class=\"ok\">" (esc v) "</span>"))
(defn- warn [v] (str "<span class=\"warn\">" (esc v) "</span>"))
(defn- crit [v] (str "<span class=\"critical\">" (esc v) "</span>"))
(defn- num-cell [v] (str "<span class=\"num\">" (esc v) "</span>"))

(defn- row [cells]
  (str "        <tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>"
       (apply str (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

(defn- kw-list [coll]
  (if (empty? coll)
    (muted "(none)")
    (str/join " " (map code (sort (map str coll))))))

;; ----------------------------- derived views -----------------------------

(defn- audit-facts
  "Every audit fact the graph produced this run, de-duplicated. The
  `:audit` channel accumulates across a resume, so the same fact
  appears in several run results."
  [log]
  (->> log
       (mapcat #(get-in % [:result :state :audit]))
       (distinct)
       (vec)))

(defn- approval-grants
  "[op subject] -> approver, read out of the graph's own
  `:approval-granted` facts (NOT out of the store -- whether the store
  kept it is exactly what the attribution section measures)."
  [log]
  (into {}
        (for [f (audit-facts log)
              :when (= :approval-granted (:t f))]
          [[(:op f) (:subject f)] (:by f)])))

(defn- holds [db]
  (filter #(#{:governor-hold :approval-rejected} (:t %)) (store/ledger db)))

(defn- hard-holds
  "Holds carrying at least one governor violation. A phase-gate hold
  carries none -- it is a rollout decision, not a compliance verdict."
  [db]
  (filter #(seq (:violations %)) (holds db)))

(defn- key-name [k] (if (keyword? k) (name k) (str k)))

(defn- approver-in-record
  "Render-time probe, never an assertion: walk this committed record's
  own keys and return the approver if one is actually there. A
  hard-coded claim about whether this store keeps the approver would
  become a lie the moment the store changes."
  [record]
  (some (fn [[k v]]
          (when (str/includes? (str/lower-case (key-name k)) "approv") v))
        (seq record)))

;; ----------------------------- sections -----------------------------

(defn- students-section [db log]
  (let [ledger (vec (store/ledger db))
        last-for (fn [id] (last (filter #(= id (:subject %)) ledger)))
        threads-for (fn [id] (->> log
                                  (filter #(= id (get-in % [:result :state :request :subject])))
                                  (map :thread) distinct sort))
        rows
        (for [{:keys [id student-name jurisdiction current-class-size maximum-class-size
                      background-check-cleared? promoted? safeguarding-recorded?
                      promotion-number safeguarding-number]} (store/all-students db)]
          (row [(code id)
                (esc student-name)
                (code jurisdiction)
                (str (num-cell (str current-class-size " / " maximum-class-size))
                     " "
                     (if (> current-class-size maximum-class-size)
                       (crit "over maximum")
                       (muted "within maximum")))
                (if background-check-cleared? (ok "cleared") (crit "NOT cleared"))
                (if promoted?
                  (str (ok "promoted") " " (code promotion-number))
                  (muted "not promoted"))
                (if safeguarding-recorded?
                  (str (ok "recorded") " " (code safeguarding-number))
                  (muted "no record"))
                (let [f (last-for id)]
                  (cond
                    (nil? f) (muted "no activity")
                    (= :committed (:t f)) (ok (str "committed · " (name (:op f))))
                    (= :approval-rejected (:t f)) (warn (str "rejected by approver · " (name (:op f))))
                    (= :governor-hold (:t f))
                    (if-let [r (-> f :violations first :rule)]
                      (crit (str "HARD hold · " (name r)))
                      (warn (str "phase hold · " (name (:phase-reason f :phase-gate)))))
                    :else (muted "in progress")))
                (muted (str/join " " (threads-for id)))]))]
    (section "Student roster (SSoT after this run)"
             (str "Read back out of <code>school.store</code> after the scenario below. "
                  "Class size is the student&#39;s own destination classroom &mdash; the governor "
                  "recomputes it independently, it never trusts the proposal.")
             (table ["Student" "Name" "Jurisdiction" "Class size (current / maximum)"
                     "Staff background check" "Promotion" "Safeguarding record"
                     "Last decision" "Threads"]
                    rows))))

(defn- governor-section [db]
  (let [hh (hard-holds db)
        by-rule (group-by #(-> % :violations first :rule) hh)
        rows (for [rule (sort-by str (keys by-rule))
                   :let [fs (by-rule rule)
                         f (first fs)]]
               (row [(code rule)
                     (crit "HARD · no human override")
                     (num-cell (count fs))
                     (kw-list (distinct (map :op fs)))
                     (kw-list (distinct (map :subject fs)))
                     (esc (-> f :violations first :detail))]))]
    (section "Curriculum Safeguarding Governor — HARD holds observed this run"
             (str "Every row was produced by <code>school.governor/check</code> during the run below, "
                  "not described here. A HARD hold ends the operation at the "
                  "<code>:hold</code> node: it never reaches the <code>:request-approval</code> "
                  "interrupt, so no human is ever offered the chance to override it.")
             (table ["Rule" "Severity" "Times fired" "Ops" "Subjects" "Detail (governor's own words)"]
                    rows))))

(defn- phase-hold-section [db]
  (let [ph (remove #(seq (:violations %)) (holds db))
        rows (for [f ph]
               (row [(code (:phase-reason f :n/a))
                     (num-cell (:phase f))
                     (code (:op f))
                     (code (:subject f))
                     (num-cell (:confidence f))
                     (muted "governor raised no violation — this is a rollout decision")]))]
    (section "Rollout-phase holds (not compliance violations)"
             (str "<code>school.phase/gate</code> can only ever add caution. These operations were "
                  "governor-clean and still held, because the op is not enabled to write at that "
                  "phase. Shown separately from the HARD holds above so the two are never conflated.")
             (table ["Reason" "Phase" "Op" "Subject" "Advisor confidence" "Note"] rows))))

(defn- phases-section []
  (let [rows (for [p (sort (keys phase/phases))
                   :let [{:keys [label writes auto]} (get phase/phases p)]]
               (row [(num-cell p)
                     (esc label)
                     (kw-list writes)
                     (kw-list auto)
                     (if (= p phase/default-phase) (ok "default") (muted ""))]))]
    (section "Staged rollout (school.phase)"
             (str "Read directly out of <code>school.phase/phases</code>. Note the permanent structural "
                  "fact: <code>:promotion/finalize</code> and <code>:safeguarding/finalize</code> are "
                  "absent from EVERY phase&#39;s auto-commit set, including phase 3. They are the two "
                  "real-world student-record acts this actor performs, and both are always a human "
                  "educator&#39;s call.")
             (table ["Phase" "Label" "May write" "May auto-commit when governor-clean" ""] rows))))

(defn- ops-section [log]
  (let [by-op (group-by #(get-in % [:result :state :request :op])
                        (filter #(= :propose (:stage %)) log))
        min-write-phase (fn [o] (first (sort (for [[p {:keys [writes]}] phase/phases
                                                   :when (contains? writes o)] p))))
        auto-phases (fn [o] (sort (for [[p {:keys [auto]}] phase/phases
                                        :when (contains? auto o)] p)))
        rows (for [o (sort-by str phase/write-ops)
                   :let [runs (get by-op o)
                         stakes (distinct (keep #(get-in % [:result :state :proposal :stake]) runs))
                         disps (sort (distinct (map #(get-in % [:result :state :disposition]) runs)))
                         confs (sort (distinct (keep #(get-in % [:result :state :proposal :confidence]) runs)))
                         aps (auto-phases o)]]
               (row [(code o)
                     (if-let [p (min-write-phase o)] (str "phase " (num-cell p) "+") (crit "never"))
                     (if (seq aps)
                       (ok (str "phase " (str/join ", " aps)))
                       (crit "never — at any phase"))
                     (if (seq stakes)
                       (str (kw-list stakes) " "
                            (if (some governor/high-stakes stakes)
                              (crit "high-stakes")
                              (muted "")))
                       (muted "none"))
                     (if (seq confs)
                       (num-cell (str/join ", " confs))
                       (muted "—"))
                     (if (seq disps) (kw-list disps) (muted "not exercised"))]))]
    (section "Action gate — per op, as this run actually behaved"
             (str "Write-eligibility and auto-eligibility are read out of <code>school.phase/phases</code>; "
                  "the stake, advisor confidence and dispositions are read out of the run transcript. "
                  "The governor&#39;s confidence floor is <code>"
                  governor/confidence-floor "</code> and its permanently high-stakes set is "
                  (kw-list governor/high-stakes) ".")
             (table ["Op" "Writable from" "Auto-eligible" "Stake observed" "Advisor confidence observed"
                     "Dispositions observed"] rows))))

(defn- jurisdictions-section [db]
  (let [assessed (into {} (for [s (store/all-students db)
                                :let [a (store/assessment-of db (:id s))]
                                :when a]
                            [(:jurisdiction a) a]))
        rows (for [iso3 (sort (keys facts/catalog))
                   :let [{:keys [name owner-authority legal-basis provenance required-evidence]}
                         (get facts/catalog iso3)]]
               (row [(code iso3)
                     (esc name)
                     (esc owner-authority)
                     (esc legal-basis)
                     (str "<a href=\"" (esc provenance) "\">" (esc provenance) "</a>")
                     (num-cell (count required-evidence))
                     (if (contains? assessed iso3)
                       (ok "assessed this run")
                       (muted "not exercised"))]))
        cov (facts/coverage)
        seeded-jurisdictions (sort (distinct (map :jurisdiction (store/all-students db))))
        uncovered (remove facts/catalog seeded-jurisdictions)]
    (section "Jurisdiction spec-basis catalog (school.facts)"
             (str "The G2 citation table the governor checks every <code>:jurisdiction/assess</code> "
                  "proposal against. Coverage is reported honestly: "
                  (:covered cov) " of " (:requested cov) " catalogued jurisdictions carry an official "
                  "spec-basis, and the seed set itself contains "
                  (if (seq uncovered)
                    (str "jurisdiction " (str/join ", " (map code uncovered))
                         ", which has NO entry &mdash; so its assessment HARD-holds rather than "
                         "inventing requirements.")
                    "no uncatalogued jurisdiction."))
             (table ["ISO3" "Name" "Owner authority" "Legal basis" "Provenance"
                     "Required evidence items" "This run"] rows))))

(defn- evidence-section [db]
  (let [assessed (->> (store/all-students db)
                      (keep #(store/assessment-of db (:id %)))
                      (map :jurisdiction)
                      distinct sort)
        rows (for [iso3 assessed
                   item (facts/evidence-checklist iso3)]
               (row [(code iso3) (esc item) (ok "on file")]))]
    (section "Required evidence actually on file (per assessed jurisdiction)"
             (str "The checklist the advisor drafted and the human educator approved, now committed to "
                  "the SSoT. <code>school.governor</code> re-derives satisfaction from "
                  "<code>school.facts/required-evidence-satisfied?</code> before either actuation may "
                  "proceed &mdash; it does not trust the advisor&#39;s self-reported confidence.")
             (table ["Jurisdiction" "Required evidence item" "Status"] rows))))

(defn- assessment-register-section [db log]
  (let [grants (approval-grants log)
        rows (for [s (store/all-students db)
                   :let [a (store/assessment-of db (:id s))]]
               (row [(code (:id s))
                     (if a (code (:jurisdiction a)) (muted "—"))
                     (if a
                       (str (num-cell (count (:checklist a))) " items")
                       (muted "no assessment committed"))
                     (if a
                       (if (facts/required-evidence-satisfied? (:jurisdiction a) (:checklist a))
                         (ok "satisfied")
                         (crit "NOT satisfied"))
                       (crit "NOT satisfied"))
                     (if a
                       (if-let [sb (:spec-basis a)] (esc sb) (crit "none"))
                       (muted "—"))
                     (if a
                       (if-let [by (approver-in-record a)]
                         (str (ok by) " " (muted "(retained in record)"))
                         (if-let [by (grants [:jurisdiction/assess (:id s)])]
                           (str (esc by) " " (muted "(audit only; not retained in record)"))
                           (muted "no approver recorded")))
                       (muted "—"))]))]
    (section "Assessment register (:assessment/set)"
             (str "One row per seeded student. A missing row is itself a result: student-2&#39;s "
                  "assessment was HARD-held, so nothing was ever written for it.")
             (table ["Student" "Jurisdiction" "Checklist" "Evidence satisfied"
                     "Spec-basis cited" "Approved by"] rows))))

(defn- screening-register-section [db log]
  (let [grants (approval-grants log)
        rows (for [s (store/all-students db)
                   :let [bc (store/background-check-of db (:id s))]]
               (row [(code (:id s))
                     (if bc
                       (case (:verdict bc)
                         :cleared (ok "cleared")
                         :not-cleared (crit "NOT cleared")
                         (warn (str (:verdict bc))))
                       (muted "no screening committed"))
                     (if bc
                       (if-let [by (approver-in-record bc)]
                         (str (ok by) " " (muted "(retained in record)"))
                         (if-let [by (grants [:background-check/screen (:id s)])]
                           (str (esc by) " " (muted "(audit only; not retained in record)"))
                           (muted "no approver recorded")))
                       (muted "—"))
                     (if bc
                       (muted "screening committed")
                       (if (false? (:background-check-cleared? s))
                         (crit "screening itself HARD-held — no record written")
                         (muted "not screened this run")))]))]
    (section "Staff background-check register (:background-check/set)"
             (str "The screening op HARD-holds on its OWN finding: a screening that detects an "
                  "uncleared staff background check is refused and writes nothing, so the absence of "
                  "a row here is load-bearing, not an omission.")
             (table ["Student" "Verdict on file" "Approved by" "Note"] rows))))

(defn- registry-section [db log]
  (let [grants (approval-grants log)
        mk (fn [kind op records]
             (for [r records]
               (row [(esc kind)
                     (code (get r "record_id"))
                     (esc (get r "kind"))
                     (code (get r "student_id"))
                     (code (get r "jurisdiction"))
                     (if (get r "immutable") (ok "immutable") (warn "mutable"))
                     (if-let [by (approver-in-record r)]
                       (str (ok by) " " (muted "(retained in record)"))
                       (if-let [by (grants [op (get r "student_id")])]
                         (str (esc by) " " (muted "(audit only; not retained in record)"))
                         (muted "no approver recorded")))])))
        rows (concat (mk "promotion" :promotion/finalize (store/promotion-history db))
                     (mk "safeguarding" :safeguarding/finalize (store/safeguarding-history db)))]
    (section "Registry drafts committed (school.registry)"
             (str "Both registers are append-only and every certificate this actor produces is "
                  "<code>status: draft-unsigned</code> with <code>issued_by_registry: false</code> "
                  "&mdash; signing is the school&#39;s own act, never this actor&#39;s. The reference "
                  "numbers are jurisdiction-scoped sequences, not an invented check-digit standard.")
             (table ["Register" "Record id" "Kind" "Student" "Jurisdiction" "Immutability" "Approved by"]
                    rows))))

(defn- attribution-section [db log]
  (let [grants (approval-grants log)
        registers [["assessment register" :assessment/set :jurisdiction/assess
                    (keep #(store/assessment-of db (:id %)) (store/all-students db))]
                   ["background-check register" :background-check/set :background-check/screen
                    (keep #(store/background-check-of db (:id %)) (store/all-students db))]
                   ["promotion drafts" :student/mark-promoted :promotion/finalize
                    (store/promotion-history db)]
                   ["safeguarding drafts" :student/mark-recorded :safeguarding/finalize
                    (store/safeguarding-history db)]]
        rows (for [[label effect op records] registers
                   :let [records (vec records)
                         with-approver (filter approver-in-record records)]]
               (row [(esc label)
                     (code effect)
                     (num-cell (count records))
                     (cond
                       (empty? records) (muted "no records — cannot tell")
                       (= (count with-approver) (count records)) (ok "yes — present on every record")
                       (seq with-approver) (warn "partially")
                       :else (crit "no — dropped on commit"))
                     (if (seq with-approver)
                       (kw-list (distinct (map approver-in-record with-approver)))
                       (if-let [bys (seq (distinct (keep (fn [r]
                                                           (grants [op (or (get r "student_id")
                                                                           (:student-id r))]))
                                                         records)))]
                         (str (str/join ", " (map esc bys)) " "
                              (muted "(audit only; not retained in record)"))
                         (muted "—")))]))
        rejections (filter #(= :approval-rejected (:t %)) (store/ledger db))]
    (section "Approver attribution — measured, not assumed"
             (str "Derived at render time by walking each committed register and asking whether an "
                  "approver key is actually present, rather than asserting a claim that would go stale. "
                  "Where it is absent the approver is joined in from the graph&#39;s own "
                  "<code>:approval-granted</code> audit facts and labelled as such &mdash; silently "
                  "omitting it would make &quot;nobody approved&quot; indistinguishable from "
                  "&quot;the store did not keep it&quot;."
                  (when (seq rejections)
                    (str " Rejections are a further gap measured the same way: "
                         (count rejections)
                         " approval rejection(s) reached the ledger and the "
                         "<code>:approval-rejected</code> fact carries no <code>:by</code> key at all, "
                         "so the rejecting operator is not recorded anywhere in the store.")))
             (table ["Register" "Commit effect" "Records committed"
                     "Approver retained in the record?" "Approver"] rows))))

(defn- timeline-section [log]
  (let [rows (for [{:keys [thread stage request decision by result]} log
                   :let [st (:state result)
                         req (or request (:request st))
                         v (:verdict st)]]
               (row [(code thread)
                     (if (= :resume stage)
                       (str (code (name (:op req))) " " (muted (str "resume · " (name decision) " by " by)))
                       (code (name (:op req))))
                     (code (:subject req))
                     (num-cell (get-in st [:context :phase]))
                     (if-let [c (get-in st [:proposal :confidence])] (num-cell c) (muted "—"))
                     (case (:disposition st)
                       :commit (ok "commit")
                       :escalate (warn "escalate → human")
                       :hold (if (seq (:violations v)) (crit "HARD hold") (warn "phase hold"))
                       (muted (str (:disposition st))))
                     (if (seq (:violations v))
                       (kw-list (map :rule (:violations v)))
                       (muted "—"))
                     (case (:status result)
                       :interrupted (warn "interrupted (awaiting human)")
                       (muted (str (name (or (:status result) :unknown)))))]))]
    (section "Operation transcript (every graph run this build made)"
             (str "One row per <code>langgraph.graph/run*</code> call. An <em>interrupted</em> row is "
                  "the <code>interrupt-before #{:request-approval}</code> gate actually firing: the "
                  "actor stopped and waited for a licensed educator, and the next row on the same "
                  "thread is that human&#39;s resume.")
             (table ["Thread" "Op" "Subject" "Phase" "Advisor confidence" "Disposition"
                     "Violations" "Graph status"] rows))))

(defn- ledger-section [db]
  (let [rows (for [f (store/ledger db)]
               (row [(code (name (:t f)))
                     (code (name (:op f)))
                     (code (:subject f))
                     (esc (:actor f))
                     (case (:disposition f)
                       :commit (ok "commit")
                       :hold (crit "hold")
                       (muted (str (:disposition f))))
                     (cond
                       (seq (:violations f)) (kw-list (map :rule (:violations f)))
                       (:phase-reason f) (str (code (:phase-reason f)) " "
                                              (muted (str "phase " (:phase f))))
                       (seq (:basis f)) (esc (str/join " · " (map str (:basis f))))
                       :else (muted "—"))
                     (if-let [s (:summary f)] (esc s) (muted "—"))]))]
    (section "Append-only audit ledger (this run)"
             (str "<code>school.store/ledger</code> after the run: the immutable decision log a parent "
                  "trusting a school, or an operator defending a disputed promotion, actually reads. "
                  "Commits and holds land in the same log &mdash; a refusal is evidence too.")
             (table ["Fact" "Op" "Subject" "Actor" "Disposition" "Basis / violations" "Summary"] rows))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the whole console from a store that has already been driven
  by `run-demo!` plus that run's transcript."
  [{:keys [db log]}]
  (let [hh (hard-holds db)
        rules (sort (distinct (map #(-> % :violations first :rule) hh)))]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
     "<title>cloud-itonami-isic-8510 &middot; pre-primary and primary education &mdash; Operator Console</title>\n"
     "<style>\n" (jp-go-dds.skin/dds+skin) "\n</style>\n"
     "</head>\n<body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Pre-primary and primary education (ISIC 8510) &mdash; Operator Console</h1>\n"
     "  <span class=\"badge\">ISIC 8510</span>\n"
     "</header>\n"
     "<p class=\"subtitle\">Read-only sample. Every value below was produced by a real run of "
     "<code>school.operation</code> &rarr; <code>school.governor</code> &rarr; "
     "<code>school.store</code> at build time (<code>clojure -M:dev:render-html</code>), not written "
     "by hand. Promotion finalization and safeguarding-record finalization are always a human "
     "educator&#39;s call, at every phase.</p>\n"
     "<div class=\"banner\">\n"
     "  <p><strong>This build:</strong> "
     (num-cell (count (store/ledger db))) " audit facts · "
     (num-cell (count log)) " graph runs · "
     (num-cell (count hh)) " HARD governor holds across "
     (num-cell (count rules)) " distinct rules ("
     (str/join ", " (map code rules)) ") · "
     (num-cell (count (store/promotion-history db))) " promotion draft(s) · "
     (num-cell (count (store/safeguarding-history db))) " safeguarding draft(s).</p>\n"
     "  <p class=\"muted\">A HARD hold never reaches a human. The build fails if this run produces "
     "none &mdash; see <code>school.render-html/-main</code>.</p>\n"
     "</div>\n"
     "<main>\n"
     (students-section db log)
     (governor-section db)
     (phase-hold-section db)
     (phases-section)
     (ops-section log)
     (jurisdictions-section db)
     (evidence-section db)
     (assessment-register-section db log)
     (screening-register-section db log)
     (registry-section db log)
     (attribution-section db log)
     (timeline-section log)
     (ledger-section db)
     "</main>\n"
     "<footer>\n"
     "  <p>Generated at build time by <code>school.render-html</code> from "
     "<code>school.store/demo-data</code>. Deterministic: no timestamps, no randomness &mdash; two "
     "consecutive runs are byte-identical. Regenerate with "
     "<code>clojure -M:dev:render-html</code>.</p>\n"
     "  <p>cloud-itonami &middot; ISIC 8510 pre-primary and primary education &middot; "
     "governed actor demo.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db log] :as run} (run-demo!)
        all-holds (holds db)
        hh (hard-holds db)]
    ;; Build-time invariant, not a convention: a console that cannot
    ;; show the governor refusing is not evidence that it exists.
    (when (zero? (count all-holds))
      (throw (ex-info "render-html: scenario produced ZERO :governor-hold records"
                      {:ledger-size (count (store/ledger db)) :runs (count log)})))
    (when (zero? (count hh))
      (throw (ex-info "render-html: scenario produced ZERO HARD (violation-bearing) governor holds"
                      {:holds (count all-holds)})))
    (let [html (render run)]
      (spit out html)
      (println "wrote" out
               (str "(" (count (store/ledger db)) " ledger facts, "
                    (count log) " graph runs, "
                    (count hh) " HARD holds over "
                    (count (distinct (map #(-> % :violations first :rule) hh))) " rules, "
                    (count (store/promotion-history db)) " promotion drafts, "
                    (count (store/safeguarding-history db)) " safeguarding drafts)")))))
