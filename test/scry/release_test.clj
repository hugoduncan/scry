(ns scry.release-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.release :as release]))

(def sample-sha "0123456789abcdef0123456789abcdef01234567")
(def other-sha "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

(defn exception-message
  [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo ex
      (ex-message ex))))

(deftest release-version-math-test
  ;; Version helpers make Git-count semantics explicit for dry-runs and the
  ;; off-by-one changelog release commit.
  (testing "version math"
    (is (= "0.1.41" (release/dry-run-version 41)))
    (is (= "0.1.42" (release/next-release-version 41)))
    (is (= "Commit count must be a non-negative integer"
           (exception-message #(release/build-version -1))))))

(deftest release-tag-validation-test
  ;; Publishing tags must be strict v0.1.N tags and must agree with the jar
  ;; version computed at the checked-out tag commit.
  (testing "valid release tags"
    (is (release/valid-release-tag? "v0.1.0"))
    (is (release/valid-release-tag? "v0.1.123"))
    (is (= "0.1.123" (release/release-tag-version "v0.1.123")))
    (is (= "0.1.123" (release/assert-tag-matches-version! "v0.1.123" "0.1.123"))))
  (testing "nonconforming v* tags fail before publishing"
    (doseq [tag ["v1.0.0" "v0.2.1" "v0.1.x" "0.1.1" "v0.1.-1"]]
      (is (= "Release tag must match v0.1.<non-negative-integer>"
             (exception-message #(release/release-tag-version tag)))
          tag)))
  (testing "tag version agreement"
    (is (= "Release tag version does not match built artifact version"
           (exception-message #(release/assert-tag-matches-version! "v0.1.123" "0.1.124"))))))

(deftest changelog-stamping-test
  ;; Changelog stamping preserves a fresh bare Unreleased section and moves the
  ;; previous non-empty body under a bracketed version/date heading.
  (testing "stamp changelog"
    (let [changelog (str "# Changelog\n\n"
                         "## Unreleased\n\n"
                         "- Added release automation.\n"
                         "- Added deploy support.\n\n"
                         "## [0.1.100] - 2026-06-01\n\n"
                         "- Previous release.\n")
          stamped (release/stamp-changelog changelog "0.1.101" "2026-06-03")]
      (is (str/includes? stamped "## Unreleased\n\n## [0.1.101] - 2026-06-03"))
      (is (= "- Added release automation.\n- Added deploy support."
             (release/release-section stamped "0.1.101")))
      (is (= "- Previous release."
             (release/release-section stamped "0.1.100")))))
  (testing "missing or empty Unreleased fails"
    (is (= "CHANGELOG.md is missing a bare ## Unreleased section"
           (exception-message #(release/stamp-changelog "# Changelog\n" "0.1.1" "2026-06-03"))))
    (is (= "CHANGELOG.md ## Unreleased section is empty"
           (exception-message #(release/stamp-changelog "# Changelog\n\n## Unreleased\n\n" "0.1.1" "2026-06-03"))))))

(deftest release-section-extraction-test
  ;; Release body extraction uses only bracketed ## [VERSION] - YYYY-MM-DD
  ;; headings and fails when the requested section is missing or empty.
  (testing "extracts bracketed release section"
    (let [changelog (str "# Changelog\n\n"
                         "## Unreleased\n\n"
                         "## [0.1.101] - 2026-06-03\n\n"
                         "- Release body.\n\n"
                         "## [0.1.100] - 2026-06-01\n\n"
                         "- Previous.\n")]
      (is (= "- Release body."
             (release/release-section changelog "0.1.101")))))
  (testing "missing bare heading and empty release body fail"
    (is (= "CHANGELOG.md is missing the requested bracketed release section"
           (exception-message #(release/release-section "## 0.1.101 - 2026-06-03\n\n- bare\n" "0.1.101"))))
    (is (= "CHANGELOG.md release section is empty"
           (exception-message #(release/release-section "## [0.1.101] - 2026-06-03\n\n" "0.1.101"))))))

(deftest dry-run-changelog-section-test
  ;; Workflow dry-run changelog shape is kept testable outside YAML: stamped
  ;; refs/tags require the matching bracketed release section, while ordinary
  ;; branch/SHA dry runs may fall back to non-empty Unreleased content.
  (let [branch-changelog (str "# Changelog\n\n"
                              "## Unreleased\n\n"
                              "- Pending release notes.\n")
        stamped-changelog (str "# Changelog\n\n"
                               "## Unreleased\n\n"
                               "## [0.1.101] - 2026-06-03\n\n"
                               "- Release body.\n")]
    (testing "stamped ref requires matching bracketed section"
      (is (= "- Release body."
             (release/dry-run-changelog-section stamped-changelog
                                                {:version "0.1.101"
                                                 :input-ref "v0.1.101"})))
      (is (= "CHANGELOG.md is missing the requested bracketed release section"
             (exception-message #(release/dry-run-changelog-section branch-changelog
                                                                    {:version "0.1.101"
                                                                     :input-ref "refs/tags/v0.1.101"})))))
    (testing "tagged HEAD requires matching bracketed section even for branch input"
      (is (= "CHANGELOG.md is missing the requested bracketed release section"
             (exception-message #(release/dry-run-changelog-section branch-changelog
                                                                    {:version "0.1.101"
                                                                     :input-ref "master"
                                                                     :head-sha sample-sha
                                                                     :tag-sha sample-sha})))))
    (testing "ordinary branch dry-run falls back to Unreleased"
      (is (= "- Pending release notes."
             (release/dry-run-changelog-section branch-changelog
                                                {:version "0.1.101"
                                                 :input-ref "master"
                                                 :head-sha sample-sha
                                                 :tag-sha other-sha}))))
    (testing "ordinary branch dry-run uses release section when present"
      (is (= "- Release body."
             (release/dry-run-changelog-section stamped-changelog
                                                {:version "0.1.101"
                                                 :input-ref "master"}))))))

(deftest release-argument-parsing-test
  ;; Maintainer CLI arguments should fail clearly when an option would otherwise
  ;; be accepted but ignored by the selected release path.
  (testing "--ref is accepted only for dry-run dispatch"
    (is (= {:command :dry-run :ref "master"}
           (release/parse-release-args ["--dry-run" "--ref" "master"])))
    (is (= "--ref is only supported with --dry-run"
           (exception-message #(release/parse-release-args ["--ref" "master"]))))
    (is (= "--ref requires a value"
           (exception-message #(release/parse-release-args ["--dry-run" "--ref"]))))))

(deftest dry-run-ref-agreement-test
  ;; Dry-run ref resolution is state-based: the selected local commit must be
  ;; advertised by origin at the selected ref before workflow dispatch.
  (testing "defaults to a remote branch pointing at HEAD and prefers master"
    (let [remote-lines (str other-sha "\trefs/heads/topic\n"
                            sample-sha "\trefs/heads/feature\n"
                            sample-sha "\trefs/heads/master\n")]
      (is (= {:ref "master" :sha sample-sha}
             (release/resolve-dry-run-target {:head-sha sample-sha
                                              :remote-lines remote-lines})))))
  (testing "explicit refs must resolve remotely to the same SHA"
    (let [remote-lines (str sample-sha "\trefs/heads/release-check\n")]
      (is (= {:ref "release-check" :sha sample-sha}
             (release/resolve-dry-run-target {:local-sha sample-sha
                                              :explicit-ref "release-check"
                                              :remote-lines remote-lines})))
      (is (= "Selected ref is not pushed at the selected local commit"
             (exception-message #(release/resolve-dry-run-target {:local-sha sample-sha
                                                                  :explicit-ref "missing"
                                                                  :remote-lines remote-lines}))))))
  (testing "unpushed HEAD fails before dispatch"
    (is (= "HEAD is not present on a remote origin branch"
           (exception-message #(release/resolve-dry-run-target {:head-sha sample-sha
                                                                :remote-lines (str other-sha "\trefs/heads/master\n")}))))))

(deftest command-boundary-state-test
  ;; Git state checks use an injectable process boundary with visible output
  ;; rather than mocks or call verification. State checks fail closed by
  ;; validating Git command exits before trusting stdout.
  (testing "clean master validation"
    (let [command-fn (fn [args]
                       (case (str/join " " args)
                         "git status --porcelain" {:exit 0 :out "" :err ""}
                         "git rev-parse --abbrev-ref HEAD" {:exit 0 :out "master\n" :err ""}))]
      (is (true? (release/assert-clean-master! command-fn)))))
  (testing "dirty tree and branch failures"
    (is (= "Working tree is not clean; commit or stash changes first"
           (exception-message #(release/assert-clean-master!
                                (fn [_] {:exit 0 :out " M CHANGELOG.md\n" :err ""})))))
    (is (= "Release must be cut from master"
           (exception-message #(release/assert-clean-master!
                                (fn [args]
                                  (case (str/join " " args)
                                    "git status --porcelain" {:exit 0 :out "" :err ""}
                                    "git rev-parse --abbrev-ref HEAD" {:exit 0 :out "topic\n" :err ""})))))))
  (testing "failed Git state commands fail before trusting stdout"
    (is (= "Failed to inspect working tree status"
           (exception-message #(release/assert-clean-master!
                                (fn [args]
                                  (case (str/join " " args)
                                    "git status --porcelain" {:exit 128 :out "" :err "fatal: not a git repository"})))))))
  (is (= "Failed to resolve current branch"
         (exception-message #(release/assert-clean-master!
                              (fn [args]
                                (case (str/join " " args)
                                  "git status --porcelain" {:exit 0 :out "" :err ""}
                                  "git rev-parse --abbrev-ref HEAD" {:exit 128 :out "master\n" :err "fatal: ambiguous HEAD"})))))))

(deftest dry-run-missing-workflow-guard-test
  ;; The workflow file presence guard is injectable so focused checks can prove
  ;; dry-run planning fails clearly before dispatch without mutating this repo.
  (let [calls (atom [])
        command-fn (fn [args]
                     (swap! calls conj args)
                     (case (str/join " " args)
                       "git status --porcelain" {:exit 0 :out "" :err ""}))]
    (is (= "Release workflow file is missing; add .github/workflows/release.yml before dry-run dispatch"
           (exception-message #(release/release-dry-run-plan {:command-fn command-fn
                                                              :workflow-file-present? false}))))
    (is (= [["git" "status" "--porcelain"]] @calls))))

(deftest dry-run-dispatch-contract-test
  ;; A successful dry-run dispatch is non-mutating and sends the exact ref, SHA,
  ;; and expected version contract to the GitHub release workflow through the
  ;; injectable command boundary.
  (let [calls (atom [])
        command-fn (fn [args]
                     (swap! calls conj args)
                     (condp = (str/join " " args)
                       "git status --porcelain" {:exit 0 :out "" :err ""}
                       "sh -c command -v gh" {:exit 0 :out "/usr/bin/gh\n" :err ""}
                       "gh auth status --hostname github.com" {:exit 0 :out "Logged in\n" :err ""}
                       "git remote get-url origin" {:exit 0 :out "git@github.com:hugoduncan/scry.git\n" :err ""}
                       "git rev-parse HEAD" {:exit 0 :out (str sample-sha "\n") :err ""}
                       "git ls-remote origin" {:exit 0 :out (str sample-sha "\trefs/heads/master\n") :err ""}
                       (str "git rev-list --count " sample-sha) {:exit 0 :out "41\n" :err ""}
                       "gh workflow run .github/workflows/release.yml --ref master -f ref=master -f sha=0123456789abcdef0123456789abcdef01234567 -f expected_version=0.1.41"
                       {:exit 0 :out "" :err ""}))]
    (is (= {:ref "master" :sha sample-sha :expected-version "0.1.41"}
           (release/dispatch-dry-run! {:command-fn command-fn
                                       :workflow-file-present? true})))
    (is (= ["gh" "workflow" "run" ".github/workflows/release.yml"
            "--ref" "master"
            "-f" "ref=master"
            "-f" (str "sha=" sample-sha)
            "-f" "expected_version=0.1.41"]
           (last @calls)))
    (is (not-any? #(contains? #{["git" "add" "CHANGELOG.md"]
                                ["git" "commit" "-m" "Release v0.1.41"]
                                ["git" "tag" "v0.1.41"]
                                ["git" "push" "origin" "master"]}
                              %)
                  @calls))))

(deftest dry-run-dispatch-missing-workflow-guard-test
  ;; The maintainer-facing dry-run dispatch entry point passes the injectable
  ;; workflow presence guard through to planning, so missing-workflow behavior is
  ;; covered without depending on this repository's real workflow file.
  (let [calls (atom [])
        command-fn (fn [args]
                     (swap! calls conj args)
                     (case (str/join " " args)
                       "git status --porcelain" {:exit 0 :out "" :err ""}))]
    (is (= "Release workflow file is missing; add .github/workflows/release.yml before dry-run dispatch"
           (exception-message #(release/dispatch-dry-run! {:command-fn command-fn
                                                           :workflow-file-present? false}))))
    (is (= [["git" "status" "--porcelain"]] @calls))))

(deftest github-origin-guidance-test
  ;; Dry-run origin validation should give maintainer guidance instead of
  ;; surfacing the generic git failure for a missing or non-GitHub origin.
  (testing "missing origin fails clearly"
    (is (= "No GitHub origin remote is configured; add an origin remote pointing at GitHub before release dry-run dispatch"
           (exception-message #(release/origin-url
                                (fn [_]
                                  {:exit 2
                                   :out ""
                                   :err "fatal: No such remote 'origin'\n"}))))))
  (testing "non-GitHub origin fails clearly"
    (is (= "origin must be a GitHub remote URL; set origin to git@github.com:OWNER/REPO.git or https://github.com/OWNER/REPO.git before release dry-run dispatch"
           (exception-message #(release/github-origin! "https://example.com/org/repo.git")))))
  (testing "GitHub origin parses"
    (is (= {:owner "hugoduncan" :repo "scry"}
           (release/github-origin! "git@github.com:hugoduncan/scry.git")))))

(deftest create-release-tag-orchestration-test
  ;; The high-level release-tag path is exercised in an isolated temp workdir
  ;; with an injectable command boundary. The test proves off-by-one versioning,
  ;; changelog stamping, and the git command sequence without touching this repo.
  (let [workdir (.toFile (java.nio.file.Files/createTempDirectory "scry-release-tag-test" (make-array java.nio.file.attribute.FileAttribute 0)))
        changelog-file (io/file workdir "CHANGELOG.md")
        calls (atom [])
        command-fn (fn [args]
                     (swap! calls conj args)
                     (case (str/join " " args)
                       "git status --porcelain" {:exit 0 :out "" :err ""}
                       "git rev-parse --abbrev-ref HEAD" {:exit 0 :out "master\n" :err ""}
                       "git tag --points-at HEAD" {:exit 0 :out "" :err ""}
                       "git rev-list --count HEAD" {:exit 0 :out "41\n" :err ""}
                       "git add CHANGELOG.md" {:exit 0 :out "" :err ""}
                       "git commit -m Release v0.1.42" {:exit 0 :out "[master release] Release v0.1.42\n" :err ""}
                       "git tag v0.1.42" {:exit 0 :out "" :err ""}))]
    (spit changelog-file (str "# Changelog\n\n"
                              "## Unreleased\n\n"
                              "- Added release workflow.\n\n"
                              "## [0.1.41] - 2026-06-02\n\n"
                              "- Previous release.\n"))
    (is (= {:tag "v0.1.42" :version "0.1.42"}
           (release/create-release-tag! {:command-fn command-fn
                                         :date "2026-06-03"
                                         :changelog-file changelog-file})))
    (is (= ["git" "status" "--porcelain"] (nth @calls 0)))
    (is (= ["git" "rev-parse" "--abbrev-ref" "HEAD"] (nth @calls 1)))
    (is (= ["git" "tag" "--points-at" "HEAD"] (nth @calls 2)))
    (is (= ["git" "rev-list" "--count" "HEAD"] (nth @calls 3)))
    (is (= [["git" "add" "CHANGELOG.md"]
            ["git" "commit" "-m" "Release v0.1.42"]
            ["git" "tag" "v0.1.42"]]
           (subvec (vec @calls) 4)))
    (let [stamped (slurp changelog-file)]
      (is (str/includes? stamped "## Unreleased\n\n## [0.1.42] - 2026-06-03"))
      (is (= "- Added release workflow."
             (release/release-section stamped "0.1.42")))
      (is (= "- Previous release."
             (release/release-section stamped "0.1.41"))))))

(deftest push-release-existing-tag-orchestration-test
  ;; A retry after a partial release failure should push the existing local tag
  ;; at HEAD and master without creating a second release tag.
  (let [calls (atom [])
        command-fn (fn [args]
                     (swap! calls conj args)
                     (case (str/join " " args)
                       "git status --porcelain" {:exit 0 :out "" :err ""}
                       "git rev-parse --abbrev-ref HEAD" {:exit 0 :out "master\n" :err ""}
                       "git tag --points-at HEAD" {:exit 0 :out "v0.1.42\n" :err ""}
                       "git ls-remote origin refs/tags/v0.1.42" {:exit 0 :out "" :err ""}
                       "git push origin master" {:exit 0 :out "" :err ""}
                       "git push origin v0.1.42" {:exit 0 :out "" :err ""}))]
    (is (= {:tag "v0.1.42" :action :push-existing-tag}
           (release/push-release! {:command-fn command-fn})))
    (is (= [["git" "status" "--porcelain"]
            ["git" "rev-parse" "--abbrev-ref" "HEAD"]
            ["git" "tag" "--points-at" "HEAD"]
            ["git" "ls-remote" "origin" "refs/tags/v0.1.42"]
            ["git" "push" "origin" "master"]
            ["git" "push" "origin" "v0.1.42"]]
           @calls))
    (is (not-any? #(contains? #{["git" "add" "CHANGELOG.md"]
                                ["git" "commit" "-m" "Release v0.1.42"]
                                ["git" "tag" "v0.1.42"]}
                              %)
                  @calls))))

(deftest multiple-release-tags-at-head-test
  ;; Ambiguous retry states fail clearly instead of silently choosing one of
  ;; several valid release tags that point at HEAD.
  (let [calls (atom [])
        command-fn (fn [args]
                     (swap! calls conj args)
                     (case (str/join " " args)
                       "git status --porcelain" {:exit 0 :out "" :err ""}
                       "git rev-parse --abbrev-ref HEAD" {:exit 0 :out "master\n" :err ""}
                       "git tag --points-at HEAD" {:exit 0 :out "v0.1.42\nv0.1.43\nother-tag\n" :err ""}))]
    (is (= "Multiple release tags point at HEAD; inspect tags before retrying release push"
           (exception-message #(release/push-release! {:command-fn command-fn}))))
    (is (= [["git" "status" "--porcelain"]
            ["git" "rev-parse" "--abbrev-ref" "HEAD"]
            ["git" "tag" "--points-at" "HEAD"]]
           @calls))))

(defn workflow-step
  [workflow step-name]
  (let [marker (str "      - name: " step-name)
        start (.indexOf workflow marker)]
    (when (neg? start)
      (throw (ex-info "Workflow step not found" {:step-name step-name})))
    (let [next-start (.indexOf workflow "\n      - name: " (inc start))]
      (subs workflow start (if (neg? next-start) (count workflow) next-start)))))

(def publishing-condition
  "if: ${{ github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v') }}")

(deftest release-workflow-publishing-gates-test
  ;; The safety-critical publishing steps must be unreachable from manual
  ;; workflow_dispatch dry runs. Static coverage keeps the YAML conditions and
  ;; two-artifact release surface under test rather than relying only on
  ;; actionlint/manual inspection.
  (let [workflow (slurp ".github/workflows/release.yml")
        validate-step (workflow-step workflow "Validate publishing tag and version")
        publishing-steps ["Extract release notes"
                          "Deploy to Clojars"
                          "Create GitHub Release"]]
    (is (str/includes? validate-step publishing-condition))
    (doseq [step-name publishing-steps]
      (let [step (workflow-step workflow step-name)]
        (is (str/includes? step publishing-condition) step-name)
        (is (not (str/includes? step "workflow_dispatch")) step-name)
        (is (not (str/includes? step "||")) step-name)
        (is (< (.indexOf workflow "      - name: Validate publishing tag and version")
               (.indexOf workflow (str "      - name: " step-name)))
            step-name)))
    (is (str/includes? workflow "clojure -T:build jars"))
    (is (str/includes? workflow "clojure -T:build:deploy deploy-all"))
    (is (str/includes? workflow "scry-[0-9]*.[0-9]*.[0-9]*.jar' ! -name 'scry-kaocha-*.jar"))
    (is (str/includes? workflow "scry-kaocha-[0-9]*.[0-9]*.[0-9]*.jar"))
    (let [release-step (workflow-step workflow "Create GitHub Release")]
      (is (str/includes? release-step "gh release create"))
      (is (str/includes? release-step "${{ steps.artifact.outputs.core_jar_file }}"))
      (is (str/includes? release-step "${{ steps.artifact.outputs.kaocha_jar_file }}")))))

(deftest partial-failure-existing-tag-plan-test
  ;; Existing local tags are handled deliberately so a retry pushes the same tag
  ;; instead of silently creating a second release.
  (testing "existing local tag recovery plan"
    (is (= :push-existing-tag
           (release/existing-tag-push-plan {:tag-at-head? true :tag-pushed? false})))
    (is (= :nothing-to-do
           (release/existing-tag-push-plan {:tag-at-head? true :tag-pushed? true})))
    (is (= :create-release-tag
           (release/existing-tag-push-plan {:tag-at-head? false :tag-pushed? false})))))
