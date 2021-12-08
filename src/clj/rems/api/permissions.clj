(ns rems.api.permissions
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :as api-util] ; required for route :roles
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.entitlements :as entitlements]
            [rems.config :as config]
            [rems.common.roles :refer [has-roles?]]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetPermissionsResponse
  {:ga4gh_passport_v1 [s/Str]})

(def permissions-api
  (routes
   (context "/jwk" []
     :tags ["permissions"]
     (GET "/" []
       :summary "Experimental. Get JSON Web Key Set (JWKS) (RFC 7517) containing the keys used for signing GA4GH Visas."
       :return s/Any
       (if (not (:enable-permissions-api config/env))
         (not-implemented "permissions api not implemented")
         (let [key (:ga4gh-visa-public-key config/env)]
           (assert key ":ga4gh-visa-public-key not defined in config!")
           (ok {:keys [key]})))))
   (context "/permissions" []
     :tags ["permissions"]
     (GET "/:user" []
       ;; We're trying to replicate https://github.com/CSCfi/elixir-rems-proxy/#get-permissionsusername here
       :summary (str "Experimental. Returns user's permissions in ga4gh visa format. "
                     "See also https://github.com/CSCfi/rems/blob/master/docs/ga4gh-visas.md")
       :path-params [user :- (describe s/Str "return permissions for this user, required")]
       :query-params [{expired :- (describe s/Bool "whether to include expired permissions") false}]
       :return GetPermissionsResponse
       (if (not (:enable-permissions-api config/env))
         (not-implemented "permissions api not implemented")
         (cond
           (empty? user)
           (api-util/not-found-json-response)

           (not (or (has-roles?  :handler :owner :organization-owner :reporter)
                    (= user (getx-user-id))))
           (throw-forbidden)

           :else
           (ok (entitlements/get-entitlements-for-permissions-api user nil expired))))))))
