(ns rems.middleware.dev
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [prone.middleware :refer [wrap-exceptions]]
            [rems.auth.NotAuthorizedException]))

(defn wrap-some-exceptions
  "Wrap some exceptions in the prone.middleware/wrap-exceptions,
  but let others pass (i.e. `NotAuthorizedException`)."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch rems.auth.NotAuthorizedException e
        (throw e))
      (catch Throwable e
        ((wrap-exceptions (fn [& _] (throw e))) req)))))

(defn wrap-dev
  "Middleware for dev use. Autoreload, nicer errors."
  [handler]
  (-> handler
      wrap-reload
      wrap-some-exceptions))
