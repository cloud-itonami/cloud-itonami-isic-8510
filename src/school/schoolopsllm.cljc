(ns school.schoolopsllm
  "SchoolOps-LLM client -- the *contained intelligence node* for the
  school actor.

  It normalizes student-intake, drafts a per-jurisdiction school-
  licensing evidence checklist, screens students for an uncleared
  staff background check, drafts the promotion-finalization action,
  and drafts the safeguarding-record-finalization action. CRITICAL: it
  is a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record or a real
  promotion/safeguarding-record finalization. Every output is censored
  downstream by `school.governor` before anything touches the SSoT,
  and `:promotion/finalize`/`:safeguarding/finalize` proposals NEVER
  auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/finalize-promotion | :actuation/finalize-safeguarding-record | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [school.facts :as facts]
            [school.registry :as registry]
            [school.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the student, class-size figures or jurisdiction.
  High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "生徒記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :student/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction school-licensing evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `school.facts` -- the Curriculum Safeguarding Governor must
  reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [s (store/student db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction s))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "school.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-background-check
  "Staff background-check screening draft. `:background-check-
  cleared?` on the student record injects the failure mode: the
  Curriculum Safeguarding Governor must HOLD, un-overridably, on any
  uncleared background check."
  [db {:keys [subject]}]
  (let [s (store/student db subject)]
    (cond
      (nil? s)
      {:summary "対象生徒記録が見つかりません" :rationale "no student record"
       :cites [] :effect :background-check/set :value {:student-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (false? (:background-check-cleared? s))
      {:summary    (str (:student-name s) ": 担当職員の身元確認未完了を検出")
       :rationale  "スクリーニングが未完了の職員身元確認を検出。人手確認とホールドが必須。"
       :cites      [:background-check]
       :effect     :background-check/set
       :value      {:student-id subject :verdict :not-cleared}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:student-name s) ": 担当職員の身元確認は完了済み")
       :rationale  "職員身元確認スクリーニング完了。"
       :cites      [:background-check]
       :effect     :background-check/set
       :value      {:student-id subject :verdict :cleared}
       :stake      nil
       :confidence 0.9})))

(defn- propose-promotion-finalization
  "Draft the actual PROMOTION-FINALIZATION action -- finalizing a real
  student's promotion to the next grade/class. ALWAYS `:stake
  :actuation/finalize-promotion` -- this is a REAL-WORLD student-
  record act, never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`school.phase`); the governor also always escalates on
  `:actuation/finalize-promotion`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [s (store/student db subject)]
    {:summary    (str subject " 向け進級確定提案"
                      (when s (str " (student=" (:student-name s) ")")))
     :rationale  (if s
                   (str "current-class-size=" (:current-class-size s)
                        " maximum-class-size=" (:maximum-class-size s))
                   "生徒記録が見つかりません")
     :cites      (if s [subject] [])
     :effect     :student/mark-promoted
     :value      {:student-id subject}
     :stake      :actuation/finalize-promotion
     :confidence (if (and s (not (registry/class-size-exceeds-maximum? s))) 0.9 0.3)}))

(defn- propose-safeguarding-record-finalization
  "Draft the actual SAFEGUARDING-RECORD-FINALIZATION action --
  finalizing a real child-safeguarding-relevant record into the
  student's permanent file. ALWAYS `:stake :actuation/finalize-
  safeguarding-record` -- this is a REAL-WORLD student-record act,
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set (`school.phase`);
  the governor also always escalates on `:actuation/finalize-
  safeguarding-record`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [s (store/student db subject)
        uncleared? (and s (false? (:background-check-cleared? s)))]
    {:summary    (str subject " 向け安全対策記録確定提案"
                      (when s (str " (student=" (:student-name s) ")")))
     :rationale  (if s
                   (str "background-check-cleared?=" (:background-check-cleared? s))
                   "生徒記録が見つかりません")
     :cites      (if s [subject] [])
     :effect     :student/mark-recorded
     :value      {:student-id subject}
     :stake      :actuation/finalize-safeguarding-record
     :confidence (if uncleared? 0.3 0.9)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :student/intake         (normalize-intake db request)
    :jurisdiction/assess     (assess-jurisdiction db request)
    :background-check/screen (screen-background-check db request)
    :promotion/finalize      (propose-promotion-finalization db request)
    :safeguarding/finalize   (propose-safeguarding-record-finalization db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは学校の進級確定・安全対策記録確定エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:student/upsert|:assessment/set|:background-check/set|"
       ":student/mark-promoted|:student/mark-recorded) "
       ":stake(:actuation/finalize-promotion か :actuation/finalize-safeguarding-record か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess      {:student (store/student st subject)}
    :background-check/screen  {:student (store/student st subject)}
    :promotion/finalize       {:student (store/student st subject)}
    :safeguarding/finalize    {:student (store/student st subject)}
    {:student (store/student st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Curriculum Safeguarding
  Governor escalates/holds -- an LLM hiccup can never auto-finalize a
  promotion or auto-finalize a safeguarding record."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :schoolopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
