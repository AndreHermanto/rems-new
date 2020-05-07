(ns ^:browser rems.test-browser
  "REMS Browser tests.

  For the test database, you need to run these tests in the :test profile to get the right :database-url and :port.

  For development development tests, you can run against a running instance with:

  (rems.browser-test-util/init-driver! :chrome \"http://localhost:3000/\" :development)

  NB: While adding more test helpers, please put the `driver` argument as first to match etaoin and enable `doto`."
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [com.rpl.specter :refer [select ALL]]
            [rems.config]
            [rems.db.test-data :as test-data]
            [rems.db.user-settings :as user-settings]
            [rems.standalone]
            [rems.browser-test-util :as btu]))

(comment ; convenience for development testing
  (btu/init-driver! :chrome "http://localhost:3000/" :development))

(use-fixtures :each btu/fixture-driver)

(use-fixtures :once btu/test-dev-or-standalone-fixture)

;;; common functionality

(defn login-as [driver username]
  (doto driver
    (btu/set-window-size 1400 7000) ; big enough to show the whole page in the screenshots
    (btu/go (btu/get-server-url))
    (btu/screenshot (io/file btu/reporting-dir "landing-page.png"))
    (btu/scroll-and-click {:css ".login-btn"})
    (btu/screenshot (io/file btu/reporting-dir "login-page.png"))
    (btu/scroll-and-click [{:class "users"} {:tag :a :fn/text username}])
    (btu/wait-visible :logout)
    (btu/screenshot (io/file btu/reporting-dir "logged-in.png"))))

(defn logout [driver]
  (doto driver
    (btu/scroll-and-click :logout)
    (btu/wait-visible {:css ".login-component"})))

(defn click-navigation-menu [driver link-text]
  (btu/scroll-and-click driver [:big-navbar {:tag :a :fn/text link-text}]))

(defn click-administration-menu [driver link-text]
  (btu/scroll-and-click driver [:administration-menu {:tag :a :fn/text link-text}]))

(defn go-to-catalogue [driver]
  (doto driver
    (click-navigation-menu "Catalogue")
    (btu/wait-visible {:tag :h1 :fn/text "Catalogue"})
    (btu/wait-page-loaded)
    (btu/screenshot (io/file btu/reporting-dir "catalogue-page.png"))))

(defn go-to-applications [driver]
  (doto driver
    (click-navigation-menu "Applications")
    (btu/wait-visible {:tag :h1 :fn/text "Applications"})
    (btu/wait-page-loaded)
    (btu/screenshot (io/file btu/reporting-dir "applications-page.png"))))

(defn go-to-admin-licenses [driver]
  (doto driver
    (click-administration-menu "Licenses")
    (btu/wait-visible {:tag :h1 :fn/text "Licenses"})
    (btu/wait-page-loaded)
    (btu/screenshot (io/file btu/reporting-dir "administration-licenses-page.png"))))

(defn go-to-admin-resources [driver]
  (doto driver
    (click-administration-menu "Resources")
    (btu/wait-visible {:tag :h1 :fn/text "Resources"})
    (btu/wait-page-loaded)
    (btu/screenshot (io/file btu/reporting-dir "administration-resources-page.png"))))

(defn change-language [driver language]
  (btu/scroll-and-click driver [{:css ".language-switcher"} {:fn/text (.toUpperCase (name language))}]))



;;; catalogue page

(defn add-to-cart [driver resource-name]
  (btu/scroll-and-click driver [{:css "table.catalogue"}
                            {:fn/text resource-name}
                            {:xpath "./ancestor::tr"}
                            {:css ".add-to-cart"}]))

(defn apply-for-resource [driver resource-name]
  (doto driver
    (btu/scroll-and-click [{:css "table.cart"}
                       {:fn/text resource-name}
                       {:xpath "./ancestor::tr"}
                       {:css ".apply-for-catalogue-items"}])
    (btu/wait-visible  {:tag :h1 :fn/has-text "Application"})
    (btu/wait-page-loaded)
    (btu/screenshot  (io/file btu/reporting-dir "application-page.png"))))



;;; application page

(defn fill-form-field
  "Fills a form field named by `label` with `text`.

  Optionally give `:index` when several items match. It starts from 1."
  [driver label text & [opts]]
  (assert (> (:index opts 1) 0) "indexing starts at 1") ; xpath uses 1, let's keep the convention though we don't use xpath here because it will likely not work

  (let [el (nth (btu/query-all driver [{:css ".fields"}
                                      {:tag :label :fn/has-text label}])
                (dec (:index opts 1)))
        id (btu/get-element-attr-el driver el :for)]
    ;; XXX: need to use `fill-human`, because `fill` is so quick that the form drops characters here and there
    (btu/fill-human driver {:id id} text)))

(defn set-date [driver label date]
  (let [id (btu/get-element-attr driver [{:css ".fields"}
                                        {:tag :label :fn/text label}]
                                :for)]
    ;; XXX: The date format depends on operating system settings and is unaffected by browser locale,
    ;;      so we cannot reliably know the date format to type into the date field and anyways WebDriver
    ;;      support for date fields seems buggy. Changing the field with JavaScript is more reliable.
    (btu/js-execute driver
                   ;; XXX: React workaround for dispatchEvent, see https://github.com/facebook/react/issues/10135
                   "
                function setNativeValue(element, value) {
                    const { set: valueSetter } = Object.getOwnPropertyDescriptor(element, 'value') || {}
                    const prototype = Object.getPrototypeOf(element)
                    const { set: prototypeValueSetter } = Object.getOwnPropertyDescriptor(prototype, 'value') || {}

                    if (prototypeValueSetter && valueSetter !== prototypeValueSetter) {
                        prototypeValueSetter.call(element, value)
                    } else if (valueSetter) {
                        valueSetter.call(element, value)
                    } else {
                        throw new Error('The given element does not have a value setter')
                    }
                }
                var field = document.getElementById(arguments[0])
                setNativeValue(field, arguments[1])
                field.dispatchEvent(new Event('change', {bubbles: true}))
                "
                   id date)))

(defn select-option [driver label option]
  (let [id (btu/get-element-attr driver [{:css ".fields"}
                                        {:tag :label :fn/has-text label}]
                                :for)]
    (btu/fill driver {:id id} (str option "\n"))))

(defn accept-licenses [driver]
  (doto driver
    (btu/scroll-and-click :accept-licenses-button)
    (btu/wait-visible :has-accepted-licenses)))

(defn send-application [driver]
  (doto driver
    (btu/scroll-and-click :submit)
    (btu/wait-visible :status-success)
    (btu/wait-has-class :apply-phase "completed")))

(defn get-application-id [driver]
  (last (str/split (btu/get-url driver) #"/")))

(defn get-attachments
  ([driver]
   (get-attachments driver {:css "a.attachment-link"}))
  ([driver selector]
   (mapv (partial btu/get-element-text-el driver) (btu/query-all driver selector))))



;; applications page

(defn get-application-summary [driver application-id]
  (let [row (btu/query driver [{:css "table.my-applications"}
                              {:tag :tr :data-row application-id}])]
    {:id application-id
     :description (btu/get-element-text-el driver (btu/child driver row {:css ".description"}))
     :resource (btu/get-element-text-el driver (btu/child driver row {:css ".resource"}))
     :state (btu/get-element-text-el driver (btu/child driver row {:css ".state"}))}))

;;; tests

(deftest test-new-application
  (let [driver (btu/get-driver)]
    (btu/with-postmortem driver {:dir btu/reporting-dir}
      (login-as driver "alice")

      (testing "create application"
        (doto driver
          (go-to-catalogue)
          (add-to-cart "Default workflow")
          (apply-for-resource "Default workflow"))

        (let [application-id (get-application-id driver)
              application (:body
                           (http/get (str (btu/get-server-url) "/api/applications/" application-id)
                                     {:as :json
                                      :headers {"x-rems-api-key" "42"
                                                "x-rems-user-id" "handler"}}))
              form-id (get-in application [:application/forms 0 :form/id])
              description-field-id (get-in application [:application/forms 0 :form/fields 1 :field/id])
              description-field-selector (keyword (str "form-" form-id "-field-" description-field-id))
              attachment-field (get-in application [:application/forms 0 :form/fields 7])
              attachment-field-selector (keyword (str "form-" form-id "-field-" (:field/id attachment-field) "-input"))]
          (is (= "attachment" (:field/type attachment-field))) ;; sanity check

          (doto driver
            (fill-form-field "Application title field" "Test name")
            (fill-form-field "Text field" "Test")
            (fill-form-field "Text area" "Test2")
            (set-date "Date field" "2050-01-02")
            (fill-form-field "Email field" "user@example.com")
            (btu/upload-file attachment-field-selector "test-data/test.txt")
            (btu/wait-predicate #(= ["test.txt"] (get-attachments driver))))

          (is (not (btu/field-visible? driver "Conditional field"))
              "Conditional field is not visible before selecting option")

          (doto driver
            (select-option "Option list" "First option")
            (btu/wait-predicate #(btu/field-visible? driver "Conditional field"))
            (fill-form-field "Conditional field" "Conditional")
            ;; pick two options for the multi-select field:
            (btu/check-box "Option2")
            (btu/check-box "Option3")
            ;; leave "Text field with max length" empty
            ;; leave "Text are with max length" empty

            (accept-licenses)
            (send-application))

          (is (= "Applied" (btu/get-element-text driver :application-state)))

          (testing "check a field answer"
            (is (= "Test name" (btu/get-element-text driver description-field-selector))))

          (testing "see application on applications page"
            (go-to-applications driver)
            (let [summary (get-application-summary driver application-id)]
              (is (= "Default workflow" (:resource summary)))
              (is (= "Applied" (:state summary)))
              ;; don't bother trying to predict the external id:
              (is (.contains (:description summary) "Test name"))))

          (testing "fetch application from API"
            (let [application (:body
                               (http/get (str (btu/get-server-url) "/api/applications/" application-id)
                                         {:as :json
                                          :headers {"x-rems-api-key" "42"
                                                    "x-rems-user-id" "handler"}}))
                  attachment-id (get-in application [:application/attachments 0 :attachment/id])]
              (testing "attachments"
                (is (= [{:attachment/id attachment-id
                         :attachment/filename "test.txt"
                         :attachment/type "text/plain"}]
                       (:application/attachments application))))
              (testing "applicant information"
                (is (= "alice" (get-in application [:application/applicant :userid])))
                (is (= (set (map :license/id (:application/licenses application)))
                       (set (get-in application [:application/accepted-licenses :alice])))))
              (testing "form fields"
                (is (= "Test name" (:application/description application)))
                (is (= [["label" ""]
                        ["description" "Test name"]
                        ["text" "Test"]
                        ["texta" "Test2"]
                        ["header" ""]
                        ["date" "2050-01-02"]
                        ["email" "user@example.com"]
                        ["attachment" (str attachment-id)]
                        ["option" "Option1"]
                        ["text" "Conditional"]
                        ["multiselect" "Option2 Option3"]
                        ["label" ""]
                        ["text" ""]
                        ["texta" ""]]
                       (for [field (select [:application/forms ALL :form/fields ALL] application)]
                         ;; TODO could test other fields here too, e.g. title
                         [(:field/type field)
                          (:field/value field)]))))
              (testing "after navigating to the application view again"
                (btu/scroll-and-click driver [{:css "table.my-applications"}
                                          {:tag :tr :data-row application-id}
                                          {:css ".btn-primary"}])
                (btu/wait-visible driver {:tag :h1 :fn/has-text "Application"})
                (btu/wait-page-loaded driver)
                (testing "check a field answer"
                  (is (= "Test name" (btu/get-element-text driver description-field-selector))))))))))))

(deftest test-handling
  (let [driver (btu/get-driver)
        applicant "alice"
        handler "developer"
        form-id (test-data/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus"}
                                                        :field/optional false
                                                        :field/type :description}]})
        catalogue-id (test-data/create-catalogue-item! {:form-id form-id})
        application-id (test-data/create-draft! applicant
                                                [catalogue-id]
                                                "test-handling")]
    (test-data/command! {:type :application.command/submit
                         :application-id application-id
                         :actor applicant})
    (btu/with-postmortem driver {:dir btu/reporting-dir}
      (login-as driver handler)
      (testing "handler should see todos on logging in"
        (btu/wait-visible driver :todo-applications))
      (testing "handler should see description of application"
        (btu/wait-visible driver {:class :application-description :fn/text "test-handling"}))
      (let [app-button {:tag :a :href (str "/application/" application-id)}]
        (testing "handler should see view button for application"
          (btu/wait-visible driver app-button))
        (btu/scroll-and-click driver app-button))
      (testing "handler should see application after clicking on View"
        (btu/wait-visible driver {:tag :h1 :fn/has-text "test-handling"}))
      (testing "open the approve form"
        (btu/scroll-and-click driver :approve-reject-action-button))
      (testing "add a comment and two attachments"
        (doto driver
          (btu/wait-visible :comment-approve-reject)
          (btu/fill-human :comment-approve-reject "this is a comment")
          (btu/upload-file :upload-approve-reject-input "test-data/test.txt")
          (btu/wait-visible [{:css "a.attachment-link"}])
          (btu/upload-file :upload-approve-reject-input "test-data/test-fi.txt")
          (btu/wait-predicate #(= ["test.txt" "test-fi.txt"]
                                  (get-attachments driver)))))
      (testing "add and remove a third attachment"
        (btu/upload-file driver :upload-approve-reject-input "resources/public/img/rems_logo_en.png")
        (btu/wait-predicate driver #(= ["test.txt" "test-fi.txt" "rems_logo_en.png"]
                                       (get-attachments driver)))
        (let [buttons (btu/query-all driver {:css "button.remove-attachment-approve-reject"})]
          (btu/click-el driver (last buttons)))
        (btu/wait-predicate driver #(= ["test.txt" "test-fi.txt"]
                                       (get-attachments driver))))
      (testing "approve"
        (btu/scroll-and-click driver :approve)
        (btu/wait-predicate driver #(= "Approved" (btu/get-element-text driver :application-state))))
      (testing "attachments visible in eventlog"
        (is (= ["test.txt" "test-fi.txt"]
               (get-attachments driver {:css "div.event a.attachment-link"})))))))

(deftest test-guide-page
  (let [driver (btu/get-driver)]
    (btu/with-postmortem driver {:dir btu/reporting-dir}
      (btu/go driver (str (btu/get-server-url) "guide"))
      (btu/wait-visible driver {:tag :h1 :fn/text "Component Guide"})
      ;; if there is a js exception, nothing renders, so let's check
      ;; that we have lots of examples in the dom:
      (is (< 60 (count (btu/query-all driver {:class :example})))))))

(deftest test-language-change
  (let [driver (btu/get-driver)]
    (btu/with-postmortem driver {:dir btu/reporting-dir}
      (testing "default language is English"
        (doto driver
          (btu/go (btu/get-server-url))
          (btu/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
          (login-as "alice")
          (btu/wait-visible {:tag :h1 :fn/text "Catalogue"})
          (btu/wait-page-loaded)))

      (testing "changing language while logged out"
        (doto driver
          (logout)
          (btu/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
          (change-language :fi)
          (btu/wait-visible {:tag :h1 :fn/text "Tervetuloa REMSiin"})))

      (testing "changed language must persist after login"
        (doto driver
          (login-as "alice")
          (btu/wait-visible {:tag :h1 :fn/text "Aineistoluettelo"})
          (btu/wait-page-loaded)))

      (testing "wait for language change to show in the db"
        (btu/wait-predicate driver #(= :fi (:language (user-settings/get-user-settings "alice")))))

      (testing "changed language must have been saved for user"
        (doto driver
          (logout)
          (change-language :en)
          (btu/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
          (btu/delete-cookies)
          (login-as "alice")
          (btu/wait-visible {:tag :h1 :fn/text "Aineistoluettelo"})))

      (testing "changing language while logged i"
        (change-language driver :en)
        (btu/wait-visible driver {:tag :h1 :fn/text "Catalogue"}))
      (is true)))) ; avoid no assertions warning

;; TODO: driver is passed to scroll-and-click, maybe create higher level overload with shorter name too

(defn slurp-fields [driver selector]
  (->> (for [row (btu/query-all driver [selector {:fn/has-class :row}])
             :let [k (btu/get-element-text-el driver (btu/child driver row {:tag :label}))
                   v (btu/get-element-text-el driver (btu/child driver row {:css ".form-control"}))]]
         [k (str/trim v)])
       (into {})))


(defn create-license [driver]
  (btu/with-postmortem driver {:dir btu/reporting-dir}
    (doto driver
      (go-to-admin-licenses)
      (btu/scroll-and-click  :create-license)
      (btu/wait-visible  {:tag :h1 :fn/text "Create license"})
      (select-option  "Organization" "nbn")
      (btu/scroll-and-click :licensetype-link)
      (fill-form-field "License name" (str (:license-name @btu/test-context) " EN") {:index 1})
      (fill-form-field "License link" "https://www.csc.fi/home" {:index 1})
      (fill-form-field "License name" (str (:license-name @btu/test-context) " FI") {:index 2})
      (fill-form-field "License link" "https://www.csc.fi/etusivu" {:index 2})
      (fill-form-field "License name" (str (:license-name @btu/test-context) " SV") {:index 3})
      (fill-form-field "License link" "https://www.csc.fi/home" {:index 3})
      (btu/screenshot (io/file btu/reporting-dir "about-to-create-license.png"))
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "License"})
      (btu/wait-page-loaded)
      (btu/screenshot (io/file btu/reporting-dir "created-license.png")))
    (is (str/includes? (btu/get-element-text driver {:css ".alert-success"}) "Success"))
    (is (= {"Organization" "nbn"
            "Title (EN)" (str (:license-name @btu/test-context) " EN")
            "Title (FI)" (str (:license-name @btu/test-context) " FI")
            "Title (SV)" (str (:license-name @btu/test-context) " SV")
            "Type" "link"
            "External link (EN)" "https://www.csc.fi/home"
            "External link (FI)" "https://www.csc.fi/etusivu"
            "External link (SV)" "https://www.csc.fi/home"
            "Active" ""}
           (slurp-fields driver :license)))))

(defn create-resource [driver]
  (btu/with-postmortem driver {:dir btu/reporting-dir}
    (doto driver
      (go-to-admin-resources)
      (btu/scroll-and-click :create-resource)
      (btu/wait-visible {:tag :h1 :fn/text "Create resource"})
      (select-option "Organization" "nbn")
      (fill-form-field "Resource identifier" (:resid @btu/test-context))
      (select-option "License" (str (:license-name @btu/test-context) " EN"))
      (btu/screenshot (io/file btu/reporting-dir "about-to-create-resource.png"))
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Resource"})
      (btu/wait-page-loaded)
      (btu/screenshot (io/file btu/reporting-dir "created-resource.png")))
    (is (str/includes? (btu/get-element-text driver {:css ".alert-success"}) "Success"))
    (is (= {"Organization" "nbn"
            "Resource" (:resid @btu/test-context)
            "Active" ""}
           (slurp-fields driver :resource)))
    (is (= (str "License \"" (:license-name @btu/test-context) " EN\"")
           (btu/get-element-text driver [:licenses {:class :license-title}])))))

(deftest test-create-catalogue-item
  (let [driver (btu/get-driver)]
    (btu/with-postmortem driver {:dir btu/reporting-dir}
      (login-as driver "owner")
      (swap! btu/test-context assoc
             :license-name (str "Browser Test License " (btu/get-seed))
             :resid (str "browser.testing.resource/" (btu/get-seed)))
      (testing "create license"
        (create-license driver))
      (testing "create resource"
        (create-resource driver))
      (testing "create form") ; TODO
      (testing "create workflow") ; TODO
      (testing "create catalogue item")))) ; TODO
