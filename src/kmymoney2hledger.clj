(ns kmymoney2hledger
  "Converts KMyMoney file format to HLedger file format.

  DISCLAIMER: THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
  CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES,
  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
  OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
  OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
  USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
  DAMAGE.
  
  Twitter: @maridonkers | Mastodon: @maridonkers@fosstodon.org | GitHub: maridonkers."
  (:require [clojure.string :as string]
            [tupelo.forest :as tf]))

;; --- CONSTANTS

(def EXTENSION_HLEDGER ".journal")
(def SEPARATOR_NEWLINE " => ")
(def SEPARATOR_PAYEE " | ")
(def POSTFIX_ACCOUNT "  ")
(def POSTFIX_POSTING "  ")
(def PREFIX_POSTING "  ")

(def ^{:doc "HLedger account types: A, L, E, R, X, ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE"}
  TOP_LEVEL_ACCOUNTS_KMYMONEY {"asset" "asset"
                               "liability" "liability"
                               "equity" "equity"
                               "income" "revenue"
                               "expense" "expense"})

;; --- SUPPORTING FUNCTIONS

(defn infix
  "To parse KMyMoney expressions. Usage: e.g. (load-string \"(infix 1.0 * 400/10)\"). See: https://prakhar.me/articles/infix-in-clojure/"
  ([x op y] (op x y))
  ([x op y & xs]
   (apply infix (cons (infix x op y) xs))))

(defn call-infix-with-fraction
  "Calls infix with appropriately converted string argument."
  [s]
  (let [as (string/replace s #"^([+-])" "0$1")
        as (string/replace as #"([/+-])" " $1 ")
        as (string/replace as #"([0-9]+)" "$1.0")
        ias (str "(kmymoney2hledger/infix " as ")")]
    (load-string ias)))

(defn date-kmymoney->hledger
  "Converts date from KMyMoney's yyyy-mm-dd to HLedger's yyyy/mm/dd."
  [s]
  (when (not (string/blank? s))
    (string/replace s #"(....)-(..)-(..)" "$1/$2/$3")))

(defn replace-newlines
  "Replaces newlines."
  [s]
  (when (not (string/blank? s))
    (string/replace s #"(\n)" SEPARATOR_NEWLINE)))

(defn replace-special-characters
  "Replaces the special characters with spaces."
  [s]
  (when (not (string/blank? s))
    (string/replace s #"[:;|\[\]]" " ")))

(defn trim+condense-whitespaces
  "Trims string and condenses several whitespaces to one space."
  [s]
  (when (not (string/blank? s))
    (-> s
        string/trim
        (string/replace #"\s+" " "))))

(defn top-level-account?
  "Is it a top-level account?"
  [account-name]
  (contains? TOP_LEVEL_ACCOUNTS_KMYMONEY account-name))

(defn somewhat-format->hledger
  "Somewhat escape and format string for hledger."
  [unformatted]
  (if (string/blank? unformatted)
    unformatted
    (let [formatted (-> unformatted
                        replace-newlines
                        replace-special-characters
                        trim+condense-whitespaces)]
      formatted)))

(defn format->hledger
  "Escape and format string for hledger."
  [unformatted]
  (if (string/blank? unformatted)
    unformatted
    (let [formatted (-> unformatted
                        replace-newlines
                        string/lower-case
                        replace-special-characters
                        trim+condense-whitespaces)]
      (if (top-level-account? formatted)
        (get TOP_LEVEL_ACCOUNTS_KMYMONEY formatted)
        formatted))))

(defn get-payee
  "Gets payee textual."
  [payees-index payee-id]

  (let [payee-hid (get payees-index payee-id -1)
        payee? (>= payee-hid 0)]
    ;;TODO Add other payee fields when applicable.
    (if payee?
      (let [payee-attrs (tf/hid->attrs payee-hid)
            name? (contains? payee-attrs :name)
            name (when name? (get payee-attrs :name ""))]
        (somewhat-format->hledger name))
      "")))

(defn get-file-reader
  "Gets a file reader function for pathname."
  [pathname]
  (let [f pathname]
    (fn []
      (slurp f))))

(defn get-file-writer
  "Gets a file writer function for pathname."
  [pathname]
  (let [f pathname]
    (fn [content append?]
      (spit f content :append append?))))

;; --- INPUT FUNCTIONS

(defn read-from-xml
  "Reads from XML source file."
  [pathname]
  (let [file-reader (get-file-reader pathname)
        xml-str-raw (file-reader)
        xml-str (apply str
                       ;; TODO What about e.g. &#xa; ?
                       (filter #(not (Character/isISOControl %))
                               xml-str-raw))]
    (tf/add-tree-xml xml-str)))

;; --- OUTPUT FUNCTIONS

(defn header-to-journal
  "Writes header to journal target file."
  [fw pathname]
  (fw (str "; Converted from KMyMoney file: " pathname
           "\n;\n")
      false))

(defn fileinfo-to-journal
  "Writes fileinfo content to journal target file."
  [fw fileinfo-hid]
  (let [fileinfo-kids-hids (tf/hid->kids fileinfo-hid)
        fileinfo-journal (reduce (fn [r1 kh]
                                   (let [ka (tf/hid->attrs kh)
                                         t (name (get ka :tag ""))]
                                     (str r1
                                          "; " t ":"
                                          (reduce (fn [r2 k]
                                                    (if (= k :tag)
                                                      r2
                                                      (str r2 " "
                                                           (replace-newlines (get ka k)))))
                                                  ""
                                                  (keys ka))
                                          "\n")))
                                 ""
                                 fileinfo-kids-hids)
        journal (str "; --FILEINFO--\n"
                     fileinfo-journal
                     ";\n")]
    (fw journal true)))

(defn user-to-journal
  "Writes user content to journal target file."
  [fw user-hid]
  (let [user-attrs (tf/hid->attrs user-hid)
        user-journal (reduce (fn [r k]
                               (if (= k :tag)
                                 r
                                 (str r "; " (name k) ": "
                                      (replace-newlines (get user-attrs k)) "\n")))
                             ""
                             (keys user-attrs))
        address-hid (tf/find-hid user-hid [:* :ADDRESS])
        address-attrs (tf/hid->attrs address-hid)
        address-journal (reduce (fn [r k]
                                  (if (= k :tag)
                                    r
                                    (str r "; " (name k) ": "
                                         (replace-newlines (get address-attrs k)) "\n")))
                                ""
                                (keys address-attrs))
        journal (str "; --USER--\n"
                     user-journal
                     address-journal
                     ";\n")]
    (fw journal true)))

(defn accountid-attrs->journal-comment
  "Converts accountid attributes to journal comment format."
  [ah accounts-hid]
  (let [accountid-attrs (tf/hid->attrs ah)]
    (reduce (fn [r1 aha]
              (if (= aha :tag)
                r1
                (str r1
                     (let [id (get accountid-attrs aha)
                           id-hid (tf/find-hid accounts-hid
                                               [:* {:tag :ACCOUNT :id id}])
                           id-attrs (tf/hid->attrs id-hid)]
                       (reduce (fn [r2 ida]
                                 (if (= ida :tag)
                                   r2
                                   (let [idan (name ida)
                                         type? (= idan "type") 
                                         idan (if type? "kmymoney-type" idan)
                                         idav (somewhat-format->hledger (get id-attrs ida))]
                                     (if (string/blank? idav)
                                       r2
                                       (str r2 ";\t" idan ": " idav "\n")))))
                               ""
                               (keys id-attrs))) "")))
            ""
            (keys accountid-attrs))))

(defn institution-to-journal
  "Writes institution to journal target file."
  [fw institution-hid accounts? accounts-hid]
  (let [institutions-attrs (tf/hid->attrs institution-hid)
        institutions-journal (reduce (fn [r k]
                                       (if (= k :tag)
                                         r
                                         (let [kn (name k)
                                               type? (= kn "type") 
                                               kn (if type? "kmymoney-type" kn)
                                               kv (somewhat-format->hledger (get institutions-attrs k))]
                                           (if (string/blank? kv)
                                             r
                                             (str r "; " kn ": " kv "\n")))))
                                     ""
                                     (keys institutions-attrs))
        address-hid (tf/find-hid institution-hid [:* :ADDRESS])
        address-attrs (tf/hid->attrs address-hid)
        address-journal (reduce (fn [r k]
                                  (if (= k :tag)
                                    r
                                    (str r "; " (name k) ": "
                                         (replace-newlines (get address-attrs k)) "\n")))
                                ""
                                (keys address-attrs))
        accountid-hids (tf/find-hids institution-hid [:* :ACCOUNTIDS :ACCOUNTID])
        accountid-journal (reduce (fn [r ah]
                                    (str r
                                         "; accountid: " (tf/hid->attr ah :id)
                                         "\n"
                                         (when accounts?
                                           (accountid-attrs->journal-comment ah
                                                                             accounts-hid))))
                                  ""
                                  accountid-hids)
        journal (str "; --INSTITUTIONS--\n"
                     institutions-journal
                     address-journal
                     accountid-journal
                     ";\n")]
    (fw journal true)))

(defn payee-to-journal
  "Writes payee content to journal target file."
  [fw payee-hid]
  (let [payee-attrs (tf/hid->attrs payee-hid)
        payee-journal (reduce (fn [r k]
                                (if (= k :tag)
                                  r
                                  (str r "; " (name k) ": "
                                       (replace-newlines (get payee-attrs k)) "\n")))
                              ""
                              (keys payee-attrs))
        address-hid (tf/find-hid payee-hid [:* :ADDRESS])
        address-attrs (tf/hid->attrs address-hid)
        address-journal (reduce (fn [r k]
                                  (if (= k :tag)
                                    r
                                    (str r "; " (name k) ": "
                                         (replace-newlines (get address-attrs k)) "\n")))
                                ""
                                (keys address-attrs))
        journal (str "; --PAYEE--\n"
                     payee-journal
                     address-journal
                     ";\n")]
    (fw journal true)))

(defn costcenter-to-journal
  "Writes costcenter content to journal target file."
  [fw costcenter-hid]
  (let [costcenter-attrs (tf/hid->attrs costcenter-hid)
        costcenter-journal (reduce (fn [r k]
                                     (if (= k :tag)
                                       r
                                       (str r "; " (name k) ": "
                                            (replace-newlines (get costcenter-attrs k)) "\n")))
                                   ""
                                   (keys costcenter-attrs))
        ;; TODO Check format of costcenter tag.
        journal (str "; --COSTCENTER--\n"
                     costcenter-journal
                     ";\n")]
    (fw journal true)))

(defn tag-to-journal
  "Writes tag content to journal target file."
  [fw tag-hid]
  (let [tag-attrs (tf/hid->attrs tag-hid)
        tag-journal (reduce (fn [r k]
                              (if (= k :tag)
                                r
                                (str r "; " (name k) ": "
                                     (replace-newlines (get tag-attrs k)) "\n")))
                            ""
                            (keys tag-attrs))
        ;; TODO Check format of tag tag.
        journal (str "; --TAG--\n"
                     tag-journal
                     ";\n")]
    (fw journal true)))

(defn institutions->hids
  "Returns a map that indexes institution ids to hids."
  [institution-hids]
  (reduce (fn [result institution-hid]
            (assoc result (tf/hid->attr institution-hid :id) institution-hid))
          {}
          institution-hids))

(defn payees->hids
  "Returns a map that indexes payee ids to hids."
  [payee-hids]
  (reduce (fn [result payee-hid]
            (assoc result (tf/hid->attr payee-hid :id) payee-hid))
          {}
          payee-hids))

(defn accounts->hids
  "Returns a map that indexes account ids to hids."
  [account-hids]
  (reduce (fn [result account-hid]
            (assoc result (tf/hid->attr account-hid :id) account-hid))
          {}
          account-hids))

(defn transactions->hids
  "Returns a map that indexes transaction ids to hids."
  [transaction-hids]
  (reduce (fn [result transaction-hid]
            (assoc result (tf/hid->attr transaction-hid :id) transaction-hid))
          {}
          transaction-hids))

(defn reports->hids
  "Returns a map that indexes report ids to hids."
  [report-hids]
  (reduce (fn [result report-hid]
            (assoc result (tf/hid->attr report-hid :id) report-hid))
          {}
          report-hids))

(defn account-hierarchical->journal
  "Converts account attributes to hierarchical journal account format."
  [accounts-index account-hid as-comment?]
  
  (let [account-attrs (tf/hid->attrs account-hid)
        account-name? (contains? account-attrs :name)
        account-name-unformatted (when account-name? (tf/hid->attr account-hid :name))
        account-name (if as-comment?
                       (somewhat-format->hledger account-name-unformatted)
                       (format->hledger account-name-unformatted))
        parentaccount? (contains? account-attrs :parentaccount)
        parentaccount (when parentaccount? (tf/hid->attr account-hid :parentaccount))
        parentaccount-hid (get accounts-index parentaccount -1)
        parentaccount? (>= parentaccount-hid 0)]
    (str (if parentaccount?
           (str (account-hierarchical->journal accounts-index
                                               parentaccount-hid
                                               as-comment?)
                ":")
           "")
         account-name)))

(defn account-attrs->journal-comment
  "Converts account attributes to journal comment format. Rename the type attribute because HLedger recognizes this as meta information."
  [account-hid]
  (let [account-attrs (tf/hid->attrs account-hid)
        parentaccount? (contains? account-attrs :parentaccount)
        parentaccount (when parentaccount? (tf/hid->attr account-hid :parentaccount))
        parentaccount? (not (string/blank? parentaccount))
        account-name? (contains? account-attrs :name)
        account-name (when account-name? (format->hledger (tf/hid->attr account-hid :name)))
        account-journal (reduce (fn [r aa]
                                  (if (= aa :tag)
                                    r
                                    (let [aan (name aa)
                                          type? (= aan "type") 
                                          aan (if type? "kmymoney-type" aan)
                                          aav (somewhat-format->hledger (get account-attrs aa))]
                                      (if (string/blank? aav)
                                        r
                                        (str r
                                             "  ; " aan ": " aav "\n")))))
                                ""
                                (keys account-attrs))]
    (str (when (and (not parentaccount?) account-name?)
           (str "  ; type: " (string/capitalize account-name) "\n"))
         account-journal)))

(defn account-declaration->journal
  "Converts account attributes to journal account declaration format."
  [accounts-index account-hid]
  (let [declaration (str "account " (account-hierarchical->journal accounts-index account-hid false))
        postfix-comment (str POSTFIX_ACCOUNT "; ")
        postfix (account-hierarchical->journal accounts-index account-hid true)
        below-comment (account-attrs->journal-comment account-hid)]
    (str declaration postfix-comment postfix "\n" below-comment "\n")))

(defn account-to-journal
  "Writes account content to journal target file."
  [fw accounts-index account-hid]
  (let [journal (account-declaration->journal accounts-index account-hid)]
    (fw journal true)))

(defn transaction-split->journal
  "Transaction split to journal."
  [fw payees-index accounts-index commodity split-hid]
  (let [split-attrs (tf/hid->attrs split-hid)
        split-memo? (contains? split-attrs :memo)
        split-memo (when split-memo?
                     (somewhat-format->hledger (get split-attrs :memo)))
        split-payee? (contains? split-attrs :payee)
        split-payee (when split-payee?
                      (get-payee payees-index (get split-attrs :payee)))
        split-bankid? (contains? split-attrs :bankid)
        split-bankid (when split-bankid?
                       (format->hledger (get split-attrs :bankid)))
        split-number? (contains? split-attrs :number)
        split-number (when split-number?
                       (format->hledger (get split-attrs :number)))
        split-value? (contains? split-attrs :value)
        split-value (when split-value?
                      (get split-attrs :value))
        ;; TODO When applicable add various other fields (e.g. shares, price, reconciledate, reconcileflag, etc.)
        account-id  (tf/hid->attr split-hid :account)
        account-hid (get accounts-index account-id)
        account (account-hierarchical->journal accounts-index account-hid false)
        journal (str PREFIX_POSTING account
                     POSTFIX_POSTING commodity
                     " " (call-infix-with-fraction split-value)
                     " ; "split-payee
                     SEPARATOR_PAYEE split-memo
                     "\n")] 
    (fw journal true)))

(defn transaction-header-to-journal
  "Writes transaction header to journal target file."
  [fw transaction-id transaction-attrs payee memo commodity]
  (let [postdate? (contains? transaction-attrs :postdate)
        postdate (when postdate?
                   (date-kmymoney->hledger (get transaction-attrs :postdate)))
        journal (str postdate " (" transaction-id ")"
                     " " payee
                     " | " memo
                     "\n")]
    (fw journal true)))

(defn transaction-to-journal
  "Writes transaction content to journal target file."
  [fw payees-index accounts-index transaction-hid]
  (let [transaction-id (tf/hid->attr transaction-hid :id)
        transaction-attrs (tf/hid->attrs transaction-hid)
        commodity? (contains? transaction-attrs :commodity)
        commodity (when commodity?
                    (get transaction-attrs :commodity))
        splits? (tf/has-descendant? transaction-hid [:* :SPLITS :SPLIT])
        split? (tf/has-descendant? transaction-hid [:* :SPLITS :SPLIT])
        split-hids (when split?
                     (tf/find-hids transaction-hid [:* :SPLITS :SPLIT]))
        split-first-hid (when split? (first split-hids))
        split-first-attrs (when split? (tf/hid->attrs split-first-hid))]
    (fw "\n" true)
    (let [payee (when split? (get-payee payees-index (get split-first-attrs :payee "")))
          memo (when split? (somewhat-format->hledger (get split-first-attrs :memo "")))]
      (transaction-header-to-journal fw
                                     transaction-id transaction-attrs
                                     payee memo commodity))
    (when (and splits? split?)
      (doseq [split-hid split-hids]
        (transaction-split->journal fw payees-index accounts-index
                                    commodity split-hid)))))

(defn convert
  "Converts a specified pathname (command line arguments)."
  [pathname]
  (tf/with-forest (tf/new-forest)
    (let [root-hid (read-from-xml pathname)
          fileinfo? (tf/has-descendant? root-hid [:KMYMONEY-FILE :FILEINFO])
          fileinfo-hid (when fileinfo?
                         (tf/find-hid root-hid [:KMYMONEY-FILE :FILEINFO]))
          user? (tf/has-descendant? root-hid [:KMYMONEY-FILE :USER])
          user-hid (when user?
                     (tf/find-hid root-hid [:KMYMONEY-FILE :USER]))
          institutions? (tf/has-descendant? root-hid [:KMYMONEY-FILE :INSTITUTIONS])
          institution? (tf/has-descendant? root-hid [:KMYMONEY-FILE :INSTITUTIONS :INSTITUTION])
          institution-hids (when institution?
                             (tf/find-hids root-hid [:KMYMONEY-FILE :INSTITUTIONS :INSTITUTION]))
          institutions-index (when (and institutions? institution?) (institutions->hids institution-hids))
          payees? (tf/has-descendant? root-hid [:KMYMONEY-FILE :PAYEES])
          payee? (tf/has-descendant? root-hid [:KMYMONEY-FILE :PAYEES :PAYEE])
          payee-hids (when payee?
                       (tf/find-hids root-hid [:KMYMONEY-FILE :PAYEES :PAYEE]))
          payees-index (when (and payees? payee?) (payees->hids payee-hids))
          costcenter? (tf/has-descendant? root-hid [:KMYMONEY-FILE :COSTCENTERS :COSTCENTER])
          costcenter-hids (when costcenter?
                            (tf/find-hids root-hid [:KMYMONEY-FILE :COSTCENTERS :COSTCENTER]))
          tag? (tf/has-descendant? root-hid [:KMYMONEY-FILE :TAGS :TAG])
          tag-hids (when tag?
                     (tf/find-hids root-hid [:KMYMONEY-FILE :TAGS :TAG]))
          accounts? (tf/has-descendant? root-hid [:KMYMONEY-FILE :ACCOUNTS])
          accounts-hid (when accounts?
                         (tf/find-hid root-hid [:KMYMONEY-FILE :ACCOUNTS]))
          account? (tf/has-descendant? root-hid [:KMYMONEY-FILE :ACCOUNTS :ACCOUNT])
          account-hids (when account?
                         (tf/find-hids root-hid [:KMYMONEY-FILE :ACCOUNTS :ACCOUNT]))
          accounts-index (when (and accounts? account?) (accounts->hids account-hids))
          transactions? (tf/has-descendant? root-hid [:KMYMONEY-FILE :TRANSACTIONS])
          transaction? (tf/has-descendant? root-hid [:KMYMONEY-FILE :TRANSACTIONS :TRANSACTION])
          transaction-hids (when transaction?
                             (tf/find-hids root-hid [:KMYMONEY-FILE :TRANSACTIONS :TRANSACTION]))
          transactions-index (when (and transactions? transaction?) (transactions->hids transaction-hids))
          schedule? (tf/has-descendant? root-hid [:KMYMONEY-FILE :SCHEDULES :SCHEDULE])
          schedule-hids (when schedule?
                          (tf/find-hids root-hid [:KMYMONEY-FILE :SCHEDULES :SCHEDULE]))
          security? (tf/has-descendant? root-hid [:KMYMONEY-FILE :SECURITIES :SECURITY])
          security-hids (when security?
                          (tf/find-hids root-hid [:KMYMONEY-FILE :SECURITIES :SECURITY]))
          currency? (tf/has-descendant? root-hid [:KMYMONEY-FILE :CURRENCIES :CURRENCY])
          currency-hids (when currency?
                          (tf/find-hids root-hid [:KMYMONEY-FILE :CURRENCIES :CURRENCY]))
          pricepair? (tf/has-descendant? root-hid [:KMYMONEY-FILE :PRICES :PRICEPAIR])
          pricepair-hids (when pricepair?
                           (tf/find-hids root-hid [:KMYMONEY-FILE :PRICES :PRICEPAIR]))
          report? (tf/has-descendant? root-hid [:KMYMONEY-FILE :REPORTS :REPORT])
          reports? (tf/has-descendant? root-hid [:KMYMONEY-FILE :REPORTS])
          report-hids (when report?
                        (tf/find-hids root-hid [:KMYMONEY-FILE :REPORTS :REPORT]))
          reports-index (when (and reports? report?) (reports->hids report-hids))
          budget? (tf/has-descendant? root-hid [:KMYMONEY-FILE :BUDGETS :BUDGET])
          budget-hids (when budget?
                        (tf/find-hids root-hid [:KMYMONEY-FILE :BUDGETS :BUDGET]))
          onlinejobe? (tf/has-descendant? root-hid [:KMYMONEY-FILE :ONLINEJOBS :ONLINEJOB])
          onlinejob-hids (when onlinejobe?
                           (tf/find-hids root-hid [:KMYMONEY-FILE :ONLINEJOBS :ONLINEJOB]))
          target-pathname (str pathname EXTENSION_HLEDGER)
          file-writer (get-file-writer target-pathname)]
      
      (header-to-journal file-writer pathname)
      (when fileinfo?
        (fileinfo-to-journal file-writer fileinfo-hid))
      (when user?
        (user-to-journal file-writer user-hid))
      (when institution?
        (doseq [institution-hid institution-hids]
          (institution-to-journal file-writer
                                  institution-hid
                                  accounts?
                                  accounts-hid)))
      (when costcenter?
        (doseq [costcenter-hid costcenter-hids]
          (costcenter-to-journal file-writer costcenter-hid)))
      (when tag?
        (doseq [tag-hid tag-hids]
          (tag-to-journal file-writer tag-hid)))
      
      ;; End the comment header.
      (file-writer "\n" true)
      (when account?
        (doseq [account-hid account-hids]
          (account-to-journal file-writer accounts-index account-hid)))
      (when transaction?
        (doseq [transaction-hid transaction-hids]
          (transaction-to-journal file-writer payees-index accounts-index transaction-hid))))))

;; --- MAIN FUNCTION

;; (-main "kmymoneyfile.kmy")
(defn -main [& args]
  (if-not (empty? args)
    (doseq [arg args]
      (print (str arg " ..."))
      (convert arg)
      (println " done."))
    (println (str "Usage: kmymoney2hledger pathname [pathname ...]\n\n"
                  "Converts KMyMoney file format to HLedger. Output files are postfixed with a " EXTENSION_HLEDGER " file extension."))))
