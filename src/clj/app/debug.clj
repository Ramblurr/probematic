(ns app.debug)

(defn xxx>>
  ([msg x]
   (tap> msg)
   (tap> x)
   x)
  ([x]
   (tap> x)
   x))

(defn xxx
  ([x msg]
   (tap> {:msg msg :xxx x})
   x)
  ([x]
   (tap> {:xxx x})
   x))

(defmacro debug* [args]
  `(let [args# ~args]
     (tap> (sorted-map :fn
                       (quote ~args)
                       :ret
                       args#))
     args#))

(defmacro debug->>
  "Insert into a ->> thread to tap the current value"
  [& fns]
  `(->> ~@(interleave fns (repeat 'debug*))))

(defn ttap>
  "Send the value 'v' to the topic function with key 'topic'."
  [topic value]
  (tap> {::topic topic ::value value}))

(comment

  (debug->> (map inc [1 2 3 4 5])
            (filter odd?))

  ;;
  )
