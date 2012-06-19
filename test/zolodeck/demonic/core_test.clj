(ns zolodeck.demonic.core-test
  (:use [clojure.test :only [run-tests deftest is are testing]]
        [zolodeck.demonic.core :only [init-db in-demarcation run-query delete] :as demonic]
        [zolodeck.demonic.helper :only [DATOMIC-TEST]]
        [zolodeck.demonic.test-schema]
        [zolodeck.demonic.test]
        [zolodeck.utils.debug]
        [zolodeck.utils.test]))

(init-db "datomic:mem://demonic-test" TEST-SCHEMA-TX)

(defn cleanup-siva []
  (testing "cleanup of database"
    (demonic/in-demarcation
     (if-let [e (find-by-fb-id (:id SIVA-FB))]
       (demonic/delete e))
     (is (nil? (:db/id (find-by-fb-id (:id SIVA-FB))))))))

(defn number-of-users-in-datomic []
  (count (demonic/run-query '[:find ?u :where [?u :user/first-name]])))

(defn load-siva-from-db []
  (find-by-fb-id (:id SIVA-FB)))

(demonictest demonictest-without-assertions
  (cleanup-siva)
  (demonic/insert SIVA-DB)
  (is (not (nil? (:db/id (load-siva-from-db))))))

(deftest test-demonictest
  (testing "demonictests should not affect the db"
    (demonictest-without-assertions)
    (demonic/in-demarcation   
     (is (nil? (:db/id (load-siva-from-db)))))))

(deftest test-demonictesting
  (cleanup-siva)
  (testing "demonictesting should not affect the db"
    (demonic/in-demarcation   
      (is (nil? (:db/id (load-siva-from-db)))))
    (demonic-testing "inserting siva, to later check that it wasnt really inserted"
      (demonic/insert SIVA-DB)
      (is (not (nil? (:db/id (load-siva-from-db))))))
    (demonic/in-demarcation   
      (is (nil? (:db/id (load-siva-from-db)))))))

(deftest test-datomic-test-infra
  (testing "nothing exists to start"
    (demonic/in-demarcation   
     (is (nil? (:db/id (find-by-fb-id "10000"))))))

  (testing "within a test, you can CRUD correctly"
    (binding [DATOMIC-TEST true]
      (demonic/in-demarcation   
       (is (nil? (:db/id (find-by-fb-id "10000"))))
       (demonic/insert (assoc SIVA-DB :user/fb-id "10000"))
       (is (not (nil? (:db/id (find-by-fb-id "10000"))))))))

  (testing "after a test, DB is restored"
    (demonic/in-demarcation
     (is (nil? (:db/id (find-by-fb-id "10000")))))))

(deftest test-inserting-a-nil
  (testing "should not throw any exception. It should just return nil"
    (is (nil? (demonic/insert nil)))))

(deftest test-new-user-persistence
  (cleanup-siva)
  
  (testing "regular demarcations do persist at the end"
    (demonic/in-demarcation   
     (is (nil? (:db/id (load-siva-from-db))))
     (demonic/insert SIVA-DB)
     (is (not (nil? (:db/id (load-siva-from-db)))))))

  (testing "regular demarcations are permanent"
    (demonic/in-demarcation   
     (is (not (nil? (:db/id (load-siva-from-db)))))))

  (cleanup-siva))

(deftest test-user-has-a-wife-persistence
  (cleanup-siva)
  (demonic-testing "can persist siva and his wife"
    (let [siva-graph (assoc SIVA-DB :user/wife HARINI-DB)]
      (demonic/insert siva-graph))
    (is (not (nil? (:db/id (load-siva-from-db)))))
    (is (not (nil? (:db/id (find-by-first-name (:user/first-name HARINI-DB))))))))

(deftest test-user-has-a-wife-re-insertion
  (cleanup-siva)
  (demonic-testing "can persist siva, his wife, not once but multiple times"
    (let [siva-graph (assoc SIVA-DB :user/wife HARINI-DB)
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)
          _ (demonic/insert siva-loaded)
          siva-reloaded (load-siva-from-db)]
      (is (not (nil? (get-in siva-loaded [:user/wife :db/id]))))
      (is (not (nil? (get-in siva-reloaded [:user/wife :db/id]))))
      (is (= (get-in siva-loaded [:user/wife :db/id])
             (get-in siva-reloaded [:user/wife :db/id])))
      (is (= 2 (number-of-users-in-datomic))))))

(deftest test-user-has-friends-persistence
  (cleanup-siva)
  (demonic-testing "can persist siva and his friends"
    (let [siva-graph (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB])]
      (demonic/insert siva-graph))
    (is (not (nil? (:db/id (load-siva-from-db)))))
    (is (= 2 (count (:user/friends (load-siva-from-db)))))
    (is (not (nil? (:db/id (find-by-first-name (:user/first-name AMIT-DB))))))
    (is (not (nil? (:db/id (find-by-first-name (:user/first-name DEEPTHI-DB))))))))

(deftest test-user-has-friends-re-insertion
  (cleanup-siva)
  (demonic-testing "can persist siva and his friends"
    (let [siva-graph (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB])
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)
          _ (demonic/insert siva-loaded)
          siva-reloaded (load-siva-from-db)]
      (is (= 2 (count (:user/friends (siva-loaded)))))
      (is (= 2 (count (:user/friends (siva-reloaded)))))
      (is-same-sequence? (map :db/id (:user/friends siva-loaded))
                         (map :db/id (:user/friends siva-reloaded)))
      (is (= 3 (number-of-users-in-datomic))))))

(deftest test-user-has-friends-addition
  (cleanup-siva)
  (demonic-testing "can persist siva and his friends"
    (let [siva-graph (assoc SIVA-DB :user/friends [AMIT-DB])
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)]
      (is (= 1 (count (:user/friends siva-loaded))))
      (is (= 2 (number-of-users-in-datomic)))

      (let [siva-graph (assoc siva-loaded :user/friends (conj  (:user/friends siva-loaded) DEEPTHI-DB))
            _ (demonic/insert siva-graph)
            siva-reloaded (load-siva-from-db)]
        (is (= 2 (count (:user/friends siva-reloaded))))
        (is (= 3 (number-of-users-in-datomic)))))))

(deftest test-user-has-friends-removal
  (cleanup-siva)
  (demonic-testing "can persist siva and his friends"
    (let [siva-graph (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB])
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)]
      (is (= 2 (count (:user/friends siva-loaded))))      
      (is (= 3 (number-of-users-in-datomic)))
      
      (let [siva-graph (assoc siva-loaded :user/friends (take 1 (:user/friends siva-loaded)))
            _ (demonic/insert siva-graph)
            siva-reloaded (load-siva-from-db)]
        (is (= 1 (count (:user/friends siva-reloaded))))
        (is (= 2 (number-of-users-in-datomic)))

        (let [siva-graph (assoc siva-loaded :user/friends nil)
              _ (demonic/insert siva-graph)
              siva-reloaded (load-siva-from-db)]
          (is (empty? (:user/friends siva-reloaded)))
          (is (= 1 (number-of-users-in-datomic))))))))

(deftest test-user-has-friends-replacement
  (cleanup-siva)
  (demonic-testing "can persist siva and his friends"
    (let [siva-graph (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB])
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)]
      (is (= 2 (count (:user/friends siva-loaded))))
      (is (= 3 (number-of-users-in-datomic)))

      (let [siva-graph (assoc siva-loaded :user/friends [ADI-DB ALEKHYA-DB])
            _ (demonic/insert siva-graph)
            siva-reloaded (load-siva-from-db)]
        (is (nil? (:db/id (find-by-first-name (:user/first-name AMIT-DB)))))
        (is (nil? (:db/id (find-by-first-name (:user/first-name DEEPTHI-DB)))))
        (is (not (nil? (:db/id (find-by-first-name (:user/first-name ADI-DB))))))
        (is (not (nil? (:db/id (find-by-first-name (:user/first-name ALEKHYA-DB))))))
        (is (= 2 (count (:user/friends siva-reloaded))))
        (is (= 3 (number-of-users-in-datomic)))))))

(deftest test-user-has-a-wife-and-friends-persistence
  (cleanup-siva)
  (demonic-testing "can persist siva, his wife, and his friends"
    (let [siva-graph (-> SIVA-DB 
                         (assoc :user/wife HARINI-DB)
                         (assoc :user/friends [AMIT-DB DEEPTHI-DB]))]
      (demonic/insert siva-graph))     
    (is (not (nil? (:db/id (load-siva-from-db)))))
    (is (not (nil? (:db/id (find-by-first-name (:user/first-name HARINI-DB))))))
    (is (not (nil? (:db/id (find-by-first-name (:user/first-name AMIT-DB))))))
    (is (not (nil? (:db/id (find-by-first-name (:user/first-name DEEPTHI-DB))))))))

(deftest test-user-has-a-wife-and-friends-re-insertion
  (cleanup-siva)
  (demonic-testing "can persist siva, his wife, and his friends"
    (let [siva-graph (-> SIVA-DB 
                         (assoc :user/wife HARINI-DB)
                         (assoc :user/friends [AMIT-DB DEEPTHI-DB]))
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)
          _ (demonic/insert siva-loaded)
          siva-reloaded (load-siva-from-db)]
      (is (= (get-in siva-loaded [:user/wife :db/id])
             (get-in siva-reloaded [:user/wife :db/id])))      
      (is-same-sequence? (map :db/id (:user/friends siva-loaded))
                         (map :db/id (:user/friends siva-reloaded)))
      (is (= 4 (number-of-users-in-datomic))))))

(deftest test-graph-loads
  (cleanup-siva)
  (demonic-testing "can load up siva's graph"
    (demonic/insert (-> SIVA-DB 
                        (assoc :user/wife HARINI-DB)
                        (assoc :user/friends [AMIT-DB DEEPTHI-DB])))
    (let [siva (load-siva-from-db)]
      (is (= (:user/fb-email SIVA-DB) (:user/fb-email siva)))
      (is (= 2 (count (:user/friends siva))))
      (is (= (set [(:user/first-name AMIT-DB) (:user/first-name DEEPTHI-DB)])
             (set (map :user/first-name (:user/friends siva)))))
      (is (= HARINI-DB (select-keys (:user/wife siva) [:user/first-name :user/last-name]))))))

(deftest test-loadable
  (cleanup-siva)
  (demonic-testing "loadble behave like maps"
    (demonic/insert (-> SIVA-DB 
                        (assoc :user/wife HARINI-DB)
                        (assoc :user/friends [AMIT-DB DEEPTHI-DB])))
    (let [siva (load-siva-from-db)]
      (is (= (merge SIVA-DB {:a 1})
             (merge (select-keys siva (keys SIVA-DB)) {:a 1})))
      (is (= (assoc SIVA-DB :a 1)
             (assoc (select-keys siva (keys SIVA-DB)) :a 1)))
      (is (= (merge HARINI-DB {:a 1})
             (merge (select-keys (:user/wife siva) (keys HARINI-DB)) {:a 1})))
      (is (= zolodeck.demonic.loadable.Loadable (class (:user/wife siva))))
      (is (= (list zolodeck.demonic.loadable.Loadable zolodeck.demonic.loadable.Loadable) (map class (:user/friends siva))))
      (is (= (list zolodeck.demonic.loadable.Loadable zolodeck.demonic.loadable.Loadable) (map class (siva :user/friends)))))))