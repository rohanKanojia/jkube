/*
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.kit.common.util;

import java.io.File;
import java.io.IOException;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * @author roland
 * @since 28/07/16
 */
public class GitUtil {

    private GitUtil() { }

    public static Repository getGitRepository(File currentDir) throws IOException {

        if (currentDir == null) {
            // TODO: Why is this check needed ?
            currentDir = new File(System.getProperty("basedir", "."));
        }
        File gitFolder = findGitFolder(currentDir);
        if (gitFolder == null) {
            // No git repository found
            return null;
        }
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        return builder
                .readEnvironment()
                .setGitDir(gitFolder)
                .build();
    }

    public static File findGitFolder(File basedir) {
        File gitDir = new File(basedir, ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            return gitDir;
        }
        File parent = basedir.getParentFile();
        if (parent != null) {
            return findGitFolder(parent);
        }
        return null;
    }

    public static String getGitCommitId(Repository repository) throws GitAPIException {
        if (repository != null) {
            return StreamSupport.stream(new Git(repository).log().call().spliterator(), false)
                .map(RevCommit::getName)
                .findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Sanitize Git Repository's remote URL, trims username and access token from URL.
     * This is taken from <a href="https://github.com/dekorateio/dekorate/blob/5f4acbd5b28251d88209388b6f2e826f5a546102/core/src/main/java/io/dekorate/utils/Git.java#L134">Dekorate's Git utility class</a>
     *
     * @param remoteUrlStr URL string of a particular git remote
     * @return sanitized URL
     */
    public static String sanitizeRemoteUrl(String remoteUrlStr) {
        if (StringUtils.isNotBlank(remoteUrlStr)) {
            if (remoteUrlStr.contains("@")) {
                String repoSegmentWithoutAuth = remoteUrlStr.substring(remoteUrlStr.indexOf('@') + 1);
                String[] repoSegmentWithoutAuthParts = repoSegmentWithoutAuth.split(":");
                if (repoSegmentWithoutAuthParts.length > 1) {
                    String[] repoSegmentAfterColonParts = repoSegmentWithoutAuthParts[1].split("/");
                    String portOrRepoPath = repoSegmentAfterColonParts[0];
                    if (!StringUtils.isNumeric(portOrRepoPath)) {
                        repoSegmentWithoutAuth = repoSegmentWithoutAuth.replaceFirst(":", "/");
                    }
                }
                remoteUrlStr = String.format("https://%s", repoSegmentWithoutAuth);
            }
            if (!remoteUrlStr.endsWith(".git")) {
                remoteUrlStr += ".git";
            }
        }
        return remoteUrlStr;
    }
}
