;;;; Observers are functions decorated with some options (see
;;;; below).  Each observer's function takes no arguments and returns
;;;; a value: that observer's reading.
;;;;
;;;; Observers' functions can refer to the current readings of other
;;;; observers, creating a dependency graph among observers, see
;;;; dependencies.clj for further details.

(ns bohr.observers
  (:require [clojure.tools.logging :as log]))

(def ^{:private true} observers (atom {}))
(def observations (atom 0))
(def ^{:dynamic true} current-observer nil)
(def ^{:dynamic true} current-prefix nil)
(def ^{:dynamic true} current-suffix nil)
(def ^{:dynamic true} current-units nil)
(def ^{:dynamic true} current-tags nil)

(defn- known-observer? [name]
  (contains? @observers name))

(defn observer-count []
  (count @observers))

(defn observer-names []
  (keys @observers))

(defn observers? []
  (< 0 (observer-count)))

(defn get-observer
  "Return the observer of the given `name`.  If `option` is provided,
  return the observer's matching option instead."
  ([name]
   (get-observer name nil))
  ([name option]
   (if (known-observer? name)
     (let [observer (get @observers name)]
       (if option
         (get observer option)
         observer))
     (throw
      (ex-info
       (format "No such observer '%s'" name)
       {:bohr true :type :bohr-no-such-observer-exception})))))

(defn define-observer!
  "Define a new observer."
  [name options instructions]
  (log/trace "Defining observer" name "with options" options)
  (let [new-options (assoc options :instructions instructions)]
    (swap! observers assoc name new-options)))

(defn make-observation
  "Make an observation."
  [name]
  ;; FIXME add error handling here...
  (log/debug "Observing" name)
  (swap! observations inc)
  (let [observer (get-observer name)]
    (binding [current-observer name
              current-prefix   (get observer :prefix)
              current-suffix   (get observer :suffix)
              current-units    (get observer :units)
              current-tags     (get observer :tags [])]
      ((get observer :instructions)))))

(defn- observer-patterns [runtime-options]
  [(map #(re-pattern %) (get runtime-options :exclude-observer))
   (map #(re-pattern %) (get runtime-options :include-observer))])

(defn- observer-allowed? [observer-name excluded-patterns included-patterns]
  (cond
    (and
     (empty? excluded-patterns)
     (empty? included-patterns))
    true

    (and
     (not-empty excluded-patterns)
     (empty? included-patterns))
    (not-any?
     #(re-find % (name observer-name))
     excluded-patterns)

    (and
     (empty? excluded-patterns)
     (not-empty included-patterns))
    (some
     #(re-find % (name observer-name))
     included-patterns)

    :else
    (and
     (some
      #(re-find % (name observer-name))
      included-patterns)
     (not-any?
      #(re-find % (name observer-name))
      excluded-patterns))))

(defn for-each-observer [runtime-options f]
  (let [[excluded-patterns included-patterns]
        (observer-patterns runtime-options)]
    (doseq [[name observer] @observers]
      (if (observer-allowed? name excluded-patterns included-patterns)
        (f name observer)))))

(defn map-periodic-observers [runtime-options f]
  (let [[excluded-patterns included-patterns]
        (observer-patterns runtime-options)]
    (map
     f
     (filter
      (fn [[name observer]]
        (and
         (observer-allowed? name excluded-patterns included-patterns)
         (get observer :ttl)))
      (seq @observers)))))

(defn check-for-observers! []
  (if (not (observers?))
    (log/warn "No observers defined, Bohr has nothing to do!!")))
