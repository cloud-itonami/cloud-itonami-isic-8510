(ns school.registry-test
  (:require [clojure.test :refer [deftest is]]
            [school.registry :as r]))

;; ----------------------------- class-size-exceeds-maximum? -----------------------------

(deftest not-exceeded-when-at-or-below-maximum
  (is (not (r/class-size-exceeds-maximum? {:current-class-size 30 :maximum-class-size 30})))
  (is (not (r/class-size-exceeds-maximum? {:current-class-size 25 :maximum-class-size 30}))))

(deftest exceeded-when-over-maximum
  (is (r/class-size-exceeds-maximum? {:current-class-size 31 :maximum-class-size 30}))
  (is (r/class-size-exceeds-maximum? {:current-class-size 32 :maximum-class-size 30})))

(deftest exceeds-is-false-on-missing-fields
  (is (not (r/class-size-exceeds-maximum? {})))
  (is (not (r/class-size-exceeds-maximum? {:current-class-size 32}))))

;; ----------------------------- register-promotion-finalization -----------------------------

(deftest promotion-finalization-is-a-draft-not-a-real-promotion
  (let [result (r/register-promotion-finalization "student-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest promotion-finalization-assigns-promotion-number
  (let [result (r/register-promotion-finalization "student-1" "JPN" 7)]
    (is (= (get result "promotion_number") "JPN-PRM-000007"))
    (is (= (get-in result ["record" "student_id"]) "student-1"))
    (is (= (get-in result ["record" "kind"]) "promotion-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest promotion-finalization-validation-rules
  (is (thrown? Exception (r/register-promotion-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-promotion-finalization "student-1" "" 0)))
  (is (thrown? Exception (r/register-promotion-finalization "student-1" "JPN" -1))))

;; ----------------------------- register-safeguarding-record-finalization -----------------------------

(deftest safeguarding-record-is-a-draft-not-a-real-record
  (let [result (r/register-safeguarding-record-finalization "student-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest safeguarding-record-assigns-safeguarding-number
  (let [result (r/register-safeguarding-record-finalization "student-1" "JPN" 3)]
    (is (= (get result "safeguarding_number") "JPN-SFG-000003"))
    (is (= (get-in result ["record" "student_id"]) "student-1"))
    (is (= (get-in result ["record" "kind"]) "safeguarding-record-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest safeguarding-record-validation-rules
  (is (thrown? Exception (r/register-safeguarding-record-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-safeguarding-record-finalization "student-1" "" 0)))
  (is (thrown? Exception (r/register-safeguarding-record-finalization "student-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-promotion-finalization "student-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-promotion-finalization "student-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-PRM-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-PRM-000001" (get-in hist2 [1 "record_id"])))))
