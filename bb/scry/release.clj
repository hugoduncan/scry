(ns scry.release
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]))

(def major-minor "0.1")
(def release-tag-pattern #"^v0\.1\.(\d+)$")
(def sha-pattern #"^[0-9a-f]{40}$")
(def release-workflow ".github/workflows/release.yml")

(defn build-version
  "Returns the scry build version for a Git commit count."
  [commit-count]
  (when-not (and (integer? commit-count) (not (neg? commit-count)))
    (throw (ex-info "Commit count must be a non-negative integer"
                    {:commit-count commit-count})))
  (str major-minor "." commit-count))

(defn dry-run-version
  "Returns the build version expected for an exact dry-run checkout commit."
  [commit-count-at-checkout]
  (build-version commit-count-at-checkout))

(defn next-release-version
  "Returns the version for the release commit about to be created."
  [current-commit-count]
  (build-version (inc current-commit-count)))

(defn release-tag-version
  "Returns the version component from a valid publishing tag.

   Throws ExceptionInfo for any nonconforming v* tag so publishing can fail
   before deploy or GitHub Release creation."
  [tag]
  (let [[_ patch] (re-matches release-tag-pattern tag)]
    (when-not patch
      (throw (ex-info "Release tag must match v0.1.<non-negative-integer>"
                      {:tag tag})))
    (str major-minor "." patch)))

(defn valid-release-tag?
  "Returns true when tag is an allowed publishing tag."
  [tag]
  (boolean (re-matches release-tag-pattern tag)))

(defn assert-tag-matches-version!
  [tag version]
  (let [tag-version (release-tag-version tag)]
    (when-not (= tag-version version)
      (throw (ex-info "Release tag version does not match built artifact version"
                      {:tag tag
                       :tag-version tag-version
                       :built-version version})))
    version))

(defn- heading?
  [line]
  (boolean (re-matches #"^## .*" line)))

(defn- trim-blank-lines
  [lines]
  (->> lines
       (drop-while str/blank?)
       reverse
       (drop-while str/blank?)
       reverse))

(defn- section-after-heading
  [lines heading-re]
  (let [start (->> lines
                   (map-indexed vector)
                   (some (fn [[line-index line]]
                           (when (re-matches heading-re line)
                             line-index))))]
    (when start
      (->> lines
           (drop (inc start))
           (take-while #(not (heading? %)))
           trim-blank-lines
           vec))))

(defn unreleased-section
  "Returns the trimmed body of the bare ## Unreleased section.

   Throws when the section is missing or empty."
  [changelog]
  (let [lines (str/split-lines changelog)
        body-lines (section-after-heading lines #"^## Unreleased\s*$")]
    (when-not body-lines
      (throw (ex-info "CHANGELOG.md is missing a bare ## Unreleased section" {})))
    (when-not (seq body-lines)
      (throw (ex-info "CHANGELOG.md ## Unreleased section is empty" {})))
    (str/join "\n" body-lines)))

(defn stamp-changelog
  "Returns changelog stamped for version/date.

   Preserves a fresh empty bare ## Unreleased section and moves the previous
   non-empty unreleased body under ## [VERSION] - YYYY-MM-DD."
  [changelog version date]
  (let [lines (str/split-lines changelog)
        start (->> lines
                   (map-indexed vector)
                   (some (fn [[line-index line]]
                           (when (re-matches #"^## Unreleased\s*$" line)
                             line-index))))]
    (when-not start
      (throw (ex-info "CHANGELOG.md is missing a bare ## Unreleased section" {})))
    (let [before (subvec (vec lines) 0 start)
          after-heading (subvec (vec lines) (inc start))
          body (->> after-heading
                    (take-while #(not (heading? %)))
                    trim-blank-lines
                    vec)
          rest-lines (->> after-heading
                          (drop-while #(not (heading? %)))
                          vec)]
      (when-not (seq body)
        (throw (ex-info "CHANGELOG.md ## Unreleased section is empty" {})))
      (str (str/join "\n"
                     (concat before
                             ["## Unreleased" "" (str "## [" version "] - " date) ""]
                             body
                             (when (seq rest-lines) [""])
                             rest-lines))
           "\n"))))

(defn release-section
  "Returns the trimmed body of ## [VERSION] - YYYY-MM-DD.

   Throws when the bracketed release section is missing or empty."
  [changelog version]
  (let [version-pattern (java.util.regex.Pattern/quote version)
        heading-re (re-pattern (str "^## \\[" version-pattern "\\] - \\d{4}-\\d{2}-\\d{2}\\s*$"))
        body-lines (section-after-heading (str/split-lines changelog) heading-re)]
    (when-not body-lines
      (throw (ex-info "CHANGELOG.md is missing the requested bracketed release section"
                      {:version version})))
    (when-not (seq body-lines)
      (throw (ex-info "CHANGELOG.md release section is empty"
                      {:version version})))
    (str/join "\n" body-lines)))

(defn dry-run-changelog-section
  "Returns the changelog body required for a workflow-dispatch dry run.

   Dry runs against an already stamped release ref/tag require the matching
   bracketed release section. Ordinary branch/SHA dry runs may use the matching
   bracketed section when present, otherwise they require a non-empty bare
   ## Unreleased section."
  [changelog {:keys [version input-ref head-sha tag-sha]}]
  (let [version (or version "")
        input-ref (or input-ref "")
        release-tag (when-not (str/blank? version) (str "v" version))
        release-ref? (and release-tag
                          (contains? #{release-tag (str "refs/tags/" release-tag)} input-ref))
        tagged-head? (and release-tag
                          (not (str/blank? (or head-sha "")))
                          (= head-sha tag-sha))]
    (cond
      (or release-ref? tagged-head?)
      (release-section changelog version)

      (not (str/blank? version))
      (try
        (release-section changelog version)
        (catch clojure.lang.ExceptionInfo _
          (unreleased-section changelog)))

      :else
      (unreleased-section changelog))))

(defn command
  "Runs command-args and captures output."
  [command-args]
  (let [result (apply shell/sh command-args)]
    {:exit (:exit result)
     :out (:out result)
     :err (:err result)}))

(defn assert-command-success!
  [result message data]
  (when-not (zero? (:exit result))
    (throw (ex-info message (merge data result))))
  result)

(defn run-command!
  [command-fn command-args message]
  (assert-command-success! (command-fn command-args) message {:command command-args}))

(defn git
  [command-fn & args]
  (:out (run-command! command-fn (into ["git"] args) "Git command failed")))

(defn git-trim
  [command-fn & args]
  (str/trim (apply git command-fn args)))

(defn git-count-at
  [command-fn commit]
  (let [count-text (git-trim command-fn "rev-list" "--count" commit)]
    (when-not (re-matches #"\d+" count-text)
      (throw (ex-info "Unable to compute version: git rev-list --count returned an invalid count"
                      {:commit commit :out count-text})))
    (Long/parseLong count-text)))

(defn clean-working-tree?
  "Returns true when git status --porcelain succeeds and is blank."
  [command-fn]
  (str/blank? (:out (run-command! command-fn
                                   ["git" "status" "--porcelain"]
                                   "Failed to inspect working tree status"))))

(defn current-branch
  [command-fn]
  (str/trim (:out (run-command! command-fn
                                 ["git" "rev-parse" "--abbrev-ref" "HEAD"]
                                 "Failed to resolve current branch"))))

(defn assert-clean-master!
  [command-fn]
  (when-not (clean-working-tree? command-fn)
    (throw (ex-info "Working tree is not clean; commit or stash changes first" {})))
  (let [branch (current-branch command-fn)]
    (when-not (= "master" branch)
      (throw (ex-info "Release must be cut from master" {:branch branch}))))
  true)

(defn parse-ls-remote-lines
  [text]
  (->> (str/split-lines (or text ""))
       (keep (fn [line]
               (let [[sha ref-name] (str/split line #"\s+" 2)]
                 (when (and sha ref-name (re-matches sha-pattern sha))
                   {:sha sha :ref ref-name}))))
       vec))

(defn github-origin
  "Returns {:owner owner :repo repo} for supported GitHub origin URLs."
  [origin-url]
  (let [[_ ssh-owner ssh-repo] (re-matches #"git@github\.com:([^/]+)/(.+?)(?:\.git)?" origin-url)
        [_ https-owner https-repo] (re-matches #"https://github\.com/([^/]+)/(.+?)(?:\.git)?" origin-url)
        owner (or ssh-owner https-owner)
        repo (or ssh-repo https-repo)]
    (when-not (and owner repo)
      (throw (ex-info "origin must be a GitHub remote URL" {:url origin-url})))
    {:owner owner :repo repo}))

(defn matching-remote-branch
  "Returns the preferred origin branch whose advertised SHA matches head-sha."
  [remote-lines head-sha]
  (let [branches (->> (parse-ls-remote-lines remote-lines)
                      (keep (fn [{:keys [sha ref]}]
                              (when (and (= sha head-sha)
                                         (str/starts-with? ref "refs/heads/"))
                                (subs ref (count "refs/heads/")))))
                      vec)]
    (or (some #(when (= "master" %) %) branches)
        (first branches))))

(defn resolve-dry-run-target
  "Resolves a dry-run target from injected local/remote data.

   The selected local commit must be present at the selected remote ref. Without
   explicit-ref, an origin branch pointing at HEAD is selected, preferring
   master. With explicit-ref, remote-lines must contain refs/heads/ref or ref
   advertised at the same SHA as local-sha."
  [{:keys [head-sha local-sha remote-lines explicit-ref]}]
  (let [checkout-sha (or local-sha head-sha)]
    (when-not (re-matches sha-pattern checkout-sha)
      (throw (ex-info "Dry-run target must resolve to an exact commit SHA"
                      {:sha checkout-sha})))
    (if explicit-ref
      (let [wanted-refs #{explicit-ref (str "refs/heads/" explicit-ref) (str "refs/tags/" explicit-ref)}
            matched-ref (some (fn [{:keys [sha ref]}]
                                (when (and (= sha checkout-sha) (contains? wanted-refs ref))
                                  ref))
                              (parse-ls-remote-lines remote-lines))]
        (when-not matched-ref
          (throw (ex-info "Selected ref is not pushed at the selected local commit"
                          {:ref explicit-ref :sha checkout-sha})))
        {:ref explicit-ref :sha checkout-sha})
      (let [branch (matching-remote-branch remote-lines checkout-sha)]
        (when-not branch
          (throw (ex-info "HEAD is not present on a remote origin branch"
                          {:sha checkout-sha})))
        {:ref branch :sha checkout-sha}))))

(defn existing-tag-push-plan
  "Returns the safe release push action for an existing local tag state."
  [{:keys [tag-at-head? tag-pushed?]}]
  (cond
    (and tag-at-head? tag-pushed?) :nothing-to-do
    tag-at-head? :push-existing-tag
    :else :create-release-tag))

(defn parse-release-args
  [args]
  (let [options (loop [remaining args
                       options {:command :release}]
                  (if-let [arg (first remaining)]
                    (case arg
                      "--dry-run" (recur (rest remaining) (assoc options :command :dry-run))
                      "--ref" (let [ref (second remaining)]
                                (when-not ref
                                  (throw (ex-info "--ref requires a value" {})))
                                (recur (nnext remaining) (assoc options :ref ref)))
                      (throw (ex-info "Unknown release option" {:option arg})))
                    options))]
    (when (and (:ref options) (not= :dry-run (:command options)))
      (throw (ex-info "--ref is only supported with --dry-run" {:ref (:ref options)})))
    options))

(defn workflow-file-present!
  ([]
   (workflow-file-present! #(.isFile (io/file release-workflow))))
  ([present?]
   (let [present? (if (fn? present?) (present?) present?)]
     (when-not present?
       (throw (ex-info "Release workflow file is missing; add .github/workflows/release.yml before dry-run dispatch"
                       {:workflow release-workflow}))))))

(defn assert-gh-ready!
  [command-fn]
  (run-command! command-fn ["sh" "-c" "command -v gh"]
        "GitHub CLI is unavailable; install gh and authenticate before dispatching a release dry run")
  (run-command! command-fn ["gh" "auth" "status"]
        "GitHub CLI is not authenticated; run gh auth login before dispatching a release dry run")
  true)

(defn origin-url
  [command-fn]
  (let [result (command-fn ["git" "remote" "get-url" "origin"])]
    (when-not (zero? (:exit result))
      (throw (ex-info "No GitHub origin remote is configured; add an origin remote pointing at GitHub before release dry-run dispatch"
                      result)))
    (str/trim (:out result))))

(defn github-origin!
  [origin-url]
  (try
    (github-origin origin-url)
    (catch clojure.lang.ExceptionInfo ex
      (throw (ex-info "origin must be a GitHub remote URL; set origin to git@github.com:OWNER/REPO.git or https://github.com/OWNER/REPO.git before release dry-run dispatch"
                      (assoc (ex-data ex) :url origin-url))))))

(defn remote-lines
  [command-fn]
  (git command-fn "ls-remote" "origin"))

(defn resolve-local-sha
  [command-fn ref]
  (if ref
    (git-trim command-fn "rev-parse" "--verify" (str ref "^{commit}"))
    (git-trim command-fn "rev-parse" "HEAD")))

(defn release-dry-run-plan
  "Returns the dry-run workflow dispatch data after validating local/remote state."
  [{:keys [command-fn ref] :as options}]
  (let [command-fn (or command-fn command)
        workflow-file-present? (if (contains? options :workflow-file-present?)
                                 (:workflow-file-present? options)
                                 #(.isFile (io/file release-workflow)))]
    (when-not (clean-working-tree? command-fn)
      (throw (ex-info "Working tree is not clean; commit or stash changes before release dry run" {})))
    (workflow-file-present! workflow-file-present?)
    (assert-gh-ready! command-fn)
    (github-origin! (origin-url command-fn))
    (let [sha (resolve-local-sha command-fn ref)
          target (resolve-dry-run-target {:head-sha sha
                                          :local-sha sha
                                          :explicit-ref ref
                                          :remote-lines (remote-lines command-fn)})
          version (dry-run-version (git-count-at command-fn (:sha target)))]
      (assoc target :expected-version version))))

(defn dispatch-dry-run!
  [{:keys [command-fn ref] :as options}]
  (let [command-fn (or command-fn command)
        plan-options (cond-> {:command-fn command-fn
                              :ref ref}
                       (contains? options :workflow-file-present?)
                       (assoc :workflow-file-present? (:workflow-file-present? options)))
        {:keys [ref sha expected-version] :as plan} (release-dry-run-plan plan-options)]
    (run-command! command-fn ["gh" "workflow" "run" release-workflow
                      "--ref" ref
                      "-f" (str "ref=" ref)
                      "-f" (str "sha=" sha)
                      "-f" (str "expected_version=" expected-version)]
          "Failed to dispatch release workflow dry run")
    plan))

(defn release-tags-at-head
  [command-fn]
  (->> (str/split-lines (git command-fn "tag" "--points-at" "HEAD"))
       (filter valid-release-tag?)
       sort
       vec))

(defn tag-pushed?
  [command-fn tag]
  (->> (parse-ls-remote-lines (git command-fn "ls-remote" "origin" (str "refs/tags/" tag)))
       (some #(= (str "refs/tags/" tag) (:ref %)))
       boolean))

(defn release-tag-at-head!
  [command-fn]
  (let [tags (release-tags-at-head command-fn)]
    (when (< 1 (count tags))
      (throw (ex-info "Multiple release tags point at HEAD; inspect tags before retrying release push"
                      {:tags tags})))
    (first tags)))

(defn current-release-tag-state
  [command-fn]
  (let [tag (release-tag-at-head! command-fn)]
    {:tag tag
     :tag-at-head? (boolean tag)
     :tag-pushed? (boolean (and tag (tag-pushed? command-fn tag)))}))

(defn today
  []
  (str (java.time.LocalDate/now)))

(defn create-release-tag!
  [{:keys [command-fn date changelog-file]}]
  (let [command-fn (or command-fn command)]
    (assert-clean-master! command-fn)
    (when-let [tag (:tag (current-release-tag-state command-fn))]
      (throw (ex-info "Release tag already exists at HEAD; run bb release to push or inspect it"
                      {:tag tag})))
    (let [current-count (git-count-at command-fn "HEAD")
          version (next-release-version current-count)
          tag (str "v" version)
          changelog-file (io/file (or changelog-file "CHANGELOG.md"))
          stamped (stamp-changelog (slurp changelog-file) version (or date (today)))]
      (spit changelog-file stamped)
      (run-command! command-fn ["git" "add" "CHANGELOG.md"] "Failed to stage CHANGELOG.md release stamp")
      (run-command! command-fn ["git" "commit" "-m" (str "Release " tag)] "Failed to commit CHANGELOG.md release stamp")
      (run-command! command-fn ["git" "tag" tag] "Failed to create release tag")
      {:tag tag :version version})))

(defn push-release!
  [{:keys [command-fn]}]
  (let [command-fn (or command-fn command)]
    (assert-clean-master! command-fn)
    (let [{:keys [tag] :as tag-state} (current-release-tag-state command-fn)
          action (existing-tag-push-plan tag-state)
          release (case action
                    :nothing-to-do {:tag tag :action action}
                    :push-existing-tag {:tag tag :action action}
                    :create-release-tag (assoc (create-release-tag! {:command-fn command-fn})
                                               :action action))
          release-tag (:tag release)]
      (when (= :nothing-to-do (:action release))
        (println (str release-tag " is already pushed; no release push needed.")))
      (when-not (= :nothing-to-do (:action release))
        (run-command! command-fn ["git" "push" "origin" "master"] "Failed to push master")
        (run-command! command-fn ["git" "push" "origin" release-tag] "Failed to push release tag"))
      release)))

(defn release-tag-command!
  []
  (let [{:keys [tag version]} (create-release-tag! {})]
    (println (str "Created local release tag " tag " for version " version "."))
    (println "Run `bb release` to push master and the tag.")))

(defn release-command!
  [args]
  (let [{:keys [command ref]} (parse-release-args args)]
    (case command
      :dry-run (let [{:keys [ref sha expected-version]} (dispatch-dry-run! {:ref ref})]
                 (println (str "Dispatched release workflow dry run for " ref " at " sha
                               " expecting " expected-version ".")))
      :release (let [{:keys [tag action]} (push-release! {})]
                 (println (str "Release push complete for " tag " (" (name action) ")."))))))

(defn -main
  [& args]
  (release-command! args))
