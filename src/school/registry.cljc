(ns school.registry
  "Pure-function promotion-finalization + safeguarding-record-
  finalization record construction -- an append-only school book-of-
  record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a promotion-finalization or
  safeguarding-record reference number -- every school/jurisdiction
  assigns its own reference format. This namespace does NOT invent
  one; it builds a jurisdiction-scoped sequence number and validates
  the record's required fields, the same honest, non-fabricating
  discipline `school.facts` uses.

  `class-size-exceeds-maximum?` is the SECOND non-temporal instance of
  this fleet's MAXIMUM-ceiling family (`facility.registry/occupancy-
  exceeds-capacity?` established the first, comparing a sports
  facility's own occupancy against its own capacity), reusing the same
  two-field-on-one-entity comparison shape for a genuinely different
  ground truth: does the student's own destination classroom's current
  size exceed that classroom's own recorded maximum, before a
  promotion can be finalized into it.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real school-information system. It builds the RECORD a
  school would keep, not the act of finalizing the promotion or the
  safeguarding record itself (that is `school.operation`'s
  `:promotion/finalize`/`:safeguarding/finalize`, always human-gated
  -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  school's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn class-size-exceeds-maximum?
  "Does `student`'s own destination `:current-class-size` (the class
  the student would be promoted INTO) exceed the school's own recorded
  `:maximum-class-size` for that classroom? A pure ground-truth check
  against the student's own permanent fields -- no upstream comparison
  needed."
  [{:keys [current-class-size maximum-class-size]}]
  (and (number? current-class-size) (number? maximum-class-size)
       (> current-class-size maximum-class-size)))

(defn register-promotion-finalization
  "Validate + construct the PROMOTION-FINALIZATION registration DRAFT
  -- the school's own legal act of finalizing a real student's
  promotion to the next grade/class. Pure function -- does not touch
  any real school-information system; it builds the RECORD a school
  would keep. `school.governor` independently re-verifies the
  student's own destination-class-size sufficiency, and blocks a
  double-promotion of the same student, before this is ever allowed to
  commit."
  [student-id jurisdiction sequence]
  (when-not (and student-id (not= student-id ""))
    (throw (ex-info "promotion-finalization: student_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "promotion-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "promotion-finalization: sequence must be >= 0" {})))
  (let [promotion-number (str (str/upper-case jurisdiction) "-PRM-" (zero-pad sequence 6))
        record {"record_id" promotion-number
                "kind" "promotion-finalization-draft"
                "student_id" student-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "promotion_number" promotion-number
     "certificate" (unsigned-certificate "PromotionFinalization" promotion-number promotion-number)}))

(defn register-safeguarding-record-finalization
  "Validate + construct the SAFEGUARDING-RECORD-FINALIZATION
  registration DRAFT -- the school's own legal act of finalizing a
  real child-safeguarding-relevant record into the student's permanent
  file. Pure function -- does not touch any real school-information
  system; it builds the RECORD a school would keep. `school.governor`
  independently re-verifies that the reporting staff member's own
  background-check clearance is on file, and blocks a double-
  finalization of the same student's safeguarding record, before this
  is ever allowed to commit."
  [student-id jurisdiction sequence]
  (when-not (and student-id (not= student-id ""))
    (throw (ex-info "safeguarding-record-finalization: student_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "safeguarding-record-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "safeguarding-record-finalization: sequence must be >= 0" {})))
  (let [record-number (str (str/upper-case jurisdiction) "-SFG-" (zero-pad sequence 6))
        record {"record_id" record-number
                "kind" "safeguarding-record-finalization-draft"
                "student_id" student-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "safeguarding_number" record-number
     "certificate" (unsigned-certificate "SafeguardingRecordFinalization" record-number record-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
