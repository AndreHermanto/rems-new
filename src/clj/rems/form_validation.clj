(ns rems.form-validation
  "Pure functions for form validation logic")

(defn- required? [field]
  (not (or (:field/optional field)
           (= :label (:field/type field)))))

(defn- too-long? [field]
  (and (:field/max-length field)
       (> (count (:field/value field)) (:field/max-length field))))

(defn- validate-field [field]
  (if (empty? (:field/value field))
    (when (required? field)
      {:field-id (:field/id field)
       :type :t.form.validation/required})
    (when (too-long? field)
      {:field-id (:field/id field)
       :type :t.form.validation/toolong})))

(defn validate-fields [fields]
  (->> (sort-by :field/id fields)
       (map validate-field)
       (remove nil?)
       (seq)))
