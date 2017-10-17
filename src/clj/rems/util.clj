(ns rems.util
  (:require [rems.context :as context]))

(defn select-values
  "Select values specified by give keys `ks`."
  [m ks]
  (vec (remove nil? (reduce #(conj %1 (m %2)) [] ks))))

(defn index-by
  "Index the collection coll with given keys `ks`.

  Result is a map indexed by the first key
  that contains a map indexed by the second key."
  [ks coll]
  (if (empty? ks)
    (first coll)
    (->> coll
         (group-by (first ks))
         (map (fn [[k v]] [k (index-by (rest ks) v)]))
         (into {}))))

(defn errorf
  "Throw a RuntimeException, args passed to `clojure.core/format`."
  [& fmt-args]
  (throw (RuntimeException. (apply format fmt-args))))

(defn get-user-id
  ([]
   (get-user-id context/*user*))
  ([user]
   (get user "eppn")))

(defn get-username
  ([]
   (get-username context/*user*))
  ([user]
   (get user "commonName")))

(defn get-user-mail
  ([]
   (get-user-mail context/*user*))
  ([user]
   (get user "mail")))

(defn get-theme-attribute
  "Fetch the attribute value from the current theme."
  [attr-name]
  (get context/*theme* attr-name))

(defn getx
  "Like `get` but throws an exception if the key is not found."
  [m k]
  (let [e (get m k ::sentinel)]
    (if-not (= e ::sentinel)
      e
      (throw (ex-info "Missing required key" {:map m :key k})))))

(defn getx-in
  "Like `get-in` but throws an exception if the key is not found."
  [m ks]
  (reduce getx m ks))
