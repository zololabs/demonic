(ns zolo.demonic.test-schema
  (:use [zolo.utils.debug]
        [zolo.demonic.schema]
        [zolo.demonic.core :only [load-entity] :as demonic]))

(def SIVA-FB {:gender "male",
              :last_name "Jagadeesan",
              :link "http://www.facebook.com/sivajag",
              :timezone -7,
              :name "Siva Jagadeesan",
              :locale "en_US",
              :username "sivajag",
              :email "sivajag@gmail.com",
              :updated_time "2012-02-17T17:36:14+0000",
              :first_name "Siva",
              :verified true,
              :id "1014524783"})

(def SIVA-DB {:user/fb-id "1014524783",
              :user/first-name "Siva",
              :user/fb-email "sivajag@gmail.com",
              :user/fb-username "sivajag",
              :user/fb-link "http://www.facebook.com/sivajag",
              :user/last-name "Jagadeesan",
              :user/gender "male"})

(def AMIT-DB {:user/first-name "Amit"
              :user/last-name "Rathore"})

(def DEEPTHI-DB {:user/first-name "Deepthi"
                 :user/last-name "Somasunder"})

(def ADI-DB {:user/first-name "Aditya"
             :user/last-name "Rathore"})

(def HARINI-DB {:user/first-name "Harini"
                :user/last-name "Nambiraghavan"})

(def ALEKHYA-DB {:user/first-name "Alekhya"
                 :user/last-name "Jagadeesan"})

(def TEST-SCHEMA-TX [
   (uuid-fact-schema :user/guid "A GUID for the user" :uniqueness :db.unique/identity :index? true)

   (string-fact-schema :user/first-name "A user's first name" :fulltext? true :index? true) 
   (string-fact-schema :user/last-name  "A user's last name" :fulltext? true  :index? true) 
   (string-fact-schema :user/gender "A user's gender")   
   (string-fact-schema :user/fb-id "A user's Facebook ID") 
   (string-fact-schema :user/fb-auth-token "A user's Facebook auth token" :uniqueness :db.unique/value)
   (string-fact-schema :user/fb-email "A user's Facebook email" :uniqueness :db.unique/value) 
   (string-fact-schema :user/fb-link "A user's Facebook link" :uniqueness :db.unique/value) 
   (string-fact-schema :user/fb-username "A user's Facebook username" :uniqueness :db.unique/value)
   
   (string-fact-schema :friend/first-name "Friend's first name")
   (string-fact-schema :friend/last-name "Friend's first name")
   
  (ref-fact-schema :user/wife "A user's wife")
  (refs-fact-schema :user/friends "A users's friends")

  (enum-fact-schema :user/callsign "A user's call-sign")
  (enum-value-schema :callsign/eagle)
  (enum-value-schema :callsign/hawk)
   ])

(defn find-by-fb-id [fb-id]
  (when fb-id
    (let [entity (-> (demonic/run-query '[:find ?u :in $ ?fb :where [?u :user/fb-id ?fb]]
                                        fb-id)
                     ffirst
                     demonic/load-entity)]
      (when (:db/id entity)
        entity))))

(defn find-by-first-name [first-name]
  (let [entity (-> (demonic/run-query '[:find ?u :in $ ?fb :where [?u :user/first-name ?fb]]
                                      first-name)
                   ffirst
                   demonic/load-entity)]
    (when (:db/id entity)
      entity)))