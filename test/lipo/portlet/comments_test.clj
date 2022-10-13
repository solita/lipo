(ns lipo.portlet.comments-test
  (:require [lipo.portlet.comments :as c]
            [clojure.test :as t :refer [deftest is testing]]
            [xtdb.api :as xt]
            [lipo.db :as db]
            [ripley.live.protocols :as p]
            [lipo.test-util :refer :all]))


(t/use-fixtures :each in-memory-db-fixture)

(deftest add-comments-source
  (let [source (c/comments-source *node* "testpage")]

    (testing "Initially comments are empty"
      (is (empty? (p/current-value source))))

    (testing "Putting a new comment changes source"
      (db/put! *node* {:xt/id {:user/id "tester"}
                       :user/given-name "Unit"
                       :user/family-name "Testing"
                       :user/email "unit.testing@example.com"})

      (c/add-comment! *node* {:user/id "tester"} "testpage" "this is my comment")
      (wait-for
       #(let [[[c] :as comments] (p/current-value source)]
          (and (= 1 (count comments))
               (= "unit.testing@example.com" (get-in c [:comment/author :user/email]))
               (= "this is my comment" (:comment/text c))))
       "Source value changes to include the comment"))))
