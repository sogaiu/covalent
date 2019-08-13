#?(:cljs
   (ns covalent.ws
     (:require
      [cljs.tagged-literals]
      [cljs.tools.reader.edn :as ctre]
      [covalent.log :as cl]
      [frame.core :as fc]
      [punk.core :as pc]))

   :default
   nil)

#?(:cljs
   (do

     ;; XXX: official rn docs don't use addEventListener
     #_(defn connect-old
       [ws-url on-msg]
       (let [ws (js/WebSocket. ws-url)]
         (.addEventListener ws "error"
           (fn [err]
             (js/console.log (str "ws error message: " (.-message err)))))
         (.addEventListener ws "close"
           (fn [ev]
             (js/console.log (str "ws close code: " (.-code ev)))
             (js/console.log (str "ws close reason: " (.-reason ev)))))
         (.addEventListener ws "open"
           (fn []
             (js/console.log "ws open")))
         (.addEventListener ws "message"
           (fn [ev]
             (js/console.log (str "ws message data: " (.-data ev)))
             (on-msg (.-data ev))))
         {:ws ws}))

     (defn connect
       [ws-url on-msg]
       (let [ws (js/WebSocket. ws-url)]
         (set! (.-onerror ws)
           (fn [err]
             (js/console.log (str "ws error message: " (.-message err)))))
         (set! (.-onclose ws)
           (fn [ev]
             (js/console.log (str "ws close code: " (.-code ev)))
             (js/console.log (str "ws close reason: " (.-reason ev)))))
         (set! (.-onopen ws)
           (fn []
             (js/console.log "ws open")))
         (set! (.-onmessage ws)
           (fn [ev]
             (js/console.log (str "ws message data: " (.-data ev)))
             (on-msg (.-data ev))))
         {:ws ws}))

     ;; XXX: leads to error in electron + punk
     #_(defn ping
       [{:keys [ws]}]
       (.ping ws))

     (defn send-msg
       [{:keys [ws]} message]
       (.send ws message))

     (defn setup-emit-handler
       [conn]
       (fc/reg-fx
         pc/frame :emit
         (fn emit [v]
           ;; XXX
           (cl/log-if-debug (str ":emit " v))
           (send-msg conn (pr-str v)))))

     (defn handle-message
       [msg]
       (let [read-value (ctre/read-string
                         {:readers
                          {'inst cljs.tagged-literals/read-inst
                           'uuid cljs.tagged-literals/read-uuid
                           'queue cljs.tagged-literals/read-queue}
                          :default tagged-literal}
                         msg)]
         (pc/dispatch read-value)))

     (defn make-ws-url
       [host port endpoint]
       (str "ws://" host ":" port "/" endpoint))

     (defn start-punk
       [host port]
       (let [conn (connect (make-ws-url host port "ws")
                    handle-message)]
         ;; preparing "frame" to handle the :emit effect
         (setup-emit-handler conn)
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

  ;; lower level stuff

  (def conn
    (connect "ws://localhost:9876/ws"
      (fn [data]
        (js/console.log data))))

  (send-msg conn "[:entry 0 {:value {:a 1 :b 2} :meta nil}]")

  )
