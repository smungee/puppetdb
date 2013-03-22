(ns com.puppetlabs.puppetdb.test.command.dlo
  (:require [cheshire.core :as json]
            [fs.core :as fs]
            [com.puppetlabs.utils :as pl-utils])
  (:use [clojure.test]
        [clj-time.core :only [years days ago now]]
        [clj-time.coerce :only [to-timestamp]]
        [com.puppetlabs.time :only [to-millis]]
        [com.puppetlabs.puppetdb.command.dlo]))

(deftest dlo-compression-introspection
  (testing "an empty directory"
    (let [dir (fs/temp-dir)
          threshold (years 20)]
      (testing "should have no archives"
        (is (empty? (archives dir))))

      (testing "should have no messages"
        (is (empty? (messages dir))))

      (testing "should have no compressible files"
        (is (empty? (compressible-files dir threshold))))

      (testing "should not have a last-archived time"
        (is (nil? (last-archived dir))))

      (testing "should not already be archived"
        (is (not (already-archived? dir threshold))))))

  (testing "a directory with a few new messages"
    (let [dir (fs/temp-dir)
          threshold (years 20)]
      (fs/touch (fs/file dir "foo"))
      (fs/touch (fs/file dir "bar"))
      (fs/touch (fs/file dir "baz"))

      (testing "should have no archives"
        (is (empty? (archives dir))))

      (testing "should have three messages"
        (is (= 3 (count (messages dir)))))

      (testing "should have no compressible files"
        (is (empty? (compressible-files dir threshold))))))

  (testing "a directory with some old and new messages"
    (let [dir (fs/temp-dir)
          threshold (days 7)
          stale-timestamp (.getMillis (ago (days 8)))]
      (fs/touch (fs/file dir "foo") stale-timestamp)
      (fs/touch (fs/file dir "bar") stale-timestamp)
      (fs/touch (fs/file dir "baz"))

      (testing "should have no archives"
        (is (empty? (archives dir))))

      (testing "should have three messages"
        (is (= 3 (count (messages dir)))))

      (testing "should have two compressible files"
        (is (= 2 (count (compressible-files dir threshold)))))))

  (testing "a directory with an old archive"
    (let [dir (fs/temp-dir)
          threshold (days 7)
          more-than-threshold (days 8)
          archive-time (ago more-than-threshold)]
      (fs/touch (fs/file dir (str (pl-utils/timestamp archive-time) ".tgz")))

      (testing "should have no messages"
        (is (empty? (messages dir))))

      (testing "should have an archive"
        (is (= 1 (count (archives dir)))))

      (testing "should have the right last-archived time"
        (is (= archive-time (last-archived dir))))

      (testing "should not already be archived"
        (is (not (already-archived? dir threshold))))

      (testing "and a new archive"
        (let [new-archive-time (now)]
          (fs/touch (fs/file dir (str (pl-utils/timestamp new-archive-time) ".tgz")))

          (testing "should have no messages"
            (is (empty? (messages dir))))

          (testing "should have two archives"
            (is (= 2 (count (archives dir)))))

          (testing "should have the right last-archived time"
            (is (= new-archive-time (last-archived dir))))

          (testing "should already be archived"
            (is (already-archived? dir threshold))))))))

(deftest dlo-compression
  (let [dlo (fs/temp-dir)
        threshold (days 7)
        stale-timestamp (.getMillis (ago (days 8)))]
    (testing "should work with no subdirectories"
      (compress! "non-existent-dir" (days 7))
      (is (empty? (fs/list-dir dlo))))

    (testing "with subdirectories"
      (let [subdir (fs/temp-dir dlo)
            other-subdir (fs/temp-dir dlo)]
        (testing "should not archive empty subdirectories"
          (compress! dlo threshold)
          (is (empty? (archives subdir)))
          (is (empty? (archives other-subdir))))

        (testing "should not archive new messages"
          (fs/touch (fs/file subdir "foo"))
          (fs/touch (fs/file other-subdir "bar"))
          (compress! dlo threshold)
          (is (= 1 (count (messages subdir))))
          (is (= 1 (count (messages other-subdir))))
          (is (empty? (archives subdir)))
          (is (empty? (archives other-subdir))))

        (testing "should archive old messages in subdirectories which haven't been archived"
          (fs/touch (fs/file subdir "foo") stale-timestamp)
          (fs/touch (fs/file other-subdir "bar") stale-timestamp)
          (compress! dlo threshold)
          (is (empty? (messages subdir)))
          (is (empty? (messages other-subdir)))
          (is (= 1 (count (archives subdir))))
          (is (= 1 (count (archives other-subdir)))))

        (testing "should not archive subdirectories which have already been, even if there are old messages"
          (fs/touch (fs/file subdir "foo2") stale-timestamp)
          (fs/touch (fs/file other-subdir "bar2") stale-timestamp)
          (compress! dlo threshold)
          (is (= 1 (count (messages subdir))))
          (is (= 1 (count (messages other-subdir))))
          (is (= 1 (count (archives subdir))))
          (is (= 1 (count (archives other-subdir)))))))))
