(ns rems.db.core
  {:ns-tracker/resource-deps ["sql/queries.sql"]}
  (:require [clj-time.core :as time]
            [clj-time.jdbc] ;; convert db timestamps to joda-time objects
            [clojure.java.jdbc]
            [clojure.set :refer [superset?]]
            [conman.core :as conman]
            [mount.core :refer [defstate] :as mount]
            [rems.config :refer [env]]))

(defstate ^:dynamic *db*
  :start (let [db (cond (:test (mount/args)) (conman/connect! {:jdbc-url (:test-database-url env)})
                        (:database-url env) (conman/connect! {:jdbc-url (:database-url env)})
                        (:database-jndi-name env) {:name (:database-jndi-name env)}
                        :else (throw (IllegalArgumentException. ":database-url or :database-jndi-name must be configured")))]
           (try
             (with-open [_ (clojure.java.jdbc/get-connection db)]
               nil)
             (catch Exception e
               (throw (IllegalArgumentException.
                       (str "Can not connect to database "
                            (pr-str db)
                            ". :database-name or :database-jndi-name invalid.")
                       e))))
           db)
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(defn assert-test-database! []
  (assert (= {:current_database "rems_test"}
             (get-database-name))))

(defn contains-all-kv-pairs? [supermap map]
  (superset? (set supermap) (set map)))

(defn apply-filters [filters coll]
  (let [filters (or filters {})]
    (filter #(contains-all-kv-pairs? % filters) coll)))

(defn now-active?
  ([start end]
   (now-active? (time/now) start end))
  ([now start end]
   (and (or (nil? start)
            (not (time/before? now start)))
        (or (nil? end)
            (time/before? now end)))))

(defn assoc-expired
  "Calculates and assocs :expired attribute based on current time and :start and :end attributes.

   Current time can be passed in optionally."
  ([x]
   (assoc-expired (time/now) x))
  ([now x]
   (assoc x :expired (not (now-active? now (:start x) (:end x))))))
