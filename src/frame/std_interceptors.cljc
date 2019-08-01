(ns frame.std-interceptors
  (:require [frame.interceptors :refer [->interceptor]]
            [frame.loggers :refer [console]]))


;;
;; -- Interceptor Factories ---------------------------------------------------------------
;;

(defn fx-handler->interceptor
  "Returns an interceptor which wraps the kind of event handler given to `reg-event-fx`.
  These handlers take two arguments;  `coeffects` and `event`, and they return `effects`.
      (fn [coeffects event]
         {:db ...
          :dispatch ...})
   Wrap handler in an interceptor so it can be added to (the RHS) of a chain:
     1. extracts `:coeffects`
     2. call handler-fn giving coeffects
     3. stores the result back into the `:effects`"
  [handler-fn]
  (->interceptor
   :id     :fx-handler
   :before (fn fx-handler-before
             [context]
             (let [{:keys [event] :as coeffects} (:coeffects context)
                   new-context
                   (->> (handler-fn coeffects event)
                        (assoc context :effects))]
               new-context))))


(defn ctx-handler->interceptor
  "Returns an interceptor which wraps the kind of event handler given to `reg-event-ctx`.
  These advanced handlers take one argument: `context` and they return a modified `context`.
  Example:
      (fn [context]
         (enqueue context [more interceptors]))"
  [handler-fn]
  (->interceptor
   :id     :ctx-handler
   :before (fn ctx-handler-before
             [context]
             (let [new-context
                   (handler-fn context)]
               new-context))))
