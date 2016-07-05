;;; self.clj
;;;
;;; Defines observers for Bohr itself.

(static :bohr.start-time (time/now))

(defn bohr-runtime [start-time]
  (time/in-seconds
   (time/interval start-time (time/now))))

(observe :bohr :ttl 5 :prefix "bohr"
         (do
           (submit "runtime"             (bohr-runtime (& :bohr.start-time)) :units "s" :desc "Bohr uptime" :tags ["duration"])
           (submit "observers"           (observer-count) :desc "Number of defined observers" :tags ["metric"])
           (submit "observations"        @observations :desc "Number of observations made" :tags ["counter"])
           (submit "metrics.gathered"    @submissions :desc "Number of unique metrics gathered" :tags ["counter"])
           (submit "metrics.transmitted" @publications :desc "Number of metrics transmitted via all journals" :tags ["counter"])))
