(ns ^:figwheel-always harnocode.core
    (:require [goog.dom :as dom]
              [clojure.string :as string]
              [figwheel.client :as fw]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(defn show-harnocode! [harnocode]
  (let [output-area (dom/getElement "output")]
    (set! (.-value output-area) harnocode)))




;; Best try on filling "column" of l subsequent ones with one or more tokens
(defn fill-column [ts l]
  [(first ts) (rest ts)])

(defn fill-group [[result tokens] group]
    (if (== (first group) 0)
      [(string/join result (repeat (count group) "_")) tokens]
      (let [[column rest-ts] (fill-column tokens (count group))]
        [(string/join result column) rest-ts])))

(defn arrange-tokens-line [ts line]
  (let [groups (partition-by identity line)]
    (first (reduce fill-group ["" ts] groups))))

(defn arrange-tokens [ts img]
  (map #(arrange-tokens-line ts %) img))


;; Tries hard to make piece of code look like img
;; code -- string
;; img  -- 2d matrix of 0's and 1's
;; w    -- int, width of output, in chars
(defn harnify [code img w]
  (let [tokens (js->clj (js/esprima.tokenize code) :keywordize-keys true)
        ts     (map :value tokens)]
    (comment apply str (interleave ts (repeat " ")))
    (string/join "\n" (arrange-tokens ts img))         ; img must be resized by now, no need in w
    ))
    

(let [code      "var answer = 42;"
      img       [[0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]
                 [0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]
                 [0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]
                 [0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]
                 [0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]
                 [0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]
                 [0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]
                 [0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]
                 [0 0 0 0 0 0 0 0 1 1 1 1 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1]]
      width     (count (first img))
      harnocode (harnify code img width)]
  (show-harnocode! harnocode))



(fw/start {
  :load-warninged-code true
})
