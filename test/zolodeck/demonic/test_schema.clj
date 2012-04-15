(ns zolodeck.demonic.test-schema
  (:use [zolodeck.demonic.schema]
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

(def TEST-SCHEMA-TX [
                (string-fact-schema :user/first-name true "A user's first name") 
                (string-fact-schema :user/last-name true "A user's last name") 
                (string-fact-schema :user/gender false "A user's gender") 
                (string-fact-schema :user/fb-id false "A user's Facebook ID") 
                (string-fact-schema :user/fb-auth-token false "A user's Facebook auth token")
                (string-fact-schema :user/fb-email false "A user's Facebook email") 
                (string-fact-schema :user/fb-link false "A user's Facebook link") 
                (string-fact-schema :user/fb-username false "A user's Facebook username")])

(defn find-by-fb-id [fb-id]
  (when fb-id
    (let [entity (-> (demonic/run-query '[:find ?u :in $ ?fb :where [?u :user/fb-id ?fb]]
                                        fb-id)
                     ffirst
                     demonic/load-entity)]
      (when (:db/id entity)
        entity))))