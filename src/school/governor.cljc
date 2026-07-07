(ns school.governor
  "Curriculum Safeguarding Governor -- the independent compliance
  layer that earns the SchoolOps-LLM the right to commit. The LLM has
  no notion of jurisdictional school-licensing law, whether a
  student's own destination classroom actually has room, whether a
  reporting staff member's own background check is actually cleared,
  or when an act stops being a draft and becomes a real-world
  promotion finalization or safeguarding-record finalization, so this
  MUST be a separate system able to *reject* a proposal and fall back
  to HOLD -- the school analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, a
  destination class size over its own maximum, an uncleared staff
  background check, or a double promotion/safeguarding-record
  finalization). The confidence/actuation gate is SOFT: it asks a
  human to look (low confidence / actuation), and the human may
  approve -- but see `school.phase`: for `:stake :actuation/finalize-
  promotion`/`:actuation/finalize-safeguarding-record` (a real
  student-record act) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`school.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:promotion/finalize`/
                                       `:safeguarding/finalize`, has the
                                       jurisdiction actually been
                                       assessed with a full enrollment/
                                       curriculum/safeguarding-policy/
                                       background-check evidence
                                       checklist on file?
    3. Class size exceeds maximum  -- for `:promotion/finalize`,
                                       INDEPENDENTLY recompute whether
                                       the student's own destination
                                       `:current-class-size` exceeds
                                       the classroom's own recorded
                                       `:maximum-class-size` (`school.
                                       registry/class-size-exceeds-
                                       maximum?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all. The SECOND
                                       instance of this fleet's non-
                                       temporal MAXIMUM-ceiling family
                                       (`facility.governor/occupancy-
                                       exceeds-capacity-violations`
                                       established the first).
    4. Background check not
       cleared                       -- reported by THIS proposal
                                       itself (a `:background-check/
                                       screen` that just found an
                                       uncleared staff background
                                       check), or already on file for
                                       the student (`:background-
                                       check/screen`/either actuation
                                       op). Evaluated UNCONDITIONALLY
                                       (not scoped to a specific op),
                                       the SAME discipline `casualty.
                                       governor/sanctions-violations`'s
                                       original fix and its sixteen
                                       prior reuses establish -- the
                                       EIGHTEENTH distinct application
                                       of this exact discipline
                                       overall, and the FIRST
                                       specifically for the staff-
                                       background-check-clearance
                                       concept (distinct from the
                                       credential-CURRENCY concept
                                       `clinic`/`veterinary`/`hospital`
                                       established -- a background
                                       check is a one-time clearance
                                       gate, not a renewable license).
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:promotion/
                                       finalize`/`:safeguarding/
                                       finalize` (REAL student-record
                                       acts) -> escalate.

  Two more guards, double-promotion/double-safeguarding-record
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all -- `already-
  promoted-violations`/`already-recorded-violations` refuse to
  finalize a promotion/safeguarding record for the SAME student twice,
  off dedicated `:promoted?`/`:safeguarding-recorded?` facts (never a
  `:status` value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [school.facts :as facts]
            [school.registry :as registry]
            [school.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Finalizing a real promotion and finalizing a real safeguarding
  record are the two real-world actuation events this actor performs
  -- a two-member set, matching every prior dual-actuation sibling's
  shape."
  #{:actuation/finalize-promotion :actuation/finalize-safeguarding-record})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:promotion/finalize`/`:safeguarding/
  finalize`) proposal with no spec-basis citation is a HARD violation
  -- never invent a jurisdiction's school-licensing requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :promotion/finalize :safeguarding/finalize} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:promotion/finalize`/`:safeguarding/finalize`, the
  jurisdiction's required enrollment/curriculum-approval/safeguarding-
  policy/background-check evidence must actually be satisfied -- do
  not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:promotion/finalize :safeguarding/finalize} op)
    (let [s (store/student st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction s) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(学齢簿記載事項証明書/教育課程編成届/安全対策方針/職員身元確認証明書等)が充足していない状態での提案"}]))))

(defn- class-size-exceeds-maximum-violations
  "For `:promotion/finalize`, INDEPENDENTLY recompute whether the
  student's own destination `:current-class-size` exceeds the
  classroom's own recorded `:maximum-class-size` via `school.registry/
  class-size-exceeds-maximum?` -- needs no proposal inspection or
  stored-verdict lookup at all, since its input is a permanent
  ground-truth field already on the student."
  [{:keys [op subject]} st]
  (when (= op :promotion/finalize)
    (let [s (store/student st subject)]
      (when (registry/class-size-exceeds-maximum? s)
        [{:rule :class-size-exceeds-maximum
          :detail (str subject " の進級先クラス人数(" (:current-class-size s)
                      ")が定員(" (:maximum-class-size s) ")を超過")}]))))

(defn- background-check-not-cleared-violations
  "An uncleared staff background check -- reported by THIS proposal
  (e.g. a `:background-check/screen` that itself just found an
  uncleared check), or already on file in the store for the student
  (`:background-check/screen`/either actuation op) -- is a HARD,
  un-overridable hold. Evaluated UNCONDITIONALLY (not scoped to a
  specific op) so the screening op itself can HARD-hold on its own
  finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :not-cleared (get-in proposal [:value :verdict]))
        student-id (when (contains? #{:background-check/screen :promotion/finalize :safeguarding/finalize} op) subject)
        hit-on-file? (and student-id (= :not-cleared (:verdict (store/background-check-of st student-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :background-check-not-cleared
        :detail "職員の身元確認が未完了の状態での進級確定/安全対策記録確定提案は進められない"}])))

(defn- already-promoted-violations
  "For `:promotion/finalize`, refuses to finalize a promotion for the
  SAME student twice, off a dedicated `:promoted?` fact (never a
  `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :promotion/finalize)
    (when (store/student-already-promoted? st subject)
      [{:rule :already-promoted
        :detail (str subject " は既に進級確定済み")}])))

(defn- already-recorded-violations
  "For `:safeguarding/finalize`, refuses to finalize a safeguarding
  record for the SAME student twice, off a dedicated
  `:safeguarding-recorded?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :safeguarding/finalize)
    (when (store/student-already-recorded? st subject)
      [{:rule :already-recorded
        :detail (str subject " は既に安全対策記録確定済み")}])))

(defn check
  "Censors a SchoolOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (class-size-exceeds-maximum-violations request st)
                           (background-check-not-cleared-violations request proposal st)
                           (already-promoted-violations request st)
                           (already-recorded-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
