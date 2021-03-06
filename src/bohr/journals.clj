;;;; Journals are data structures consisting of a name and a
;;;; function).  Each journal's function takes information about an
;;;; observation made by a Bohr observer and sends it to some other
;;;; place: a server, a database, a file,&c.

(ns bohr.journals
  (:require [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [clojure.string :as string])
  (:use bohr.observers
        bohr.config
        bohr.utils))

;; The set of Bohr journals.
;;
;; Keys should be journal names (strings) and values are the journal
;; functions themselves.
(def ^{:dynamic true} journals (atom {}))

;; Counter for the number of unique observations successfully
;; submitted (across all journals).
(def submissions  (atom 0))

;; Counter for the number of total observations successfully
;; submitted (across all journals).
(def publications (atom 0))

;; The set of journals disabled at runtime.
(def disabled-journals (atom (set [])))

(defn journal-count
  "The total number of journals."
  []
  (count @journals))

(defn journal-names
  "All journal names."
  []
  (keys @journals))

(defn journals?
  "Are there any journals?"
  []
  (< 0 (journal-count)))

(defn- scope-observation-name
  "Scope the name of an observation using the current prefix and suffix."
  [raw-name]
  (let [string-name (name raw-name)
        prefixed-string (string/join "." (filter identity [current-prefix string-name]))]
    (if current-suffix
      (format "%s%s" prefixed-string current-suffix)
      prefixed-string)))
        
(defn- scope-observation-options
  "Scope the options of an observation using the current description,
  units, and attributes."
  [options]
   {
    :desc  (get options :desc)
    :units (or (get options :units) current-units)
    :attrs (merge-configs
                 [current-attributes
                 (get options :attrs {})])
    })

(defn submit
  "Submit a single observation with the given `name` and `value`.

  Additional arguments will be interpreted as a map of options.  The
  following keys will be interpreted:

  - :prefix : add a prefix for the observation's name
  - :suffix : add a suffix for the observation's name
  - :units : set the observation's units
  - :attrs : set the observation's attributes

  The above options will be scoped appropriately.

  Additional key-value pairs will be passed to the journal's
  function."
  [name value & args]
  (let [observation-name    (scope-observation-name name)
        observation-options (scope-observation-options (apply hash-map args))]
    (if (allowed? observation-name (get-config :exclude-observations []) (get-config :include-observations []))
      (do
        (log/trace "Submitting observation" observation-name "with value" value "and options" observation-options)
        (doseq [[journal-name journal] (seq @journals)]
          (if (not (contains? @disabled-journals journal-name))
            (do
              (journal observation-name value observation-options)
              (swap! publications inc))))
        (swap! submissions inc))
      (log/trace "Skipping submitting observation" observation-name))))
    
(defn submit-many
  "Submit many observations.

  The argument `values` is a map of observation names to values.
  Additional arguments will be passed to the `submit` function."
  [values & args]
  (let [options (apply hash-map args)]
    (binding [current-prefix     (string/join "." (filter identity [current-prefix (get options :prefix)]))
              current-suffix     (string/join ""  (filter identity [current-suffix (get options :suffix)]))
              current-units      (get options :units current-units)
              current-attributes (merge-configs [current-attributes (get options :attrs {})])]
      (doseq [[name value] values]
        (if (and
             (map? value)
             (contains? value :value))
          (submit name (get value :value) :units (get value :units current-units) :desc (get value :desc) :attrs (get value :attrs current-attributes))
          (submit name value))))))

(defn define-journal!
  "Define a new journal with the given `name` and function `f`"
  [name f]
  (swap! journals assoc name f))

(defn console-journal
  "A function implementing a journal which prints all submitted
  observations to the console."
  [name value options]
  (println
   (format
    "%s\t%s\t%s\t%s\t%s"
    (time/now)
    name
    (formatted-attributes options)
    (formatted-value-with-units value options)
    (formatted-description options))))

(defn ensure-some-journal!
  "If no journals are defined, will define a journal `console` using
  the `console-journal` function."
  []
  (if (not (journals?))
    (do
      (log/debug "No journals defined, defaulting to \"console\" journal.")
      (define-journal! "console" console-journal))))

;; The publications made by the memory journal are held in memory in
;; this atom.
(def memory-journal-publications (atom []))

(defn memory-journal
  "A function implementing a journal which stores all submitted
  observations in memory."
  [name value options]
  (swap! memory-journal-publications conj [current-observer name value options]))

(defn memory-journal-publication-count
  "The number of publications made by the `memory-journal`."
  []
  (count @memory-journal-publications))

(defn memory-journal-publications?
  "Has the `memory-journal` made any publications?"
  []
  (< 0 (memory-journal-publication-count)))
