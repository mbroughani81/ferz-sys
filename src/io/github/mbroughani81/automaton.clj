(ns io.github.mbroughani81.automaton)

(defprotocol Automaton
  (give [this m])
  (receive [this m]))

