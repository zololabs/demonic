(ns zolo.demonic.core-test
  (:use [clojure.test :only [run-tests deftest is are testing]]
        [zolo.demonic.core :only [init-db in-demarcation run-query delete] :as demonic]
        [zolo.demonic.helper :only [DATOMIC-TEST]]
        [zolo.demonic.refs :only [process-graph]]        
        [zolo.demonic.test-schema]
        [zolo.demonic.test]
        [zolo.utils.debug]
        [zolo.utils.test]))

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

(deftest test-retract-attribute-entirely
  (cleanup-siva)
  (demonic-testing "can delete a simple attrib"
    (demonic/insert (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB]))
    (let [siva (load-siva-from-db)]
      (is (= (:user/fb-link SIVA-DB) (:user/fb-link siva)))
      (demonic/retract siva :user/fb-link)
      (demonic/retract siva :user/friends (:user/friends siva))
      (let [siva-reloaded (load-siva-from-db)]
        (is (nil? (:user/fb-link siva-reloaded)))
        (is (nil? (:user/friends siva-reloaded)))))))

(deftest test-retract-one-of-many
  (cleanup-siva)
  (demonic-testing "can delete on of a many cardinality attribute"
    (demonic/insert (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB]))
    (let [siva (load-siva-from-db)]
      (is (= 2 (count (:user/friends siva))))
      (demonic/retract siva :user/friends (first (:user/friends siva)))
      (let [siva-reloaded (load-siva-from-db)]
        (is (= 1 (count (:user/friends siva-reloaded))))))))

(deftest test-enum-type-persistence
  (cleanup-siva)
  (demonic-testing "enum types are saved right, and can be read back"
    (demonic/insert (->  SIVA-DB
                         (assoc :user/callsign :callsign/hawk)
                         (assoc :user/friends [(assoc AMIT-DB :user/callsign :callsign/eagle)])))
    (let [siva (load-siva-from-db)]
      (is (= :callsign/hawk (:user/callsign siva)))
      (is (= :callsign/eagle (-> siva :user/friends first :user/callsign))))))

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

(deftest test-user-can-append-friends
  (cleanup-siva)
  (demonic-testing "can persist append to siva's friends"
    (let [siva-graph (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB])]
      (demonic/insert siva-graph)
      (demonic/append-multiple (load-siva-from-db) :user/friends [ADI-DB ALEKHYA-DB]))
    (is (= 4 (count (:user/friends (load-siva-from-db)))))))

(deftest test-user-has-friends-re-insertion
  (cleanup-siva)
  (demonic-testing "can re-persist siva and his friends"
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
  (demonic-testing "can persist siva, and a friend, and then another friend"
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
  (demonic-testing "can persist siva, his friends, and then can remove them"
    (let [siva-graph (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB])
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)]
      (is (= 2 (count (:user/friends siva-loaded))))      
      (is (= 3 (number-of-users-in-datomic)))
      
      (let [siva-graph (assoc siva-loaded :user/friends (take 1 (:user/friends siva-loaded)))
            _ (demonic/insert siva-graph)
            siva-reloaded (load-siva-from-db)]
        (is (= 1 (count (:user/friends siva-reloaded))))
        (is (= 3 (number-of-users-in-datomic)))

        (let [siva-graph (assoc siva-loaded :user/friends nil)
              _ (demonic/insert siva-graph)
              siva-reloaded (load-siva-from-db)]
          (is (empty? (:user/friends siva-reloaded)))
          (is (= 3 (number-of-users-in-datomic))))))))

(deftest test-user-has-friends-replacement
  (cleanup-siva)
  (demonic-testing "can persist siva and his friends, and then replace them"
    (let [siva-graph (assoc SIVA-DB :user/friends [AMIT-DB DEEPTHI-DB])
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)]
      (is (= 2 (count (:user/friends siva-loaded))))
      (is (= 3 (number-of-users-in-datomic)))

      (let [siva-graph (assoc siva-loaded :user/friends [ADI-DB ALEKHYA-DB])
            _ (demonic/insert siva-graph)
            siva-reloaded (load-siva-from-db)]
        (is (not (nil? (:db/id (find-by-first-name (:user/first-name AMIT-DB))))))
        (is (not (nil? (:db/id (find-by-first-name (:user/first-name DEEPTHI-DB))))))
        (is (not (nil? (:db/id (find-by-first-name (:user/first-name ADI-DB))))))
        (is (not (nil? (:db/id (find-by-first-name (:user/first-name ALEKHYA-DB))))))
        (is (= 2 (count (:user/friends siva-reloaded))))
        (is (= 5 (number-of-users-in-datomic)))))))

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

(deftest test-user-has-a-friends-who-have-friends
  (cleanup-siva)
  (demonic-testing "can re-persist siva, his wife, and his friends"
    (let [deepthi-graph (assoc DEEPTHI-DB :user/friends [ADI-DB ALEKHYA-DB])
          siva-graph (-> SIVA-DB 
                         (assoc :user/friends [AMIT-DB deepthi-graph]))]
      (demonic/insert siva-graph))
    (let [siva-reloaded (load-siva-from-db)]
      (is (not (nil? (:db/id siva-reloaded))))
      (is (= 5 (number-of-users-in-datomic)))
      (let [deepthi-reloaded (-> siva-reloaded :user/friends second)]
        (demonic/insert (assoc deepthi-reloaded :user/gender "female"))))))

;;TODO Uncomment this once append is fixed
;; (deftest test-user-has-a-friends-who-have-friends-appending
;;   (cleanup-siva)
;;   (demonic-testing "can re-persist siva, his wife, and his friends"
;;     (let [deepthi-graph (assoc DEEPTHI-DB :user/friends [ADI-DB ALEKHYA-DB])
;;           siva-reloaded (demonic/insert-and-reload SIVA-DB)]
;;       (demonic/append-multiple siva-reloaded :user/friends [AMIT-DB deepthi-graph])      
;;       (let [siva-reloaded (load-siva-from-db)]
;;         (is (not (nil? (:db/id siva-reloaded))))
;;         (is (= 5 (number-of-users-in-datomic)))))))

(deftest test-user-has-a-wife-who-has-friends
  (cleanup-siva)
  (demonic-testing "can persist siva, wife with a friend, and friend with a friend"
    (let [harini-graph (assoc HARINI-DB :user/friends [ALEKHYA-DB])
          amit-graph (-> AMIT-DB
                         (assoc :user/wife DEEPTHI-DB)
                         (assoc :user/friends [ADI-DB]))
          siva-graph (-> SIVA-DB 
                         (assoc :user/wife harini-graph)
                         (assoc :user/friends [amit-graph]))]
      (demonic/insert siva-graph))
    (let [siva (load-siva-from-db)]
      (demonic/insert siva) ;;testing re-insertion
      (is (not (nil? (:db/id siva))))
      (is (= (:user/first-name HARINI-DB)
             (get-in siva [:user/wife :user/first-name])))
      (is (= (:user/first-name ALEKHYA-DB)
             (-> siva :user/wife :user/friends first :user/first-name)))
      (is (= (map :user/first-name [AMIT-DB])
             (map :user/first-name (:user/friends siva))))
      (is (= (:user/first-name DEEPTHI-DB)
             (-> siva :user/friends first :user/wife :user/first-name)))
      (is (= (:user/first-name ADI-DB)
             (-> siva :user/friends first :user/friends first :user/first-name))))
    (is (= 6 (number-of-users-in-datomic)))))

(demonictest test-subgraphs-can-be-updated-within-a-transaction
  (cleanup-siva)
  (let [siva-graph (assoc SIVA-DB :user/friends [DEEPTHI-DB HARINI-DB])]
    (demonic/insert siva-graph)
    (let [loaded-siva (load-siva-from-db)
          deepthi-graph (-> (-> loaded-siva :user/friends first)
                            (assoc :user/friends [ADI-DB]))
          harini-graph (-> (-> loaded-siva :user/friends last)
                           (assoc :user/friends [ALEKHYA-DB]))
          ;; siva-graph-2 (-> loaded-siva
          ;;                  (assoc :user/friends [deepthi-graph harini-graph]))
          ]
      (doall (map demonic/insert [deepthi-graph harini-graph]))
      (let [loaded-siva-2 (load-siva-from-db)]
        (map :user/friends (:user/friends loaded-siva-2))
        (demonic/insert loaded-siva-2)))))

(demonictest test-empty-change-graph-if-no-changes
  (cleanup-siva)
  (let [siva-graph (-> SIVA-DB
                       (assoc :user/wife (assoc HARINI-DB :user/friends [ALEKHYA-DB]))
                       (assoc :user/friends [(assoc AMIT-DB :user/friends [ADI-DB])
                                             (assoc DEEPTHI-DB :user/friends [ADI-DB])]))
        _ (demonic/insert siva-graph)
        siva-loaded (load-siva-from-db)
        changes (process-graph siva-loaded)]
    (is (empty? changes) "Should not have any change sets")))

(demonictest test-child-single-ref-attrib-change
  (testing "Wife changes her last name should have changeset"
    (cleanup-siva)
    (let [siva-graph (-> SIVA-DB
                         (assoc :user/wife (assoc HARINI-DB :user/friends [ALEKHYA-DB]))
                         (assoc :user/friends [(assoc AMIT-DB :user/friends [ADI-DB])
                                               (assoc DEEPTHI-DB :user/friends [ADI-DB])]))
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)
          siva-loaded (-> siva-loaded
                          (assoc :user/wife (assoc (:user/wife siva-loaded) :user/last-name "Jag")))
          changes (process-graph siva-loaded)]
      (is (= 1 (count changes)))
      (is (= "Jag" (:user/last-name (first changes))))
      (is (= (:db/id (:user/wife siva-loaded)) (:db/id (first changes)))))))

(demonictest test-child-single-ref-removal
  (testing "Siva before he got married ( bad times )"
    (cleanup-siva)
    (let [siva-graph (-> SIVA-DB
                         (assoc :user/wife (assoc HARINI-DB :user/friends [ALEKHYA-DB]))
                         (assoc :user/friends [(assoc AMIT-DB :user/friends [ADI-DB])
                                               (assoc DEEPTHI-DB :user/friends [ADI-DB])]))
          _ (demonic/insert siva-graph)
          siva-after-marriage (load-siva-from-db)
          siva-loaded (-> siva-after-marriage
                          (assoc :user/wife nil))
          changes (process-graph siva-loaded)]
      
      (is (= 1 (count changes)))
      
      (demonic/insert siva-loaded)
      (let [siva-before-marriage (load-siva-from-db)]
        (is (not (nil? (:user/wife siva-after-marriage))))
        (is (nil? (:user/wife siva-before-marriage)))))))

(demonictest test-child-multi-refs-attrib-change
  (testing "Friend changes last name should have changeset"
    (cleanup-siva)
    (let [siva-graph (-> SIVA-DB
                         (assoc :user/wife (assoc HARINI-DB :user/friends [ALEKHYA-DB]))
                         (assoc :user/friends [(assoc AMIT-DB :user/friends [ADI-DB])
                                               (assoc DEEPTHI-DB :user/friends [ADI-DB])]))
          _ (demonic/insert siva-graph)
          siva-loaded (load-siva-from-db)
          siva-loaded (-> siva-loaded
                          (assoc :user/friends
                            [ (assoc (first (:user/friends siva-loaded)) :user/last-name "Rathore2")
                              (second (:user/friends siva-loaded))]))
          changes (process-graph siva-loaded)]
      (is (= 1 (count changes)))
      (is (= "Rathore2" (:user/last-name (first changes)))))))

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
      (assoc siva :a 1)
      (dissoc siva :user/wife)
      (is (= (merge SIVA-DB {:a 1})
             (merge (select-keys siva (keys SIVA-DB)) {:a 1})))
      (is (= (assoc SIVA-DB :a 1)
             (assoc (select-keys siva (keys SIVA-DB)) :a 1)))
      (is (= (merge HARINI-DB {:a 1})
             (merge (select-keys (:user/wife siva) (keys HARINI-DB)) {:a 1})))
      (is (= zolo.demonic.loadable.Loadable (class (:user/wife siva))))
      (is (= (list zolo.demonic.loadable.Loadable zolo.demonic.loadable.Loadable) (map class (:user/friends siva))))
      (is (= (list zolo.demonic.loadable.Loadable zolo.demonic.loadable.Loadable) (map class (siva :user/friends)))))))