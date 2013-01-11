(ns zolodeck.demonic.test-schema
  (:use [zolodeck.utils.debug]
        [zolodeck.demonic.schema]
        [zolodeck.demonic.core :only [load-entity] :as demonic]))

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
   (uuid-fact-schema :user/guid false "A GUID for the user" false false)
   (string-fact-schema :user/first-name true "A user's first name" false false) 
   (string-fact-schema :user/last-name true "A user's last name" false false) 
   (string-fact-schema :user/gender false "A user's gender" false false) 
   (string-fact-schema :user/fb-id false "A user's Facebook ID" false false) 
   (string-fact-schema :user/fb-auth-token false "A user's Facebook auth token" false false)
   (string-fact-schema :user/fb-email false "A user's Facebook email" false false) 
   (string-fact-schema :user/fb-link false "A user's Facebook link" false false) 
   (string-fact-schema :user/fb-username false "A user's Facebook username" false false)
   
   (string-fact-schema :friend/first-name true "Friend's first name" false false)
   (string-fact-schema :friend/last-name true "Friend's first name" false false)
   
   (ref-fact-schema :user/wife false "A user's wife" false false)
   (refs-fact-schema :user/friends false "A users's friends" false false)

   (enum-fact-schema :user/callsign false "A user's call-sign" false false)
   (enum-value-schema :callsign/eagle)
   (enum-value-schema :callsign/hawk)])

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