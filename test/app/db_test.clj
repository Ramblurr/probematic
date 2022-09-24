(ns app.db-test
  (:require [clojure.test :refer :all]
            [app.db :refer :all]))

(deftest parse-where-test
  (is (= (parse-where {:where {
                               :and [{:created {:is_null true}}
                                     {:count {:between [0 10]}}
                                     {:count {:gt 10}}
                                     {:foobar {:is_null false}}
                                     ]
                               :or  [
                                     {:company_name {:eq "Acme"}}
                                     {:company_name {:like "Ca%"}}]}})
         [[:and
           [:is :created nil]
           [:between :count [0 10]]
           [:> :count 10]
           [:is-not :foobar nil]
           ]
          [:or
           [:= :company_name "Acme"]
           [:like :company_name "Ca%"]]]))
  )


(deftest coalesce-key-test
  (let [m {:pet/id 1 :pet/name "Fido" :pet/owner_id 1 :owner/name "Alice" :owner/id 1}
        id-key :pet/owner_id
        child-key :pet/owner
        id-ns "owner"]
    (is (= (coalesce-key m id-key child-key id-ns)
           {:pet/id    1
            :pet/name  "Fido"
            :pet/owner {:owner/name "Alice"
                        :owner/id   1}}
           ))))