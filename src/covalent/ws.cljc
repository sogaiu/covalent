#?(:cljs
   (ns covalent.ws
     (:require
      [cljs.tagged-literals]
      [cljs.tools.reader.edn :as ctre]
      [clojure.core.async :as cca]
      [covalent.log :as cl]
      [covalent.print :as cp]
      [frame.core :as fc]
      [punk.core :as pc]))

   :default
   nil)

#?(:cljs
   (do

     (defonce in-chan
       (cca/chan))

     (defonce out-chan
       (cca/chan))

     (defonce err-chan
       (cca/chan))

     (defn connect
       [ws-url]
       (let [ws (js/WebSocket. ws-url)]
         (set! (.-onerror ws)
           (fn [ev]
             (cl/log-if-debug (str "ws error message: " (.-message ev)))
             (cca/put! err-chan [:onerror ev])))
         (set! (.-onclose ws)
           (fn [ev]
             (cl/log-if-debug (str "ws close code: " (.-code ev)))
             (cl/log-if-debug (str "ws close reason: " (.-reason ev)))
             (cca/put! err-chan [:onclose ev])))
         (set! (.-onopen ws)
           (fn [ev]
             (cl/log-if-debug "ws open")
             (cca/put! err-chan [:onopen ev])))
         (set! (.-onmessage ws)
           (fn [ev]
             (cl/log-if-debug (str "ws message data: " (.-data ev)))
             (cca/put! in-chan (.-data ev))))
         (cca/go-loop []
           (let [ev (cca/<! out-chan)]
             (.send ws ev)
             (recur)))
         {:ws ws}))

     (defn setup-emit-handler
       [conn]
       (fc/reg-fx
         pc/frame :emit
         (fn emit [v]
           (cl/log-if-debug (str ":emit " v))
           (let [v-str (binding [covalent.print/*make-readable* true]
                         (pr-str v))]
             (cca/put! out-chan v-str)))))

     (defn make-ws-url
       [host port endpoint]
       (str "ws://" host ":" port "/" endpoint))

     (defn start-loop
       []
       (cca/go-loop []
         (let [ev (cca/<! in-chan)
               read-value (ctre/read-string
                           {:readers
                            {'inst cljs.tagged-literals/read-inst
                             'uuid cljs.tagged-literals/read-uuid
                             'queue cljs.tagged-literals/read-queue}
                            :default tagged-literal}
                           ev)]
           (pc/dispatch read-value)
           (recur))))

     (defn start-punk
       [host port]
       (let [conn (connect (make-ws-url host port "ws"))]
         ;; preparing "frame" to handle the :emit effect
         (setup-emit-handler conn)
         ;; handle messages from network
         (start-loop) ; return value is channel
         ;; prepare tap> to trigger dispatching
         (pc/add-taps!)
         ;; capture this return value to work with send-msg or get-line
         conn))

     )
   )

(comment

  (require '[covalent.ws :as cw])
  (in-ns 'covalent.ws)

  ;; convenience function for set up (see source for details)
  (def conn
    (start-punk "127.0.0.1" 9876)) ; XXX: ip address of box w/ electron + punk

  ;; try sending a value to the electron app for viewing in punk.ui
  (tap> {:a 1 :b 2})

  ;; now take a look at the electron app and start clicking a bit :)

  ;; other things to try:
  (tap> #{:ant :bee :fox :elephant})
  (tap> [2 3 5 7 9])
  (tap> {:bag #{:pencil :notepad :water-bottle}
         :position :standing
         :mind [:tune :chatter]})
  (tap> (atom {:position :sitting}))

  ;; things known to cause problems:
  (tap> *ns*)
  (tap> #'start-punk)

  )
