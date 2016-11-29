(ns nlogn.database
  (:require [clojure.java.jdbc :as jdbc]
            [korma.core :as kc]
            [korma.db :as db]))

(defonce pg (atom nil))

(def db-spec (db/sqlite3 {:db "nlogn"
                          ;; :user user
                          ;; :password password
                          ;; :host host
                          ;; :port port
                          }))

(defn init-db! [user password host port]
  (swap!
   pg
   (fn [val]
     (if val val
         (db/defdb database (db/create-db db-spec))))))

(defn create-tables! []
  (jdbc/with-db-connection [db-con db-spec]
    (jdbc/db-do-commands
     db-spec
     (jdbc/create-table-ddl
      :posts
      [:post_id :string "PRIMARY KEY"]
      [:path :string]
      [:title :string]
      [:date :integer]
      [:tags :string]
      [:template :string]
      [:content :text]
      ))))

(kc/defentity posts
  (kc/database db-spec)
  (kc/pk :post_id)
  (kc/entity-fields :post_id :title :template :date :tags :content :path))

(comment
  (kc/insert
   posts
   (kc/values {:post_id "test-post" :path "/test" :title "Test Post" :content "Test!"}))

  (kc/select posts
             (kc/fields [:title])))

;; TODO Add templates
;; TODO Add pages
