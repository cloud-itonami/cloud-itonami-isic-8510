(ns school.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [school.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka" (:student-name (store/student s "student-1"))))
      (is (= "JPN" (:jurisdiction (store/student s "student-1"))))
      (is (= 25 (:current-class-size (store/student s "student-1"))))
      (is (= 30 (:maximum-class-size (store/student s "student-1"))))
      (is (true? (:background-check-cleared? (store/student s "student-1"))))
      (is (= 32 (:current-class-size (store/student s "student-3"))))
      (is (false? (:background-check-cleared? (store/student s "student-4"))))
      (is (false? (:promoted? (store/student s "student-1"))))
      (is (false? (:safeguarding-recorded? (store/student s "student-1"))))
      (is (= ["student-1" "student-2" "student-3" "student-4"]
             (mapv :id (store/all-students s))))
      (is (nil? (store/background-check-of s "student-1")))
      (is (nil? (store/assessment-of s "student-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/promotion-history s)))
      (is (= [] (store/safeguarding-history s)))
      (is (zero? (store/next-promotion-sequence s "JPN")))
      (is (zero? (store/next-safeguarding-sequence s "JPN")))
      (is (false? (store/student-already-promoted? s "student-1")))
      (is (false? (store/student-already-recorded? s "student-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :student/upsert
                                 :value {:id "student-1" :student-name "Sakura Tanaka"}})
        (is (= "Sakura Tanaka" (:student-name (store/student s "student-1"))))
        (is (= 25 (:current-class-size (store/student s "student-1"))) "unrelated field preserved"))
      (testing "assessment / background-check payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["student-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "student-1")))
        (store/commit-record! s {:effect :background-check/set :path ["student-1"]
                                 :payload {:student-id "student-1" :verdict :cleared}})
        (is (= {:student-id "student-1" :verdict :cleared} (store/background-check-of s "student-1"))))
      (testing "promotion finalization drafts a promotion record and advances the sequence"
        (store/commit-record! s {:effect :student/mark-promoted :path ["student-1"]})
        (is (= "JPN-PRM-000000" (get (first (store/promotion-history s)) "record_id")))
        (is (= "promotion-finalization-draft" (get (first (store/promotion-history s)) "kind")))
        (is (true? (:promoted? (store/student s "student-1"))))
        (is (= 1 (count (store/promotion-history s))))
        (is (= 1 (store/next-promotion-sequence s "JPN")))
        (is (true? (store/student-already-promoted? s "student-1")))
        (is (false? (store/student-already-promoted? s "student-2"))))
      (testing "safeguarding-record finalization drafts a record and advances the sequence"
        (store/commit-record! s {:effect :student/mark-recorded :path ["student-1"]})
        (is (= "JPN-SFG-000000" (get (first (store/safeguarding-history s)) "record_id")))
        (is (= "safeguarding-record-finalization-draft" (get (first (store/safeguarding-history s)) "kind")))
        (is (true? (:safeguarding-recorded? (store/student s "student-1"))))
        (is (= 1 (count (store/safeguarding-history s))))
        (is (= 1 (store/next-safeguarding-sequence s "JPN")))
        (is (true? (store/student-already-recorded? s "student-1")))
        (is (false? (store/student-already-recorded? s "student-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/student s "nope")))
    (is (= [] (store/all-students s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/promotion-history s)))
    (is (= [] (store/safeguarding-history s)))
    (is (zero? (store/next-promotion-sequence s "JPN")))
    (is (zero? (store/next-safeguarding-sequence s "JPN")))
    (store/with-students s {"x" {:id "x" :student-name "n" :current-class-size 25
                                 :maximum-class-size 30 :background-check-cleared? true
                                 :promoted? false :safeguarding-recorded? false
                                 :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:student-name (store/student s "x"))))))
