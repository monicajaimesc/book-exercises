(ns doom)

(def num-players 2)
(def max-dice 3)
(def board-size 4)
(def board-hexnum (* board-size board-size))
(def ai-level 4)

(defn third [coll]
  (first (next (next coll))))

(defn indexed [coll]
  (map list (range) coll))

(defn gen-board []
  (let [f (fn [] [(rand-int num-players) (inc (rand-int max-dice))])]
    (vec (repeatedly board-hexnum f))))

(defn player-letter [n]
  (char (+ 97 n)))

(defn draw-board [board]
  (doseq [row (range board-size)]
    (print (apply str (repeat (- board-size row) \space)))
    (doseq [col (range board-size)]
      (let [[player dice] (board (+ col (* board-size row)))]
        (print (str (player-letter player) "-" dice " "))))
    (newline)))

(defn neighbors [pos]
  (let [up (- pos board-size)
        down (+ pos board-size)]
    (for [p (concat [up down]
                    (when-not (zero? (mod pos board-size))
                      [(dec up) (dec pos)])
                    (when-not (zero? (mod (inc pos) board-size))
                      [(inc pos) (inc down)]))
          :when (< -1 p board-hexnum)]
      p)))

(def neighbors (memoize neighbors))

(defn board-attack [board player src dst dice]
  (assoc board
    src [player 1]
    dst [player (dec dice)]))

(defn add-new-dice [board player spare-dice]
  (letfn [(f [[[cur-player cur-dice] :as lst] n acc]
            (cond (zero? n) (into acc lst)
                  (empty? lst) acc
                  :else (if (and (= cur-player player)
                                 (< cur-dice max-dice))
                          (recur (next lst)
                                 (dec n)
                                 (conj acc [cur-player (inc cur-dice)]))
                          (recur (next lst)
                                 n
                                 (conj acc [cur-player cur-dice])))))]
    (f board spare-dice [])))

(declare game-tree add-passing-move attacking-moves)

(defn game-tree [board player spare-dice first-move?]
  [player
   board
   (add-passing-move board
                     player
                     spare-dice
                     first-move?
                     (attacking-moves board player spare-dice))])

(def game-tree (memoize game-tree))

(defn add-passing-move [board player spare-dice first-move? moves]
  (if first-move?
    moves
    (lazy-seq (cons [nil
                     (game-tree (add-new-dice board player
                                              (dec spare-dice))
                                (mod (inc player) num-players)
                                0
                                true)]
                    moves))))

(defn attacking-moves [board cur-player spare-dice]
  (let [player (fn [pos] (first (board pos)))
        dice (fn [pos] (second (board pos)))]
    (mapcat
     (fn [src]
       (when (= (player src) cur-player)
         (mapcat
          (fn [dst]
            (when (and (not= (player dst) cur-player)
                       (> (dice src) (dice dst)))
              [[[src dst]
                (game-tree (board-attack board
                                         cur-player
                                         src
                                         dst
                                         (dice src))
                           cur-player
                           (+ spare-dice (dice dst))
                           false)]]))
          (neighbors src))))
     (range board-hexnum))))

(defn print-info [tree]
  (println "current player =" (player-letter (first tree)))
  (draw-board (second tree)))

(defn handle-human [tree]
  (println "choose your move:")
  (let [moves (third tree)]
    (doseq [[n [action]] (indexed moves)]
      (print (str (inc n) ". "))
      (if action
        (println (first action) "->" (second action))
        (println "end turn")))
    (second (nth moves (dec (read))))))

(defn winners [board]
  (let [tally (map first board)
        totals (frequencies tally)
        best (apply max (vals totals))]
    (map first (filter (fn [[player total]] (= total best)) totals))))

(defn announce-winners [board]
  (let [w (winners board)]
    (if (> (count w) 1)
      (println "The game is a tie between" (map player-letter w))
      (println "The winner is" (player-letter (first w))))))

(defn play-vs-human [tree]
  (print-info tree)
  (if (seq (third tree))
    (recur (handle-human tree))
    (announce-winners (second tree))))

(defn threatened? [pos board]
  (let [[player dice] (board pos)]
    (some (fn [n]
            (let [[nplayer ndice] (board n)]
              (and (not= player nplayer)
                   (> ndice dice))))
          (neighbors pos))))

(defn score-board [board player]
  (reduce + (for [[pos hex] (indexed board)]
              (if (= player (first hex))
                (if (threatened? pos board)
                  1
                  2)
                -1))))

(declare rate-position get-ratings)

(defn rate-position [tree player]
  (let [moves (third tree)]
    (if (seq moves)
      (if (= (first tree) player)
        (apply max (get-ratings tree player))
        (apply min (get-ratings tree player)))
      (score-board (second tree) player))))

(def rate-position (memoize rate-position))

(defn get-ratings [tree player]
  (map #(rate-position (second %) player) (third tree)))

(defn limit-tree-depth [tree depth]
  [(first tree)
   (second tree)
   (when-not (zero? depth)
     (map (fn [move]
            [(first move)
             (limit-tree-depth (second move) (dec depth))])
          (third tree)))])

(defn handle-computer [tree]
  (let [player (first tree)
        trimmed-tree (limit-tree-depth tree ai-level)
        indexed-ratings (indexed (get-ratings trimmed-tree player))
        idx-best (first (reduce (fn [x y]
                                  (if (>= (second x) (second y))
                                    x
                                    y))
                                indexed-ratings))]
    (second (nth (third tree) idx-best))))

(defn play-vs-computer [tree]
  (print-info tree)
  (cond (empty? (third tree)) (announce-winners (second tree))
        (zero? (first tree)) (recur (handle-human tree))
        :else (recur (handle-computer tree))))
