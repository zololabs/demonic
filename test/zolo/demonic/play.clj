(ns zolo.demonic.play
  (:use [zolo.demonic.test-schema]
        [zolo.demonic.test]
        [zolo.utils.debug]
        clojure.pprint)
  (:require [zolo.demonic.core :as demonic]
            [zolo.demonic.helper :as devil]))

(defn init-db []
  (demonic/init-db "datomic:mem://demonic-test" TEST-SCHEMA-TX))

(defn setup []
  (demonic/insert (-> SIVA-DB
                      (assoc :user/friends [AMIT-DB])
                      (assoc :user/wife HARINI-DB))))

(defn base []
  (demonic-testing "Base setup"
    (setup)

   (print-vals "INSERTING AGAIN")
   (demonic/insert (find-by-fb-id (:id SIVA-FB)))

   (let [siva-loaded (find-by-fb-id (:id SIVA-FB))]
     (print-vals "Loaded SIVA:" siva-loaded)
     (print-vals "Loaded SIVA friends:" (:user/friends siva-loaded))
     (print-vals "Loaded SIVA wife:" (:user/wife siva-loaded))
     (print-vals "List of users:" (demonic/run-query '[:find ?u :where [?u :user/first-name]])))

))
