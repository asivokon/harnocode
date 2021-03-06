(ns ^:figwheel-always harnocode.core
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.object :as obj]
            [clojure.string :as string]))

(enable-console-print!)

(defn dummy-image [w h]
  (let [ones (concat (repeat 10 0) (repeat w 1))]
    (repeat h ones)))

(defonce img
    (atom (dummy-image 100 200)))

(defn to-clj-array [js-col]
  (-> (clj->js [])
      (.-slice)
      (.call js-col)
      (js->clj)))

(defn show-harnocode! [harnocode]
  (let [output-area (dom/getElement "output")]
    (set! (.-value output-area) harnocode)))

;; Ugly, but have no time to find a better way
(defn insert [v pos item]
  (let [[h t] (split-at pos v)]
    (vec (concat h [item] t))))

;; Insert spaces between terms so they occupy exactly l chars
;; TODO: make spacing more even
(defn insert-spaces [terms l]
  (let [terms-interleaved terms                             ;; TODO: need better interleave that would work as string.join (without last space)
        spaces-needed (- l (apply + (map count terms-interleaved)))
        f (fn [terms _] (insert terms (inc (rand-int (- (count terms) 1))) " "))]
    (string/join (reduce f terms-interleaved (range spaces-needed)))))

;; Best try on filling "column" of l subsequent ones with one or more tokens
(defn fill-column [ts l]
  (let [f (fn [terms-so-far term]
            (if (>= (apply + (map count terms-so-far)) l)
              (reduced (if (> (count terms-so-far) 1) (butlast terms-so-far) terms-so-far))
              (conj terms-so-far term)))
        terms-that-fit (reduce f [] ts)
        ts-rest (drop (count terms-that-fit) ts)]
    [(insert-spaces terms-that-fit l) ts-rest]))


(defmulti fill-group
          (fn [[result tokens] group]
            (first group)))

;; shorten defined for 0 group only: how much chars "eaten" by preceding 1-groups
(defmethod fill-group 0 [[result tokens shorten] group]
  (let [l (max 1 (- (count group) shorten))]
    [(conj result (string/join (repeat l " "))) tokens 0]))

(defmethod fill-group 1 [[result tokens _] group]
  (let [l (count group)
        [column rest-ts] (fill-column tokens l)
        extra-len (- (count column) l)]
    [(conj result column) rest-ts extra-len]))

(defn arrange-tokens-line [ts line]
  (let [groups (partition-by identity line)
        [this-line-tokens ts-rest _] (reduce fill-group [[] ts 0] groups)]
    ;(println "Line: " (string/join this-line-tokens) "Rest:" ts-rest)
    [(string/join this-line-tokens) ts-rest]))

;; arrange tokens to form ascii-art
;; return [result remained-tokens] tuple
(defn arrange-tokens [ts img result]
  (if (or (empty? ts) (empty? img)) [(identity result) ts]
    (let [[line ts-rest] (arrange-tokens-line ts (first img))]
      (recur ts-rest (rest img) (conj result line)))))

(defn arrange-unused [ts width]
  (let [a    (/ width 7)
        line (concat (repeat a 0) (repeat (* 3 a) 1) (repeat a 0) (repeat (* 3 a) 1))
        fake-img (take (/ (count ts) 5) (cycle [line]))       ; crap, re-do!!
        [result _] (arrange-tokens ts fake-img [])]
    (concat (repeat 20 "\n") result)))

;; TODO: handle escaped symbols
(defn split-string-literal [token l result]
 (let [[quote & value-rest] (:value token)
       text   (string/join (butlast value-rest))
       chunks (string/split text " ")
       plus   {:value "+", :type "Punctuator"}
       quoted-chunks (map #(str quote % " " quote) chunks)
       quoted-tokens (map (fn [t] {:value t :type "String"}) quoted-chunks)
       open-brace  {:value "(" :type "Punctuator"}
       close-brace {:value ")" :type "Punctuator"}
       interleaved (butlast (interleave quoted-tokens (cycle [plus])))]
    ;[token]   ;  == do nothing
    (concat [open-brace] interleaved [close-brace])))

(defn split-string-literals [tokens l]
  (let [split-token (fn [token]
                     (if (not= (:type token) "String")
                      [token]
                      (split-string-literal token l [])))]
  (mapcat split-token tokens)))

;; TODO
(defn tokens-to-text [[t1 t2 & tokens] result]
  (cond
    (nil? t1) result
    (nil? t2) (conj result (:value t1))
    :else     (let [not-punct? #(not= (:type %) "Punctuator")
                    tok-line   #(get-in % [:loc :end :line])
                    same-line? #(= (tok-line %1) (tok-line %2))

                    need-space (or (and (not-punct? t1) (not-punct? t2))
                                   (and (= (:value t1) (:value t2))))       ; hack to fix cases like "+ +" -> "++". would be nice to make finer condition
                    glue-toks  (and (same-line? t1 t2)
                                    (or (contains? #{"++" "--"} (:value t2))
                                        (contains? #{"continue" "break" "return" "throw"} (:value t1))))]
              (cond
                glue-toks  (recur tokens           (conj result (str (:value t1) " " (:value t2) " ")))
                need-space (recur (conj tokens t2) (conj result (str (:value t1) " ")))
                :else      (recur (conj tokens t2) (conj result (str (:value t1))))))))

(defn analyze-code [code]
  (let [ast (js/esprima.parse code)
        regenerated (js/escodegen.generate ast)
        tokens (js->clj (js/esprima.tokenize regenerated (clj->js {:loc true})) :keywordize-keys true)]
    (tokens-to-text tokens [])))
    ;(tokens-to-text (split-string-literals tokens 5) [])))

(defn invert-image [img]
  (let [invert-row (fn [row] (map #(if (= % 0) 1 0) row))]
   (map invert-row img)))
   
;; Tries hard to make piece of code look like img
;; code -- string
;; img  -- 2d matrix of 0's and 1's
;; w    -- int, width of output, in chars
(defn harnify [code img w]
  ;(.profile js/console "harnify")
  (let [ts (analyze-code code)
        [arranged ts-unused] (arrange-tokens ts img [])
        unused               (arrange-unused ts-unused 160)
        result (concat arranged unused)]
    (string/join "\n" result)))              ; img must be resized by now, no need in w

(defn validate [code img w]
  (try
    (let [regen-before (js/escodegen.generate (js/esprima.parse code))]
      (let [harnocode  (harnify code img w)
            regen-after  (js/escodegen.generate (js/esprima.parse harnocode))
            msg        (if (= regen-before regen-after)
                        "Validate: OK"
                        (str "Differs: " "\nFAIL"))]
          (println msg)))
    (catch js/Error e
      (println "Parse error: " e))))

(defn redraw! []
  (let [code (.-value (dom/getElement "output"))
        width (count (first @img))
        harnocode (harnify code @img width)]
    (println "clicked")
    (show-harnocode! harnocode)
    (validate code @img width)))

(defn go-click-listener [event]
  (redraw!))

(defonce setup-stuff
 (do
  (let [button (dom/getElement "go")]
   (events/listen button "click" go-click-listener))
  (events/listen (dom/getElement "inverse") "click"
    (fn [event]
     (swap! img invert-image)
     (redraw!)))))

(defn get-pixels [context canvas]
  (let [w (.-width canvas)
        h (.-height canvas)
        img-data (.getImageData context 0 0 w h)
        pixels (.-data img-data)
        result (atom [])]
    (obj/forEach pixels
                 (fn [val]
                   (swap! result conj val)))
    @result))

(defn array-to-arrays [src w acc]
  (let [[h t] (split-at w src)]
    (if (empty? h) acc (recur t w (conj acc h)))))

(defn rescale-width [img]
  (let [rescale-line (fn [line] (mapcat #(repeat 2 %) line))]
    (map rescale-line img)))

(defn black-and-white [context canvas]
  (let [pixels (get-pixels context canvas)
        greyscale (fn [r g b alpha]
                    (let [grey (+ (* r 0.3) (* g 0.59) (* b 0.11))]
                      (if (> grey (/ 255 2)) 0 1)))
        f (fn [[r g b a & rest-pixels] result]
            (if (nil? a)
              result
              (recur rest-pixels (conj result (greyscale r g b a)))))]
    (array-to-arrays (f pixels []) (.-width canvas) [])))

(defn pixels-to-string [px acc]
  (if (empty? px)
    (string/join "<br/>" acc)
    (let [h (first px)
          t (rest px)]
      (recur t (conj acc (string/join "&nbsp;" h)))
      )))

(defn display-image-array [pixels]
  (let [pixels-div (dom/getElement "pixels")
        s          (pixels-to-string pixels [])]
    (set! (.-innerHTML pixels-div) s)))

(defonce max-image-dimension 224)

  ;w (.-width image)
  ;h (.-height image)
  ;ratio (if (> w max-image-dimension) (/ w max-image-dimension) 1)
  ;new-w (/ w ratio)
  ;new-h (* (/ h w) new-w)

(defn on-image-load [event]
  (let [image   (.-target event)
        canvas  (dom/getElement "viewport")
        context (.getContext canvas "2d")]
    (set! (.-width canvas)  (.-width image))
    (set! (.-height canvas) (.-height image))
    (.drawImage context image 0 0)
    (->> (black-and-white context canvas)
         rescale-width
         (reset! img))
    (redraw!)))

(defn on-reader-load [event]
  (let [pixels-div (dom/getElement "pixels")
        image      (js/Image.)
        src        (.. event -target -result)]
    (events/listen image (.-LOAD events/EventType) on-image-load)
    (set! (.-src image) src)
    (set! (.-innerHTML pixels-div) "")))

(defn handle-files [event]
  {:pre [(> (alength (.-files (.-target event))) 0)]}
  (let [f (nth (to-clj-array (.-files (.-target event))) 0)
        reader (js/FileReader.)]
    (set! (.-onloadend reader) on-reader-load)
    (.readAsDataURL reader f))
  )

(defn on-dom-loaded []
  (let [file-upload (dom/getElement "fileInput")]
    (events/listen file-upload "change" handle-files)))

(def w (dom/getWindow))
(events/listen w (.-DOMCONTENTLOADED events/EventType) on-dom-loaded)

